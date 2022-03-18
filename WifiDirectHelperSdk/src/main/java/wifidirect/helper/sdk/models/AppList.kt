package wifidirect.helper.sdk.models

data class AppList(
    var appName: String,
    var size: Long?,
    val packageName: String,
    val apkPath: String,
    val firstInstallTime: Long
)