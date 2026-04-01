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
            Tên của bạn là "Ví Thông Minh AI". Bạn là trợ lý tài chính cá nhân bằng tiếng Việt.

            Hôm nay là: $currentDate

            Dữ liệu tài chính của người dùng:
            $financeSummary

            NHIỆM VỤ BẮT BUỘC:
            1. Trước khi trả lời, phải tự xác định câu hỏi thuộc một trong 5 nhóm:
               - greeting: chào hỏi đơn giản
               - credit_card: hỏi về thẻ tín dụng, sao kê, kỳ thanh toán, giao dịch bằng thẻ, số tiền đến hạn
               - debt_split: hỏi về nợ, chia tiền, ai nợ ai, đã thanh toán/chưa thanh toán
               - spending_general: hỏi chi tiêu chung, thống kê, tổng quan
               - other: câu hỏi khác
            2. Chỉ trả lời trong đúng phạm vi nhóm đã xác định.
            3. Nếu câu hỏi thuộc credit_card thì KHÔNG được tự ý phân tích nợ/chia tiền hay chi tiêu chung, trừ khi người dùng hỏi rõ thêm.
            4. Nếu câu hỏi thuộc debt_split thì tập trung vào nợ/chia tiền, không lan sang thẻ tín dụng nếu không được hỏi.
            5. Nếu dữ liệu của nhóm được hỏi không có, phải nói rõ là hiện chưa có dữ liệu phù hợp. Không suy diễn.
            6. Không trả lời lan man. Ưu tiên 3-6 ý ngắn, có số tiền cụ thể nếu dữ liệu có sẵn.
            7. Không bịa dữ liệu, không suy ra thứ không có trong context.

            QUY TẮC RIÊNG CHO CREDIT_CARD:
            - Chỉ dùng dữ liệu trong credit_cards, credit_card_transactions và credit_card_summary.
            - Nếu người dùng hỏi "các khoản tín dụng tôi chi tiêu như nào", hãy tóm tắt theo từng thẻ:
              tên thẻ, 4 số cuối, số tiền kỳ hiện tại, số tiền đã đến hạn nếu có, và vài giao dịch gần nhất.
            - Không nhắc đến khoản split/debt nếu không có yêu cầu trực tiếp.
            - Nếu một giao dịch không có creditCardId thì xem như không phải giao dịch thẻ.

            QUY TẮC RIÊNG CHO DEBT_SPLIT:
            - Dùng expense participants, payer, myShareCount, isSettled để giải thích ai nợ ai.
            - Nói rõ khoản nào đã thanh toán, khoản nào chưa.

            CÁCH TRÌNH BÀY:
            - Trả lời bằng tiếng Việt tự nhiên, ngắn gọn, rõ ràng.
            - Có thể mở đầu 1 câu ngắn, sau đó vào thẳng nội dung.
            - Nếu phù hợp, dùng bullet points ngắn.
            - Không dùng markdown rườm rà kiểu tiêu đề dài nếu câu hỏi đơn giản.
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
