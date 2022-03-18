package wifidirect.helper.sdk.services

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaScannerConnection
import android.media.RingtoneManager
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.*
import android.provider.ContactsContract.Directory.PACKAGE_NAME
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import android.os.Environment
import android.os.PowerManager.ON_AFTER_RELEASE
import android.provider.ContactsContract
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.content.ContentProviderOperation
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.RawContacts
import wifidirect.helper.sdk.R
import wifidirect.helper.sdk.helpers.WifiDirectUtil
import wifidirect.helper.sdk.helpers.enums.FileMimes
import wifidirect.helper.sdk.helpers.enums.SendingStatus
import wifidirect.helper.sdk.helpers.NotificationHelper
import wifidirect.helper.sdk.models.FileModel
import wifidirect.helper.sdk.models.SharingFileModel

class WifiDirectSenderService : Service() {

    val WIFIDIRECT_SENDDATANOTIFICAION_CHANNELID = "190"
    val RECEIVEDFILES_DIRECTORYNAME = "smartswitch"
    val COMPLETED = "completed"
    val ISSERVER_AlIVED = "isServerAlived"
    val ALLFILESIZE = "totalFileSize"

    private val PACKAGE_NAME =
        "com.google.android.gms.location.sample.locationupdatesforegroundservice"

    /**
     * The name of the channel for notifications.
     */

    private val EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME +
            ".started_from_notification"

    private val mBinder: IBinder = LocalBinder()

    /**
     * The identifier for the notification displayed for the foreground service.
     */

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    private var mNotificationManager: NotificationManager? = null

    /**
     * Callback for changes in location.
     */

    var wifiP2pManager: WifiP2pManager? = null
    var channel: WifiP2pManager.Channel? = null


    var dataTransferringStateForSender: MutableStateFlow<DataTransferringModel> =
        MutableStateFlow(
            DataTransferringModel(SendingStatus.InitialState, 0, 0, 0)
        )

    var dataTransferringStateForReceiver: MutableStateFlow<DataTransferringModel> =
        MutableStateFlow(
            DataTransferringModel(SendingStatus.InitialState, 0, 0, 0)
        )

    var isConnectedWithEachOther: MutableStateFlow<Boolean> = MutableStateFlow(true)

    @SuppressLint("MissingPermission")
    override fun onCreate() {

        Log.d("service", "onCreate")
    }

    var currentGroup: WifiP2pGroup? = null
    var wakeLock: PowerManager.WakeLock? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("service", "onStartCommand")
        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager?.initialize(this, this.mainLooper, null)

        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.makeNotificationChannel(
                context = this,
                notificationManager = mNotificationManager!!
            )
        }

        wifiP2pManager?.requestGroupInfo(channel) {
            if (it != null) {
                startForeground(
                    WIFIDIRECT_SENDDATANOTIFICAION_CHANNELID.toInt(),
                    getNotification(it)
                )
                setWifiDirectBroadCastReceiver()


                currentGroup = it


            } else {
                Toast.makeText(this, "Null Info", Toast.LENGTH_SHORT).show()
            }
        }

        // managing screen wake up
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.FULL_WAKE_LOCK, "MyApp::MyWakelockTag")
        }

        return START_STICKY
    }

    /**
     * Returns the [NotificationCompat] used as part of the foreground service.
     */
    private fun getNotification(wifiP2pGroup: WifiP2pGroup): Notification {
        // The PendingIntent to launch activity.
        val path = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val patternVibrate = longArrayOf(1000, 1000)

        var clientList: ArrayList<WifiP2pDevice>? = null
        if (wifiP2pGroup.isGroupOwner) {

            clientList = ArrayList(wifiP2pGroup.clientList)
        }
        val connectDeviceName =
            if (!wifiP2pGroup.isGroupOwner) wifiP2pGroup.owner.deviceName else clientList?.get(0)?.deviceName
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this)
//            .setContentIntent(activityPendingIntent)
            .setContentText(getString(R.string.device_connected, connectDeviceName))
            .setContentTitle("Device Connected")
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher)
            .setVibrate(patternVibrate)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTicker("text")
            .setSound(path)
            .setWhen(System.currentTimeMillis())

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(WIFIDIRECT_SENDDATANOTIFICAION_CHANNELID) // Channel ID
        }
        return builder.build()
    }


    fun stopService() {
        stopForeground(true)
        stopSelf()
    }

    var serverSocket: ServerSocket? = null
    var client: Socket? = null
    fun startSocketForTransferringDataForServer(closeConnection: () -> Unit) {

        CoroutineScope(Dispatchers.Default).launch {

            if (serverSocket?.isClosed == false) {
                serverSocket?.close()
            }

            val buf = ByteArray(1024)
            var len = 0
            var isServerTesting = false
            var stopIt = true
            try {
                dataTransferringStateForReceiver.value =
                    DataTransferringModel(SendingStatus.InitialState, 0, 0, 0)
                serverSocket = ServerSocket(5000)

                client = serverSocket?.accept()
                dataTransferringStateForReceiver.value =
                    DataTransferringModel(SendingStatus.Transferring, 0, 0, 0)

                val dirPath =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + "/$RECEIVEDFILES_DIRECTORYNAME";
                if (!File(dirPath).exists()) {

                    File(dirPath).mkdirs()
                }
                var allFileSize = 0L
                var wakeLockEnabled = false
                val connectedOutputStream: InputStream? = client?.getInputStream()

                val objectInputStream = ObjectInputStream(connectedOutputStream)
                while (stopIt) {
                    val fileModel: FileModel = objectInputStream.readObject() as FileModel


                    when (fileModel.fileName) {
                        COMPLETED -> {
                            withContext(Dispatchers.Main) {

                                dataTransferringStateForReceiver.value =
                                    DataTransferringModel(
                                        SendingStatus.TransferCompleted,
                                        0,
                                        0,
                                        100
                                    )
                            }
                            stopIt = false
                            objectInputStream.close()
                        }
                        ALLFILESIZE -> {
                            allFileSize = fileModel.allFileSize ?: 0L
                            dataTransferringStateForReceiver.value.totalProgress = allFileSize
                        }
                        ISSERVER_AlIVED -> {
                            isServerTesting = true
                            objectInputStream.close()
                            connectedOutputStream?.close()
                            stopIt = false
                            break
                        }
                        else -> {

                            if (!wakeLockEnabled) {
                                wakeLockEnabled = !wakeLockEnabled
                            }

                            if (fileModel.mimeType == FileMimes.FILE_CONTACTS) {
                                val op_list = ArrayList<ContentProviderOperation>()
                                op_list.add(
                                    ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                                        .withValue(RawContacts.ACCOUNT_TYPE, null)
                                        .withValue(
                                            RawContacts.ACCOUNT_NAME,
                                            null
                                        ) //.withValue(RawContacts.AGGREGATION_MODE, RawContacts.AGGREGATION_MODE_DEFAULT)
                                        .build()
                                )

                                // first and last names

                                // first and last names
                                op_list.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(
                                            ContactsContract.Data.RAW_CONTACT_ID,
                                            0
                                        )
                                        .withValue(
                                            ContactsContract.Data.MIMETYPE,
                                            StructuredName.CONTENT_ITEM_TYPE
                                        )
                                        .withValue(
                                            StructuredName.GIVEN_NAME,
                                            fileModel.ContactData?.name
                                        )
                                        .withValue(StructuredName.FAMILY_NAME, "")
                                        .build()
                                )

                                op_list.add(
                                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                        .withValueBackReference(
                                            ContactsContract.Data.RAW_CONTACT_ID,
                                            0
                                        )
                                        .withValue(
                                            ContactsContract.Data.MIMETYPE,
                                            Phone.CONTENT_ITEM_TYPE
                                        )
                                        .withValue(
                                            Phone.NUMBER,
                                            fileModel.ContactData?.mobileNumber
                                        )
                                        .withValue(Phone.TYPE, Phone.TYPE_HOME)
                                        .build()
                                )

                                try {
                                    val results =
                                        contentResolver.applyBatch(
                                            ContactsContract.AUTHORITY,
                                            op_list
                                        )
                                } catch (e: java.lang.Exception) {
                                    e.printStackTrace()
                                }
                            } else {


                                val currentFilDir = File("$dirPath/${fileModel.mimeType?.type}")
                                if (!currentFilDir.exists()) {
                                    currentFilDir.mkdir()
                                }
                                val outPutFile =
                                    File("$currentFilDir/${System.currentTimeMillis()}_${fileModel.fileName}")

                                val outputStream: OutputStream = FileOutputStream(outPutFile.path)

                                outputStream.use { output ->
                                    while (objectInputStream.read(buf).also { len = it } != -1) {
                                        if (client?.isConnected == true) {

                                            output.write(buf, 0, len)


                                            val currentPro =
                                                dataTransferringStateForReceiver.value.currentProgress + len
                                            val percentage =
                                                calculateCurrentProgressForMe(
                                                    allFileSize,
                                                    currentPro
                                                )
                                            dataTransferringStateForReceiver.value =
                                                DataTransferringModel(
                                                    SendingStatus.Transferring,
                                                    allFileSize,
                                                    currentPro,
                                                    percentage
                                                )


                                        } else {
                                            throw IOException()
                                        }

                                    }
                                    MediaScannerConnection.scanFile(
                                        applicationContext, arrayOf(outPutFile.path),
                                        null, null
                                    )
                                }
                            }

                        }
                    }

                }
            } catch (e: IOException) {
                stopIt = false
                Log.d("namesget", "io exception ${e.message}")


                if (dataTransferringStateForReceiver.value.status == SendingStatus.Transferring) {
                    withContext(Dispatchers.IO) {

                        dataTransferringStateForReceiver.value =
                            DataTransferringModel(SendingStatus.TransferFailed, 0, 0, 0)
                    }
                }

            } catch (ex: Exception) {
                stopIt = false
                Log.d("namesget", "Exception ${ex.message}")
                if (dataTransferringStateForReceiver.value.status == SendingStatus.Transferring) {
                    withContext(Dispatchers.IO) {

                        dataTransferringStateForReceiver.value =
                            DataTransferringModel(SendingStatus.TransferFailed, 0, 0, 0)
                    }
                }
            } finally {

                stopIt = false
                serverSocket.takeIf { it?.isClosed == false }?.apply {
                    close()
                }

                if (!isServerTesting) {
                    closeConnection()
                    if (wakeLock?.isHeld == true)
                        wakeLock?.release(ON_AFTER_RELEASE)
                } else {
                    startSocketForTransferringDataForServer(closeConnection)
                }
            }
        }

    }

    var job: Job? = null
    var mClientSocket: Socket? = null
    fun startSocketForTransferringDataForClient(mySelectedData: MutableList<SharingFileModel?>) {
        job?.cancel()
        wifiP2pManager?.requestConnectionInfo(
            channel
        ) { connectionINfo ->
            job = CoroutineScope(Dispatchers.Default).launch {
                if (mClientSocket?.isClosed == true) {
                    mClientSocket?.close()
                }
                wakeLock?.acquire()

                val host = connectionINfo.groupOwnerAddress
                val port = 5000
                var len = 0
                val buf = ByteArray(1024)
                mClientSocket = Socket()
                try {

                    mClientSocket?.bind(null)
                    mClientSocket?.connect(InetSocketAddress(host, port), 5000)

                    val connectedOutputStream: OutputStream? = mClientSocket?.outputStream


                    val objectOutputStream = ObjectOutputStream(connectedOutputStream)
                    var totalProgress = 0L
                    mySelectedData.forEach {
                        when (it?.mimeType) {

                            FileMimes.FILE_VIDEO -> {
                                val mFile = File(it.videoData?.uri ?: "")
                                totalProgress += mFile.length()
                            }
                            FileMimes.FILE_IMAGE -> {
                                val mFile = File(it.imageData?.uri ?: "")
                                totalProgress += mFile.length()
                            }
                            FileMimes.FILE_APP -> {
                                val mFile = File(it.appData?.apkPath ?: "")
                                totalProgress += mFile.length()
                            }
                            FileMimes.FILE_FILE -> {
                                val mFile = File(it.fileData?.uri ?: "")
                                totalProgress += mFile.length()
                            }
                            FileMimes.FILE_AUDIO -> {
                                val mFile = File(it.audioDat?.uri ?: "")
                                totalProgress += mFile.length()
                            }
                            else -> {

                            }

                        }
                    }
                    val fileModel =
                        FileModel(
                            ALLFILESIZE,
                            0,
                            null,
                            allFileSize = totalProgress,
                            state = SendingStatus.InitialState
                        )
                    objectOutputStream.writeObject(fileModel)
                    objectOutputStream.flush()

                    dataTransferringStateForSender.value =
                        DataTransferringModel(SendingStatus.Transferring, totalProgress, 0, 0)
                    mySelectedData.forEach {
                        var name = ""
                        var mFile: File? = null
                        val mimeType: FileMimes = it?.mimeType ?: FileMimes.FILE_APP
                        when (it?.mimeType) {

                            FileMimes.FILE_VIDEO -> {
                                mFile = File(it.videoData?.uri ?: "")
                                name = it.videoData?.name ?: ""
                            }
                            FileMimes.FILE_IMAGE -> {
                                mFile = File(it.imageData?.uri ?: "")
                                name = it.imageData?.name ?: ""
                            }
                            FileMimes.FILE_APP -> {
                                mFile = File(it.appData?.apkPath ?: "")
                                name = it.appData?.appName + ".apk"
                            }
                            FileMimes.FILE_FILE -> {
                                mFile = File(it.fileData?.uri ?: "")
                                name = it.fileData?.name ?: ""
                            }
                            FileMimes.FILE_AUDIO -> {
                                mFile = File(it.audioDat?.uri ?: "")
                                name = it.audioDat?.name ?: ""
                            }
                            else -> {

                            }

                        }

                        var fileModel: FileModel?
                        if (mimeType == FileMimes.FILE_CONTACTS) {
                            fileModel =
                                FileModel(
                                    name,
                                    0,
                                    null,

                                    mimeType,
                                    state = null,
                                    ContactData = it?.contactData


                                )
                            objectOutputStream.writeObject(fileModel)
                            objectOutputStream.flush()
                        } else {

                            fileModel =
                                FileModel(
                                    name,
                                    mFile?.length()?.toInt() ?: 0,
                                    null,
                                    mimeType,
                                    state = SendingStatus.InitialState,
                                    null
                                )
                            objectOutputStream.writeObject(fileModel)
                            objectOutputStream.flush()

                            val inputStream =
                                applicationContext.contentResolver.openInputStream(
                                    Uri.fromFile(
                                        mFile
                                    )
                                )
                            while (inputStream?.read(buf).also { len = it ?: 0 } != -1) {

                                if (mClientSocket?.isConnected == true) {

                                    objectOutputStream.write(buf, 0, len)
                                    objectOutputStream.flush()
                                    startCalculations(len, true)
                                    //launch {
                                    val currentPro =
                                        dataTransferringStateForSender.value.currentProgress + len
                                    val percentage =
                                        calculateCurrentProgressForMe(totalProgress, currentPro)
                                    dataTransferringStateForSender.value =
                                        DataTransferringModel(
                                            SendingStatus.Transferring,
                                            totalProgress,
                                            currentPro,
                                            percentage
                                        )
                                    // }

                                } else {
                                    throw IOException()
                                }
                            }
                            inputStream?.close()

                        }

                    }

                    objectOutputStream.writeObject(
                        FileModel(
                            COMPLETED,
                            0,
                            null,
                            mimeType = null
                        )
                    )

                    objectOutputStream.close()
                    withContext(Dispatchers.Main) {

                        dataTransferringStateForSender.value =
                            DataTransferringModel(
                                SendingStatus.TransferCompleted,
                                0,
                                0,
                                100
                            )
                    }

                    Log.d("clienterror", "success")
                } catch (Ex: Exception) {
                    Log.d("clienterror", Ex.message.toString())
                    withContext(Dispatchers.Main) {

                        dataTransferringStateForSender.value =
                            DataTransferringModel(SendingStatus.TransferFailed, 0, 0, 0)
                    }


                } finally {
                    Log.d("clienterror", "success")
                    if (wakeLock?.isHeld == true) {

                        wakeLock?.release(ON_AFTER_RELEASE)
                    }
                    mClientSocket.takeIf { it?.isConnected == true }?.apply {
                        close()
                    }

                }
            }

        }

    }


    fun startCalculations(len: Int, b: Boolean) {

    }

    fun calculateCurrentProgressForMe(total: Long, current: Long): Int {
        val gg = (current.toDouble() / total.toDouble())
        Log.d("mValue", "$current $total $gg")
        //    val number2digits: Double = String.format("%.2f", gg).toDouble()

        return (gg * 100).toInt()
    }

    override fun onDestroy() {

        try {

            dataTransferringStateForSender.value =
                DataTransferringModel(SendingStatus.InitialState, 0, 0, 0)
            dataTransferringStateForReceiver.value =
                DataTransferringModel(SendingStatus.InitialState, 0, 0, 0)
            if (mClientSocket?.isClosed == false)
                mClientSocket?.close()

            if (serverSocket?.isClosed == false)
                serverSocket?.close()

            wifiReciever?.let {

                unregisterReceiver(wifiReciever)
            }
        } catch (ex: java.lang.Exception) {

        }

        isConnectedWithEachOther.value = false
        super.onDestroy()

    }


    inner class LocalBinder : Binder(), IBinder {
        val service: WifiDirectSenderService
            get() = this@WifiDirectSenderService
    }

    override fun onBind(p0: Intent?): IBinder {
        return LocalBinder()
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The [Context].
     */


    companion object {
        const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        const val ACTION_BROADCAST_For_FriendlistCLick = "$PACKAGE_NAME.broadcastfriendlistclick"
        const val ACTION_BROADCAST_FOR_CONTACTList = "$PACKAGE_NAME.broadcastForContactList"
        const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
    }

    var wifiReciever: WifiStateReceiverForService2? = null
    fun setWifiDirectBroadCastReceiver() {
        val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val channel = manager.initialize(applicationContext, application.mainLooper, null)

        val intentFilter = IntentFilter()
        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)


        var isWifiDirectConnected = true
        wifiReciever = WifiStateReceiverForService2(
            manager, channel,
            OnP2pConnectionChanges = {
                WifiDirectUtil.checkIfDeviceIsConnected(manager, channel) {
                    if (!it) {
                        stopService()
                    }
                }
            },

            )

        applicationContext.registerReceiver(wifiReciever!!, intentFilter)


    }

    data class DataTransferringModel(
        var status: SendingStatus,
        var totalProgress: Long,
        var currentProgress: Long,
        var percentage: Int,
    )

    class WifiStateReceiverForService2(
        var manager: WifiP2pManager,
        var channel: WifiP2pManager.Channel,
        var OnP2pConnectionChanges: () -> Unit = {},


        ) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {

                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION,
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION,

                -> {
                    Log.d("checkstate", "Rejection3")
                    OnP2pConnectionChanges()

                }
            }

        }
    }
}

