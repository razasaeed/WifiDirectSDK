package wifidirect.helper.sdk.helpers

import android.annotation.SuppressLint
import android.net.wifi.p2p.WifiP2pManager
import java.lang.Exception

object WifiDirectUtil {

    @SuppressLint("MissingPermission")
    fun checkIfDeviceIsConnected(
        manager: WifiP2pManager?,
        channel: WifiP2pManager.Channel?,
        isConnected: (back: Boolean) -> Unit
    ) {
        manager?.requestGroupInfo(channel) {
            isConnected(it != null && ((it.isGroupOwner && !it.clientList.isNullOrEmpty()) || !it.isGroupOwner))
        }
    }

    fun deletePersistentGroup(
        manager: WifiP2pManager?,
        channel: WifiP2pManager.Channel?,
        alldeleted: () -> Unit
    ) {
        try {
            val methods = WifiP2pManager::class.java.methods
            for (i in methods.indices) {
                if (methods[i].name == "deletePersistentGroup") {
                    // Delete any persistent group
                    for (netid in 0..31) {
                        methods[i].invoke(manager, channel, netid, null)
                    }
                }
            }
            alldeleted()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

}