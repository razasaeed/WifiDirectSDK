package wifidirect.helper.sdk.helpers.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionManager {

    fun checkForPermissions(permission: String, context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun checkForStoragePermissions(context: Context, isPer: (Boolean) -> Unit) {
        if (!checkForPermissions(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                context
            )
        ) {
            isPer(false)
        } else {
            isPer(true)
        }
    }

    fun checkForMultiplePermissions(permissions: Array<String>, context: Context) {
        ActivityCompat.requestPermissions(context as Activity, permissions, 1)
    }

}