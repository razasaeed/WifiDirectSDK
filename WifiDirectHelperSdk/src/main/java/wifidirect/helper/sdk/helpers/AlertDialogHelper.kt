package wifidirect.helper.sdk.helpers

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Button
import wifidirect.helper.sdk.R

object AlertDialogHelper {

    fun makeWaitAlertDialog(context: Context): AlertDialog? {
       val waitDiolg = AlertDialog.Builder(context).create()
        waitDiolg?.setCancelable(false)
        waitDiolg?.setView(LayoutInflater.from(context).inflate(R.layout.waitcustomdiolog, null))
        return waitDiolg
    }

    fun makeWifiDirectNoServiceAvailableDialog(context: Context): AlertDialog? {
        val waitDiolg = AlertDialog.Builder(context).create()
        waitDiolg.setCancelable(false)
        waitDiolg.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val view=LayoutInflater.from(context).inflate(R.layout.wifi_direct_noreceiver,null)
        val btn = view.findViewById<Button>(R.id.receivernotreadyBtn)
        waitDiolg.setView(view)
        btn.setOnClickListener {
          waitDiolg.dismiss()
        }
        return waitDiolg
    }
}