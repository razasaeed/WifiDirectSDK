package wifidirect.helper.sdk.helpers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import wifidirect.helper.sdk.R

object NotificationHelper {
    val WIFIDIRECT_SENDDATANOTIFICAION_CHANNELID = "19"

    @RequiresApi(Build.VERSION_CODES.O)
    fun makeNotificationChannel(
        path: Uri?= RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        context: Context,
        notificationManager: NotificationManager,
        patternVibrate: LongArray?=longArrayOf(1000, 1000),
        name:String = "Wifi Direct Notification",

        id:String= WIFIDIRECT_SENDDATANOTIFICAION_CHANNELID


    ) {
        val channel = NotificationChannel(
            id,
            name,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.lightColor = Color.GRAY
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.vibrationPattern = patternVibrate
        channel.description = context.resources.getString(R.string.app_name)
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
        channel.setSound(path, audioAttributes)
        notificationManager.createNotificationChannel(channel)

    }

}