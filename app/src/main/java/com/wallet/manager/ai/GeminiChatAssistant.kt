package com.wallet.manager.ai

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiChatAssistant(
    apiKey: String
) {
    private val _apiKey = apiKey

    suspend fun chatWithContext(
        userMessage: String,
        financeSummary: String
    ): String {
        val currentDate = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN")).format(Date())
        val systemInstruction = """
            Tên của bạn là "Ví Thông Minh AI". Bạn là trợ lý tài chính cá nhân thân thiện và chuyên nghiệp.
            
            Thông tin hiện tại:
            - Hôm nay là: $currentDate
            
            Dữ liệu chi tiêu của người dùng:
            $financeSummary

            Hướng dẫn:
            1. Luôn trả lời bằng tiếng Việt, xưng hô thân thiện (ví dụ: "mình", "bạn").
            2. Nếu người dùng hỏi về ngày tháng, hãy sử dụng thông tin "Hôm nay là" ở trên.
            3. Nếu người dùng hỏi các câu hỏi thông thường, hãy trả lời một cách tự nhiên nhưng vẫn giữ phong cách của một trợ lý tài chính.
            4. Khi trả lời về tài chính và các khoản nợ:
               - Hãy tóm tắt một cách rõ ràng, dễ đọc (sử dụng dấu gạch đầu dòng, in đậm các con số).
               - Phân biệt rõ ràng giữa "Tiền bạn nợ" và "Tiền bạn cho vay".
               - Luôn nhắc lại tổng số tiền để người dùng dễ nắm bắt.
               - Nếu là danh sách chi tiêu, hãy sắp xếp theo thời gian hoặc loại chi tiêu.
            5. Định dạng Markdown: Sử dụng bảng hoặc danh sách để trình bày số liệu phức tạp.
        """.trimIndent()

        val model = GenerativeModel(
            modelName = "gemini-3.1-flash-lite-preview",
            apiKey = _apiKey,
            systemInstruction = content { text(systemInstruction) }
        )

        val response = model.generateContent(
            content { text(userMessage) }
        )

        return response.text ?: "Xin lỗi, hiện tại tôi không trả lời được."
    }
}
