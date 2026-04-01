package com.wallet.manager.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiChatAssistant(
    private val apiKey: String
) {
    suspend fun chatWithContext(
        userMessage: String,
        financeSummary: String
    ): String = withContext(Dispatchers.IO) {
        val currentDate = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).format(Date())
        val systemInstruction = """
            Tên của bạn là "Ví Thông Minh AI". Bạn là trợ lý tài chính cá nhân thân thiện và chuyên nghiệp.

            Thông tin hiện tại:
            - Hôm nay là: $currentDate

            Dữ liệu chi tiêu của người dùng:
            $financeSummary

            Hướng dẫn:
            1. Luôn trả lời bằng tiếng Việt, xưng hô thân thiện.
            2. Nếu người dùng hỏi về tài chính hoặc nợ, hãy trả lời rõ ràng, dễ đọc, có cấu trúc.
            3. Nếu là danh sách số liệu, ưu tiên bullet points hoặc bảng ngắn.
            4. Nếu người dùng chỉ chào hỏi, trả lời ngắn gọn, tự nhiên.
        """.trimIndent()

        val requestBody = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", systemInstruction))
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", userMessage))
                    )
                )
            )

        val responseText = GeminiRestClient.generateContent(apiKey, requestBody)
        GeminiRestClient.extractText(responseText) ?: "Xin lỗi, hiện tại tôi chưa thể trả lời."
    }
}
