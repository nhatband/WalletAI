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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max

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
        val todayMillis = System.currentTimeMillis()
        val today = billDateFormatter().format(Date(todayMillis))
        val prompt = """
            Bạn là bộ đọc hóa đơn/biên lai cho app quản lý chi tiêu cá nhân.
            Hôm nay là $today.

            Hãy đọc ảnh và chỉ trả về một JSON hợp lệ theo format:
            {
              "type": "Ăn uống",
              "title": "Tiêu đề ngắn",
              "content": "Mô tả ngắn gồm cửa hàng và vài mặt hàng chính nếu thấy",
              "amount": 120000.0,
              "dateText": "14/05/2026"
            }

            Quy tắc:
            - type chỉ chọn một trong: Ăn uống, Di chuyển, Mua sắm, Giải trí, Học tập, Khác.
            - amount là tổng tiền cuối cùng khách phải trả, đơn vị VND, không lấy tiền tạm tính nếu có tổng thanh toán.
            - Nếu có nhiều số tiền, ưu tiên các nhãn: tổng cộng, thành tiền, thanh toán, total, grand total, amount paid.
            - dateText là ngày in trên hóa đơn, định dạng dd/MM/yyyy.
            - Không tự tính timestamp/epoch.
            - Nếu hóa đơn có giờ thì bỏ giờ, chỉ lấy ngày.
            - Nếu ngày trên hóa đơn thiếu năm, dùng năm hiện tại.
            - Nếu không thấy ngày, dùng ngày hôm nay: $today.
            - title ngắn gọn, ví dụ tên cửa hàng hoặc "Hóa đơn ăn uống".
            - Nếu ảnh không phải hóa đơn/biên lai hoặc không đọc được tổng tiền, trả JSON với amount = 0.
            - Chỉ trả về JSON, không thêm giải thích, không dùng markdown.
        """.trimIndent()

        val preparedBitmap = bitmap.scaledForGemini()
        val imageBytes = ByteArrayOutputStream().use { stream ->
            preparedBitmap.compress(Bitmap.CompressFormat.JPEG, 82, stream)
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
            .put(
                "generationConfig",
                JSONObject()
                    .put("response_mime_type", "application/json")
                    .put(
                        "response_schema",
                        JSONObject()
                            .put("type", "OBJECT")
                            .put(
                                "properties",
                                JSONObject()
                                    .put("type", JSONObject().put("type", "STRING"))
                                    .put("title", JSONObject().put("type", "STRING"))
                                    .put("content", JSONObject().put("type", "STRING"))
                                    .put("amount", JSONObject().put("type", "NUMBER"))
                                    .put("dateText", JSONObject().put("type", "STRING"))
                            )
                            .put("required", JSONArray().put("type").put("title").put("content").put("amount").put("dateText"))
                    )
            )
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))

        val responseText = GeminiRestClient.generateContent(apiKey, requestBody)
        val jsonText = GeminiRestClient.extractJsonText(responseText) ?: return@withContext null
        if (jsonText.equals("null", ignoreCase = true)) return@withContext null

        val json = JSONObject(jsonText)
        val amount = json.optDouble("amount", 0.0)
        if (amount <= 0.0 || json.optString("title").isBlank()) return@withContext null

        ParsedBill(
            type = json.optString("type").normalizeBillType(),
            title = json.optString("title").ifBlank { "Hóa đơn" },
            content = json.optString("content"),
            amount = amount,
            dateMillis = parseBillDateMillis(json.optString("dateText"), todayMillis)
        )
    }

    private fun parseBillDateMillis(rawDate: String, fallbackMillis: Long): Long {
        val normalizedInput = rawDate
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace('.', '/')
            .replace('-', '/')
        val normalized = Regex("\\d{1,4}/\\d{1,2}/\\d{1,4}|\\d{1,2}/\\d{1,2}")
            .find(normalizedInput)
            ?.value
            ?: normalizedInput.substringBefore(" ").substringBefore("T")

        if (normalized.isBlank()) return startOfDayMillis(fallbackMillis)

        val currentYear = Calendar.getInstance(VIETNAM_TIME_ZONE).get(Calendar.YEAR)
        val candidates = mutableListOf(normalized).apply {
            if (Regex("^\\d{1,2}/\\d{1,2}$").matches(normalized)) {
                add("$normalized/$currentYear")
            }
        }

        val patterns = listOf("dd/MM/yyyy", "d/M/yyyy", "dd/MM/yy", "d/M/yy", "yyyy/MM/dd", "yyyy/M/d")
        for (candidate in candidates) {
            for (pattern in patterns) {
                runCatching {
                    SimpleDateFormat(pattern, Locale("vi", "VN")).apply {
                        isLenient = false
                        timeZone = VIETNAM_TIME_ZONE
                    }.parse(candidate)
                }.getOrNull()?.let { parsed ->
                    return startOfDayMillis(parsed.time)
                }
            }
        }

        return startOfDayMillis(fallbackMillis)
    }

    private fun startOfDayMillis(timeMillis: Long): Long {
        return Calendar.getInstance(VIETNAM_TIME_ZONE).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun billDateFormatter(): SimpleDateFormat {
        return SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply {
            timeZone = VIETNAM_TIME_ZONE
        }
    }

    private fun Bitmap.scaledForGemini(maxSide: Int = 1600): Bitmap {
        val longestSide = max(width, height)
        if (longestSide <= maxSide) return this

        val scale = maxSide.toFloat() / longestSide.toFloat()
        val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    }

    private fun String.normalizeBillType(): String {
        val value = trim().lowercase(Locale("vi", "VN"))
        return when {
            value.contains("ăn") || value.contains("uong") || value.contains("uống") || value.contains("food") -> "Ăn uống"
            value.contains("di chuyển") || value.contains("di chuyen") || value.contains("transport") || value.contains("xe") -> "Di chuyển"
            value.contains("mua") || value.contains("shopping") || value.contains("siêu thị") || value.contains("sieu thi") -> "Mua sắm"
            value.contains("giải trí") || value.contains("giai tri") || value.contains("entertainment") -> "Giải trí"
            value.contains("học") || value.contains("hoc") || value.contains("study") -> "Học tập"
            else -> "Khác"
        }
    }

    companion object {
        private val VIETNAM_TIME_ZONE: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }
}

internal object GeminiRestClient {
    private const val MODEL = "gemini-2.5-flash-lite"

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
        return buildString {
            for (index in 0 until parts.length()) {
                val text = parts.optJSONObject(index)?.optString("text").orEmpty()
                if (text.isNotBlank()) {
                    if (isNotEmpty()) append('\n')
                    append(text)
                }
            }
        }.ifBlank { null }
    }

    fun extractJsonText(rawResponse: String): String? {
        val text = extractText(rawResponse)?.trim() ?: return null
        val cleaned = text
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (cleaned.equals("null", ignoreCase = true)) {
            return "null"
        }

        if (cleaned.startsWith("{") && cleaned.endsWith("}")) {
            return cleaned
        }

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return cleaned.substring(start, end + 1)
    }
}
