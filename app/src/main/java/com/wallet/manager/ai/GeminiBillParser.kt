package com.wallet.manager.ai

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ParsedBill(
    val type: String,
    val title: String,
    val content: String,
    val amount: Double,
    val dateMillis: Long
)

class GeminiBillParser(
    private val apiKey: String
) {
    suspend fun parseBill(bitmap: Bitmap): ParsedBill? = withContext(Dispatchers.IO) {
        val prompt = """
            Hãy đọc hóa đơn tiếng Việt và trả về JSON duy nhất với format:
            {
              "type": "Ăn uống | Di chuyển | Mua sắm | ...",
              "title": "Tiêu đề ngắn",
              "content": "Mô tả chi tiết",
              "amount": 120000.0,
              "dateMillis": 1690848000000
            }
            Chỉ trả về JSON, không thêm giải thích.
        """.trimIndent()

        val imageBytes = ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            stream.toByteArray()
        }

        val parts = JSONArray()
            .put(JSONObject().put("text", prompt))
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", Base64.encodeToString(imageBytes, Base64.NO_WRAP))
                )
            )

        val requestBody = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))

        val responseText = GeminiRestClient.generateContent(apiKey, requestBody)
        val jsonText = GeminiRestClient.extractText(responseText)
            ?.trim()
            ?.removePrefix("```json")
            ?.removePrefix("```")
            ?.removeSuffix("```")
            ?.trim()
            ?: return@withContext null

        val json = JSONObject(jsonText)
        ParsedBill(
            type = json.optString("type"),
            title = json.optString("title"),
            content = json.optString("content"),
            amount = json.optDouble("amount"),
            dateMillis = json.optLong("dateMillis")
        )
    }
}

internal object GeminiRestClient {
    private const val MODEL = "gemini-3.1-flash-lite-preview"

    fun generateContent(apiKey: String, body: JSONObject): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException(response)
        }
        return response
    }

    fun extractText(rawResponse: String): String? {
        val root = JSONObject(rawResponse)
        val candidates = root.optJSONArray("candidates") ?: return null
        if (candidates.length() == 0) return null
        val content = candidates.optJSONObject(0)?.optJSONObject("content") ?: return null
        val parts = content.optJSONArray("parts") ?: return null
        if (parts.length() == 0) return null
        return parts.optJSONObject(0)?.optString("text")
    }
}
