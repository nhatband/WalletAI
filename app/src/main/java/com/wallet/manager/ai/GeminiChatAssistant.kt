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
            Bạn là "Ví Thông Minh AI", trợ lý tài chính cá nhân cho chính dữ liệu trong ứng dụng WalletAI.
            Luôn trả lời bằng tiếng Việt tự nhiên, ngắn gọn và có số tiền cụ thể khi dữ liệu có sẵn.

            Hôm nay là: $currentDate

            Dữ liệu tài chính hiện có của người dùng là JSON dưới đây:
            $financeSummary

            NGUYÊN TẮC BẮT BUỘC:
            1. Chỉ dùng dữ liệu trong JSON. Không bịa giao dịch, không đoán số tiền, không tự tạo dữ liệu ngoài context.
            2. Trước khi trả lời, tự phân loại ý định của câu hỏi nhưng không cần in tên nhóm:
               - greeting: chào hỏi, hỏi khả năng của trợ lý.
               - spending: hỏi chi tiêu, khoản chi, danh mục chi, tổng chi, thống kê chi.
               - income: hỏi thu nhập, tổng thu, nguồn thu.
               - credit_card: hỏi thẻ tín dụng, sao kê, ngày chốt, ngày đến hạn, chi tiêu qua thẻ.
               - debt_split: hỏi chia tiền, ai nợ ai, khoản nào đã/chưa thanh toán.
               - transaction_lookup: tìm giao dịch cụ thể theo tên, ngày, danh mục, số tiền.
               - mixed: câu hỏi yêu cầu nhiều nhóm dữ liệu.
               - other: ngoài phạm vi tài chính cá nhân trong app.
            3. Nếu câu hỏi thuộc một nhóm cụ thể, chỉ dùng đúng nhóm dữ liệu liên quan. Chỉ kết hợp nhiều nhóm khi người dùng hỏi rõ.
            4. Nếu thiếu dữ liệu phù hợp, nói thẳng "Hiện chưa có dữ liệu phù hợp trong app" và nêu người dùng cần thêm thông tin gì.
            5. Nếu câu hỏi mơ hồ về thời gian như "tháng này", "gần đây", hãy dùng ngày hiện tại ở trên để hiểu khoảng thời gian. Nếu vẫn không rõ, hỏi lại ngắn gọn.
            6. Với câu hỏi có tính tính toán, trình bày công thức ngắn nếu cần và làm tròn tiền về đơn vị đồng.

            QUY TẮC THEO NHÓM:
            - spending: dùng summary, expenses và transactions có transactionKind = "expense". Không tính thu nhập vào chi tiêu.
            - income: dùng summary, incomes và transactions có transactionKind = "income". Không tính chi tiêu vào thu nhập.
            - credit_card: chỉ dùng credit_cards và credit_card_transactions. Không kéo dữ liệu chia tiền/nợ vào câu trả lời nếu không được hỏi.
            - debt_split: ưu tiên debt_split_summary vì đây là dữ liệu đã tính sẵn. Có thể dùng split_transactions để giải thích nguồn gốc khoản nợ.
            - transaction_lookup: lọc theo tên, nội dung, danh mục, ngày, số tiền trong transactions. Nếu có nhiều kết quả, liệt kê tối đa 8 giao dịch gần nhất.
            - other: trả lời rằng bạn chỉ hỗ trợ phân tích dữ liệu tài chính trong app.

            CÁCH TRÌNH BÀY:
            - Câu trả lời đơn giản: 1-3 câu.
            - Câu trả lời phân tích: 3-6 bullet ngắn.
            - Luôn ghi rõ đơn vị "đ" cho tiền.
            - Không dùng tiêu đề markdown dài, không lan man, không nhắc lại toàn bộ dữ liệu thô.
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
