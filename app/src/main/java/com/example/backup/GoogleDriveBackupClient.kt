package com.example.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DriveBackupMetadata(
    val fileId: String,
    val name: String,
    val modifiedTimeMs: Long,
    val sizeBytes: Long
)

class GoogleDriveBackupClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        const val BACKUP_FILE_NAME = "kiraya_khata_backup.db"
        private const val FILES_ENDPOINT = "https://www.googleapis.com/drive/v3/files"
        private const val UPLOAD_ENDPOINT = "https://www.googleapis.com/upload/drive/v3/files"
    }

    suspend fun findLatestBackup(accessToken: String): Result<DriveBackupMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                val query = URLEncoder.encode(
                    "name='$BACKUP_FILE_NAME' and trashed=false",
                    "UTF-8"
                )
                val url = "$FILES_ENDPOINT?spaces=appDataFolder&q=$query" +
                    "&orderBy=modifiedTime%20desc&pageSize=1" +
                    "&fields=files(id,name,modifiedTime,size)"

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IOException(parseApiError(response.code, bodyStr))
                        )
                    }
                    val root = JSONObject(bodyStr)
                    val files = root.optJSONArray("files")
                    if (files == null || files.length() == 0) {
                        return@withContext Result.success(null)
                    }
                    val item = files.getJSONObject(0)
                    Result.success(parseMetadata(item))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun uploadDatabaseBackup(
        accessToken: String,
        dbFile: File
    ): Result<DriveBackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val existing = findLatestBackup(accessToken).getOrElse { return@withContext Result.failure(it) }

            val metadataJson = JSONObject().apply {
                put("name", BACKUP_FILE_NAME)
                put("mimeType", "application/x-sqlite3")
                if (existing == null) {
                    put("parents", JSONArray().put("appDataFolder"))
                }
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(dbFile.asRequestBody("application/x-sqlite3".toMediaType()))
                .build()

            val requestBuilder = Request.Builder()
                .header("Authorization", "Bearer $accessToken")

            val request = if (existing != null) {
                requestBuilder
                    .url("$UPLOAD_ENDPOINT/${existing.fileId}?uploadType=multipart&fields=id,name,modifiedTime,size")
                    .patch(multipartBody)
                    .build()
            } else {
                requestBuilder
                    .url("$UPLOAD_ENDPOINT?uploadType=multipart&fields=id,name,modifiedTime,size")
                    .post(multipartBody)
                    .build()
            }

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException(parseApiError(response.code, bodyStr))
                    )
                }
                val parsed = parseMetadata(JSONObject(bodyStr))
                val normalized = if (parsed.sizeBytes > 0L) {
                    parsed
                } else {
                    parsed.copy(sizeBytes = dbFile.length())
                }
                Result.success(normalized)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun downloadBackup(
        accessToken: String,
        fileId: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$FILES_ENDPOINT/$fileId?alt=media")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string().orEmpty()
                    return@withContext Result.failure(
                        IOException(parseApiError(response.code, err))
                    )
                }
                val stream = response.body?.byteStream()
                    ?: return@withContext Result.failure(IOException("Empty download stream from Google Drive."))

                destinationFile.parentFile?.mkdirs()
                stream.use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseMetadata(obj: JSONObject): DriveBackupMetadata {
        val modifiedIso = obj.optString("modifiedTime")
        return DriveBackupMetadata(
            fileId = obj.getString("id"),
            name = obj.optString("name", BACKUP_FILE_NAME),
            modifiedTimeMs = parseRfc3339Millis(modifiedIso),
            sizeBytes = obj.optString("size").toLongOrNull() ?: obj.optLong("size", 0L)
        )
    }

    private fun parseRfc3339Millis(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(iso)
                if (date != null) return date.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    private fun parseApiError(statusCode: Int, body: String): String {
        val extracted = try {
            JSONObject(body).optJSONObject("error")?.optString("message")
        } catch (_: Exception) {
            null
        }
        return when {
            statusCode == 401 -> "Google Drive authorization expired. Please tap Back Up Now to reconnect."
            !extracted.isNullOrBlank() -> "Google Drive error ($statusCode): $extracted"
            else -> "Google Drive request failed (HTTP $statusCode)."
        }
    }
}
