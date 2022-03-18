package wifidirect.helper.sdk.models

import wifidirect.helper.sdk.helpers.enums.FileMimes

data class SharingFileModel(
    val mimeType: FileMimes?,
    val imageData: ImageListModel?=null,
    val videoData: VideoListModel?=null,
    val audioDat: AudioListModel?=null,
    val fileData: AllFileModel?=null,
    val contactData: ContactModel?=null,
    val appData: AppList?=null,
)
