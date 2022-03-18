package wifidirect.helper.sdk.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wifidirect.helper.sdk.helpers.WifiDirectUtil
import wifidirect.helper.sdk.helpers.managers.WifiDirectManager

class WifiStateReceiver(
    var manager: WifiP2pManager,
    var channel: WifiP2pManager.Channel,
    var OnP2pConnectionChanges: () -> Unit = {},
    var isWifiDirectEnabled: (enab: Boolean) -> Unit = {},
    var userRecjectionCheck: () -> Unit = {},
    var wifiManager: WifiDirectManager?,
    var lifecycleCoroutineScope: LifecycleCoroutineScope,
    var wifiDevicesList: (list: WifiP2pDeviceList) -> Unit = {}

) : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Determine if Wifi P2P mode is enabled or not, alert
                // the Activity.
                Log.d("checkstate", "WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION")
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    isWifiDirectEnabled(true)
                } else {
                    isWifiDirectEnabled(false)
                }
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                Log.d("checkstate", "WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION")

                manager.requestPeers(channel) {
                    wifiDevicesList(it)

                }

            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                Log.d("checkstate", "WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION")
                userRecjectionCheck()
                OnP2pConnectionChanges()

            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                Log.d("checkstate", "WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION")
                userRecjectionCheck()
            }

            WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {

                val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
                when (state) {
                    2 -> {
                        wifiManager?.isDiscovering = true
                    }
                    1 -> {
                        wifiManager?.isDiscovering = false
                        lifecycleCoroutineScope.launch {
                            delay(1500)
                            WifiDirectUtil.checkIfDeviceIsConnected(
                                wifiManager?.manager,
                                wifiManager?.channel
                            ) {
                                if (!it) {
                                    wifiManager?.startScanning()
                                }
                            }
                        }
                    }
                }

            }


        }
    }
}