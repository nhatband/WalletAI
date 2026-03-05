package com.wallet.manager.ai

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import org.json.JSONObject

data class ParsedBill(
    val type: String,
    val title: String,
    val content: String,
    val amount: Double,
    val dateMillis: Long
)

class GeminiBillParser(
    apiKey: String
) {
    private val model = GenerativeModel(
        modelName = "gemini-3.1-flash-lite-preview",
        apiKey = apiKey
    )

    suspend fun parseBill(bitmap: Bitmap): ParsedBill? {
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

        val response = model.generateContent(
            content {
                text(prompt)
                image(bitmap)
            }
        )

        val text = response.text ?: return null
        val json = JSONObject(text)
        return ParsedBill(
            type = json.optString("type"),
            title = json.optString("title"),
            content = json.optString("content"),
            amount = json.optDouble("amount"),
            dateMillis = json.optLong("dateMillis")
        )
    }
}
