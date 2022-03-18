package wifidirect.helper.sdk

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ApplicationClass : Application(), LifecycleObserver {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        CoroutineScope(Dispatchers.Default).launch {
            val manager: WifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
//            val channel = manager.initialize(this@ApplicationClass, mainLooper, null)

            /*WifiDirectUtil.checkIfDeviceIsConnected(manager , channel){
                if(!it){
                    try {
                        stopService(Intent(this@ApplicationClass,
                            WifiDirectSenderService::class.java))
                    }catch (ex:Exception){

                    }

                }
            }*/

            Log.d("flowcheckmain", "this")

       }

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {

    }
}