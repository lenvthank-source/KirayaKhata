package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ExtractedBillData(
    val consumerNumber: String? = null,
    val prevReading: Double? = null,
    val currReading: Double? = null,
    val unitsConsumed: Double? = null,
    val billAmount: Double? = null,
    val summaryNote: String = "",
    val rawTextPreview: String = "",
    val engineLabel: String = "Offline ML Kit OCR"
) {
    val hasAnyUsefulField: Boolean
        get() = !consumerNumber.isNullOrBlank() ||
            prevReading != null ||
            currReading != null ||
            unitsConsumed != null ||
            billAmount != null
}

object IndianBillTextParser {

    private val consumerPatterns = listOf(
        Regex(
            """(?i)\b(?:CA|C\.A\.|Consumer|Account|Service|Customer|K)\s*(?:No\.?|Number|ID|#)?\s*[:\-]?\s*([A-Z0-9]{6,16})\b"""
        )
    )

    private val prevReadingPatterns = listOf(
        Regex(
            """(?i)(?:Prev(?:ious)?|Old|Initial|Past|Last)\s*(?:Meter\s*)?(?:Rdg|Reading|kWh)?\s*[:\-]?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
        )
    )

    private val currReadingPatterns = listOf(
        Regex(
            """(?i)(?:Curr(?:ent)?|Present|New|Closing|Final)\s*(?:Meter\s*)?(?:Rdg|Reading|kWh)?\s*[:\-]?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
        )
    )

    private val unitsPatterns = listOf(
        Regex(
            """(?i)(?:Units?\s*(?:Consumed|Billed)?|Billed\s*Units?|Total\s*Units?|Net\s*Units?|Consumption|kWh\s*Consumed)\s*[:\-]?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
        ),
        Regex(
            """(?i)\b([0-9]+(?:\.[0-9]{1,2})?)\s*(?:kWh|Units)\b"""
        )
    )

    private val amountPatterns = listOf(
        Regex(
            """(?i)(?:Net\s*Payable|Total\s*(?:Bill\s*)?(?:Amount|Payable|Due)|Amount\s*Payable|Bill\s*Amount|Payable\s*Amount|Current\s*Bill)\s*[:\-]?\s*(?:₹|Rs\.?|INR)?\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
        ),
        Regex(
            """(?i)(?:₹|Rs\.?|INR)\s*([0-9]+(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?)"""
        )
    )

    private val dueDatePattern = Regex(
        """(?i)(?:Due\s*Date|Pay\s*By)\s*[:\-]?\s*([0-9]{1,2}[\-/][A-Za-z0-9]{2,3}[\-/][0-9]{2,4})"""
    )

    fun parse(rawText: String, engineLabel: String = "Offline ML Kit OCR"): ExtractedBillData {
        val cleaned = rawText.replace("\r\n", "\n")

        val consumer = consumerPatterns.firstNotNullOfOrNull { regex ->
            regex.findAll(cleaned)
                .map { it.groupValues[1].trim() }
                .firstOrNull { candidate -> candidate.any { it.isDigit() } && candidate.length >= 6 }
        }

        var prev = findFirstNumber(cleaned, prevReadingPatterns, maxAllowed = 99_999_999.0)
        var curr = findFirstNumber(cleaned, currReadingPatterns, maxAllowed = 99_999_999.0)
        var units = findFirstNumber(cleaned, unitsPatterns, maxAllowed = 1_000_000.0)

        if (prev != null && curr != null && curr >= prev && units == null) {
            units = round2(curr - prev)
        } else if (curr != null && units != null && prev == null && curr >= units) {
            prev = round2(curr - units)
        } else if (prev != null && units != null && curr == null) {
            curr = round2(prev + units)
        }

        // Handle bare digital sub-meter photo (only numbers visible, no bill labels)
        if (prev == null && curr == null && units == null) {
            val standaloneMeter = Regex("""\b([0-9]{3,7}(?:\.[0-9]{1,2})?)\b""")
                .findAll(cleaned)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }
                .firstOrNull { it in 1.0..9_999_999.0 }
            if (standaloneMeter != null && consumer == null) {
                curr = standaloneMeter
            }
        }

        val labeledAmount = findFirstNumber(cleaned, listOf(amountPatterns[0]), maxAllowed = 1_000_000.0)
        val currencyAmount = amountPatterns[1].findAll(cleaned)
            .mapNotNull { parseCleanDouble(it.groupValues[1]) }
            .filter { it in 10.0..1_000_000.0 }
            .maxOrNull()
        val billAmount = labeledAmount ?: currencyAmount

        val dueMatch = dueDatePattern.find(cleaned)?.groupValues?.getOrNull(1)?.trim()
        val note = if (!dueMatch.isNullOrBlank()) "OCR • Due Date: $dueMatch" else ""

        return ExtractedBillData(
            consumerNumber = consumer,
            prevReading = prev,
            currReading = curr,
            unitsConsumed = units,
            billAmount = billAmount,
            summaryNote = note,
            rawTextPreview = cleaned.trim().take(280),
            engineLabel = engineLabel
        )
    }

    private fun findFirstNumber(
        text: String,
        patterns: List<Regex>,
        maxAllowed: Double
    ): Double? {
        for (regex in patterns) {
            for (match in regex.findAll(text)) {
                val parsed = parseCleanDouble(match.groupValues[1])
                if (parsed != null && parsed in 0.0..maxAllowed) {
                    return round2(parsed)
                }
            }
        }
        return null
    }

    private fun parseCleanDouble(raw: String): Double? {
        return raw.replace(",", "").trim().toDoubleOrNull()
    }

    private fun round2(v: Double): Double = kotlin.math.round(v * 100.0) / 100.0
}

class BillOcrEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    suspend fun extractBillData(
        context: Context,
        uri: Uri,
        preferGemini: Boolean,
        userGeminiApiKey: String
    ): Result<ExtractedBillData> {
        val bitmapResult = withContext(Dispatchers.IO) {
            loadBitmapFromUri(context, uri)
        }
        val bitmap = bitmapResult.getOrElse { return Result.failure(it) }

        return try {
            if (preferGemini && userGeminiApiKey.isNotBlank()) {
                val aiResult = extractWithUserGeminiKey(bitmap, userGeminiApiKey.trim())
                if (aiResult.isSuccess) {
                    return aiResult
                }
                val errMsg = aiResult.exceptionOrNull()?.localizedMessage ?: "Gemini OCR failed"
                Log.w(TAG, "Gemini OCR failed, falling back to offline ML Kit: $errMsg")
                val fallback = extractWithMlKit(bitmap)
                fallback.map { data ->
                    data.copy(
                        engineLabel = "Offline ML Kit (Gemini fallback: ${errMsg.take(60)})"
                    )
                }
            } else {
                extractWithMlKit(bitmap)
            }
        } finally {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private suspend fun extractWithMlKit(bitmap: Bitmap): Result<ExtractedBillData> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { result ->
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener { error ->
                        if (cont.isActive) cont.resumeWithException(error)
                    }
            }
            val parsed = IndianBillTextParser.parse(
                rawText = visionText.text,
                engineLabel = "Offline ML Kit OCR"
            )
            Result.success(parsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Offline OCR failed."))
        }
    }

    private suspend fun extractWithUserGeminiKey(
        bitmap: Bitmap,
        apiKey: String
    ): Result<ExtractedBillData> = withContext(Dispatchers.IO) {
        try {
            val jpegBytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            val promptText =
                "Extract Indian electricity bill or meter reading fields from this image. " +
                    "Return consumerNumber (CA/Consumer/Account ID), prevReading, currReading, " +
                    "unitsConsumed, billAmount (in INR rupees), and short summaryNote (e.g. due date or DISCOM)."

            val schema = JSONObject().apply {
                put("type", "OBJECT")
                put("properties", JSONObject().apply {
                    put("consumerNumber", JSONObject().put("type", "STRING").put("nullable", true))
                    put("prevReading", JSONObject().put("type", "NUMBER").put("nullable", true))
                    put("currReading", JSONObject().put("type", "NUMBER").put("nullable", true))
                    put("unitsConsumed", JSONObject().put("type", "NUMBER").put("nullable", true))
                    put("billAmount", JSONObject().put("type", "NUMBER").put("nullable", true))
                    put("summaryNote", JSONObject().put("type", "STRING").put("nullable", true))
                })
            }

            val payload = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", promptText))
                        put(JSONObject().put("inline_data", JSONObject().apply {
                            put("mime_type", "image/jpeg")
                            put("data", base64Image)
                        }))
                    })
                }))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("responseSchema", schema)
                })
            }

            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val apiMsg = try {
                        JSONObject(body).optJSONObject("error")?.optString("message")
                    } catch (_: Exception) {
                        null
                    }
                    val message = when (response.code) {
                        400, 401, 403 -> "Invalid Gemini API key (${response.code})"
                        429 -> "Daily free Gemini quota reached (429)"
                        else -> apiMsg ?: "Gemini HTTP ${response.code}"
                    }
                    return@withContext Result.failure(IOException(message))
                }

                val root = JSONObject(body)
                val text = root
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?.trim()
                    .orEmpty()

                if (text.isBlank()) {
                    return@withContext Result.failure(IOException("Empty response from Gemini"))
                }

                val parsedJson = JSONObject(text)
                val consumer = parsedJson.optString("consumerNumber")
                    .takeIf { it.isNotBlank() && it != "null" }
                val prev = parsedJson.optDoubleOrNull("prevReading")
                val curr = parsedJson.optDoubleOrNull("currReading")
                val units = parsedJson.optDoubleOrNull("unitsConsumed")
                    ?: if (prev != null && curr != null && curr >= prev) curr - prev else null
                val amount = parsedJson.optDoubleOrNull("billAmount")
                val note = parsedJson.optString("summaryNote")
                    .takeIf { it.isNotBlank() && it != "null" }
                    .orEmpty()

                Result.success(
                    ExtractedBillData(
                        consumerNumber = consumer,
                        prevReading = prev,
                        currReading = curr,
                        unitsConsumed = units,
                        billAmount = amount,
                        summaryNote = note,
                        rawTextPreview = text.take(260),
                        engineLabel = "Gemini AI OCR"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return if (value.isNaN()) null else kotlin.math.round(value * 100.0) / 100.0
    }

    private fun loadBitmapFromUri(context: Context, uri: Uri): Result<Bitmap> {
        return try {
            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val isPdf = mimeType.contains("pdf", ignoreCase = true) ||
                uri.toString().endsWith(".pdf", ignoreCase = true)

            if (isPdf) {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                    ?: return Result.failure(IOException("Unable to open selected PDF."))
                pfd.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (renderer.pageCount <= 0) {
                            return Result.failure(IOException("PDF has no readable pages."))
                        }
                        renderer.openPage(0).use { page ->
                            val scale = (1800f / maxOf(page.width, page.height)).coerceIn(1f, 3f)
                            val width = (page.width * scale).toInt().coerceAtLeast(1)
                            val height = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            return Result.success(bmp)
                        }
                    }
                }
            } else {
                val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: return Result.failure(IOException("Could not decode selected image."))
                Result.success(scaleBitmapIfNeeded(raw, maxDimension = 1920))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun scaleBitmapIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(source.width, source.height)
        if (largest <= maxDimension) return source
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val targetW = (source.width * ratio).toInt().coerceAtLeast(1)
        val targetH = (source.height * ratio).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, targetW, targetH, true)
        if (scaled !== source) {
            source.recycle()
        }
        return scaled
    }

    companion object {
        private const val TAG = "BillOcrEngine"
        private const val GEMINI_MODEL = "gemini-3.8-flash"
    }
}
