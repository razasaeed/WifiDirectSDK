package wifidirect.helper.sdk.models

import wifidirect.helper.sdk.helpers.enums.FileMimes
import wifidirect.helper.sdk.helpers.enums.SendingStatus
import java.io.Serializable

class FileModel(
     val fileName: String,
     val fileSize: Int,
     val byteArray: ByteArray?=null,
     val mimeType: FileMimes?=null,
     val state:SendingStatus?=null,
     val allFileSize:Long?=null,
     val ContactData: ContactModel?=null
) : Serializable