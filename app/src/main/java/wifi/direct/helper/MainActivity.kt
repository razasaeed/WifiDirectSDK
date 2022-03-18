package wifi.direct.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import wifidirect.helper.sdk.helpers.enums.SendingStatus
import wifidirect.helper.sdk.helpers.managers.PermissionManager
import wifidirect.helper.sdk.helpers.managers.WifiDirectManager
import wifidirect.helper.sdk.services.WifiDirectSenderService

class MainActivity : AppCompatActivity(), WifiP2pManager.ConnectionInfoListener,
    WifiP2pManager.ChannelListener, WifiP2pManager.GroupInfoListener {

    var wifiDirectManager: WifiDirectManager? = null

    /** Messenger for communicating with the service.  */
    private var mService: WifiDirectSenderService? = null

    /** Flag indicating whether we have called bind on the service.  */
    private var bound: Boolean = false
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder: WifiDirectSenderService.LocalBinder =
                service as WifiDirectSenderService.LocalBinder
            mService = binder.service
            bound = true

            if (mService?.dataTransferringStateForSender?.value?.status == SendingStatus.Transferring || mService?.dataTransferringStateForSender?.value?.status == SendingStatus.TransferCompleted) {

            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifiDirectManager = WifiDirectManager(this, lifecycleScope)

    }

    override fun onResume() {
        super.onResume()
        wifiDirectManager?.checkDeviceConnection(this) {
            wifiDirectManager?.isConnect = it
            if (!it) {
                wifiDirectManager?.connectedDevice?.value = null
                setUpPermissions(this)
            } else {
                wifiDirectManager?.isConnect = true
                wifiDirectManager?.init()
            }
            observePeers(this)
        }
    }

    fun setUpPermissions(context: Context) {
        val isPermissionEnabled = PermissionManager.checkForPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            context
        )

        if (isPermissionEnabled) {
            wifiDirectManager?.init()
        }
    }

    @SuppressLint("MissingPermission")
    fun observePeers(context: Context) {
        lifecycleScope.launch {
            launch {
                wifiDirectManager?.connectedDevice?.collect {
                    if (it!=null) {
                        Log.d("checkdata", it.toString())
                    } else {
                        Log.d("checkdata", "null")
                    }
                }
            }

            launch {
                wifiDirectManager?.foundDevicesList?.collect {
                    if (it!=null) {
                        Log.d("checkdata1", it.toString())
                    } else {
                        Log.d("checkdata1", "null")
                    }
                }
            }
        }
    }

    override fun onConnectionInfoAvailable(p0: WifiP2pInfo?) {
        Log.d("statecheck", p0?.groupFormed.toString())
    }

    override fun onChannelDisconnected() {
        Log.d("statecheck", "disconnected")
    }

    override fun onGroupInfoAvailable(p0: WifiP2pGroup?) {
        Log.d("statecheck", p0?.isGroupOwner.toString())
    }

}