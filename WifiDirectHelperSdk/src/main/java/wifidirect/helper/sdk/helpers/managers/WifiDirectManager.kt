package wifidirect.helper.sdk.helpers.managers

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pGroup
import wifidirect.helper.sdk.helpers.WifiDirectUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import wifidirect.helper.sdk.R
import wifidirect.helper.sdk.helpers.WifiDirectUtil.deletePersistentGroup
import wifidirect.helper.sdk.helpers.AlertDialogHelper
import wifidirect.helper.sdk.receivers.WifiStateReceiver
import java.lang.Exception

class WifiDirectManager(
    var context: Context,
    var lifecycleCoroutineScope: LifecycleCoroutineScope,
    var isSender: Boolean = true,
) {
    val intentFilter = IntentFilter()
    var receiver: WifiStateReceiver? = null
    var channel: WifiP2pManager.Channel
    var manager: WifiP2pManager =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    var isDiscovering = false
    var isConnect = false
    var connectedDevice: MutableStateFlow<WifiP2pGroup?> = MutableStateFlow(null)

    var foundDevicesList: MutableStateFlow<WifiP2pDeviceList?> = MutableStateFlow(null)
    var wifiDirectEnabled: MutableStateFlow<Boolean?> = MutableStateFlow(true)
    var isRequestSend = false
    val alertDialog: AlertDialog? by lazy {
        AlertDialogHelper.makeWaitAlertDialog(context)
    }


    var counter = 0
    val timerFlow: Flow<Int> = flow {
        while (counter < 31) {
            counter++
            delay(1000)
            emit(counter)
        }

    }

    init {
        channel = manager.initialize(context, context.mainLooper, null)
    }

    fun init() {

        with(context) {

            // Indicates a change in the Wi-Fi P2P status.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

            // Indicates a change in the list of available peers.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

            // Indicates the state of Wi-Fi P2P connectivity has changed.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

            // Indicates this device's details have changed.
            intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

            intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)

            receiver = WifiStateReceiver(
                manager, channel,
                OnP2pConnectionChanges = {
                    countJob?.cancel()
                    alertDialog?.dismiss()
                    if (isConnect) {
                        lifecycleCoroutineScope.launch {

                            WifiDirectUtil.checkIfDeviceIsConnected(manager, channel) {
                                if (!it) {
                                    isConnect = false
                                    connectedDevice.value = null
                                    lifecycleCoroutineScope.launch {

                                        delay(1500)
                                        startScanning()
                                    }
                                }
                            }
                        }
                        deletePersistentGroup(manager, channel) {

                        }

                    } else {
                        checkDeviceConnection(context) {
                            if (it && isDiscovering) {

                            }
                        }
                        startScanning()
                    }
                },
                isWifiDirectEnabled = {
                    wifiDirectEnabled.value = it
                    if (it) {
                        if (!isConnect)
                            startScanning()
                    } else {
                        if (isConnect) {
                            alertDialog?.show()
                            stopConnection {
                                if (!it) {
                                    Toast.makeText(
                                        context,
                                        getString(R.string.cant_discoct),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                alertDialog?.dismiss()
                            }
                        } else if (isDiscovering) {
                            stopDiscovering {
                                alertDialog?.dismiss()

                            }

                        }

                    }
                },
                userRecjectionCheck = {
                    countJob?.cancel()
                    if (isRequestSend) {

                        WifiDirectUtil.checkIfDeviceIsConnected(manager, channel) {
                            if (!it) {

                                alertDialog?.dismiss()
                                Toast.makeText(
                                    context,
                                    getString(R.string.cant_connect),
                                    Toast.LENGTH_SHORT
                                ).show()

                                isRequestSend = false
                                startScanning()
                            }
                        }

                    }
                },
                wifiManager = this@WifiDirectManager,
                lifecycleCoroutineScope = lifecycleCoroutineScope
            ) {
                foundDevicesList.value = it

            }

            registerReceiver(receiver, intentFilter)
        }


    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        Log.d("discvering", "gg")
        isRequestSend = false
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {

            }

            override fun onFailure(reasonCode: Int) {

            }
        })
    }

    var countJob: Job? = null

    @SuppressLint("MissingPermission")
    fun connect(wifiP2pDevice: WifiP2pDevice, context: Context) {
        // Picking the first device found on the network.
        alertDialog?.show()
        val device = wifiP2pDevice
        Log.d("devicedata", wifiP2pDevice.toString())
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            //   if(!isSender){
            groupOwnerIntent = 0
            //}
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {

            override fun onSuccess() {
                isRequestSend = true
            }

            override fun onFailure(reason: Int) {
                alertDialog?.dismiss()
                Toast.makeText(
                    context,
                    context.getString(R.string.cant_connect),
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
        counter = 0
        countJob?.cancel()
        countJob = CoroutineScope(Dispatchers.Main).launch {
            try {

                timerFlow.collect {
                    if (it == 30) {

                        alertDialog?.dismiss()
                        manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {

                            }

                            override fun onFailure(p0: Int) {

                            }
                        })
                        startScanning()
                    }
                }
            } catch (ex: Exception) {

            }
        }
    }


    @SuppressLint("MissingPermission")
    fun stopConnection(
        goBack: (back: Boolean) -> Unit,
    ) {
        alertDialog?.show()
        Log.d("channel", channel.hashCode().toString())
        manager.requestGroupInfo(channel) {
            if (it == null) {
                goBack(false)
                alertDialog?.dismiss()
                return@requestGroupInfo
            }
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // alertDialog?.dismiss()
                    deletePersistentGroup(manager, channel) {
                        goBack(true)
                    }
                    // }
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(context, reason.toString(), Toast.LENGTH_SHORT).show()
                    alertDialog?.dismiss()
                    goBack(false)
                }

            })
        }

    }

    fun stopDiscovering(goBack: (back: Boolean) -> Unit) {
        manager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            @SuppressLint("MissingPermission")
            override fun onSuccess() {
                goBack(true)
            }

            override fun onFailure(p0: Int) {
                goBack(false)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun checkDeviceConnection(context: Context, isConnected: (conn: Boolean) -> Unit) {

        WifiDirectUtil.checkIfDeviceIsConnected(channel = channel, manager = manager) {
            if (it) {
                manager.requestGroupInfo(channel) {
                    isConnect = true
                    isRequestSend = false
                    stopDiscovering {
                    }
                    connectedDevice.value = it
                    isConnected(true)
                }

            } else {
                Log.d("data", "nodata")
                isConnected(false)
            }
        }
    }

}