package com.example.ocr

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object BillScanUriHelper {
    fun createTempCameraImageUri(context: Context): Uri {
        val dir = File(context.cacheDir, "bill_scans").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
