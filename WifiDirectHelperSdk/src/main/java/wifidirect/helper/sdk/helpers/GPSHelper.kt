package wifidirect.helper.sdk.helpers

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.location.LocationManager
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task

object GPSHelper {

    fun checkIfGpsIsOn(context: Context):Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    }

    fun checkGPSEnabled(context: Context) {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER).not()) {
            turnOnGPS(context)
        }
    }

    private fun turnOnGPS(context: Context) {
        val request = LocationRequest.create().apply {
            interval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(request)
        val client: SettingsClient = LocationServices.getSettingsClient(context)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnFailureListener {
            if (it is ResolvableApiException) {
                try {
                    it.startResolutionForResult(context as Activity, 12345)

                } catch (sendEx: IntentSender.SendIntentException) {

                }
            }
        }.addOnSuccessListener {
            // Successfully on
        }
    }
}