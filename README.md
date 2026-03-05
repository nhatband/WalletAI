# Ví Thông Minh AI (Wallet AI)

## 🌟 Giới thiệu tổng quan
**Ví Thông Minh AI** là một ứng dụng quản lý tài chính cá nhân hiện đại, tích hợp trí tuệ nhân tạo (Gemini AI) để giúp người dùng theo dõi thu chi một cách thông minh và tiện lợi nhất. Ứng dụng không chỉ dừng lại ở việc ghi chép thủ công mà còn có khả năng tự động phân tích hóa đơn qua hình ảnh và hỗ trợ giải đáp thắc mắc về tài chính thông qua Chatbot.

## ✨ Các tính năng chính
1.  **Quản lý chi tiêu:** Thêm, sửa, xóa các khoản chi tiêu hàng ngày với các danh mục phân loại rõ ràng (Ăn uống, Di chuyển, Mua sắm...).
2.  **Phân tích hóa đơn bằng AI:** Sử dụng camera hoặc chọn ảnh từ thư viện, AI sẽ tự động trích xuất thông tin như tiêu đề, số tiền, ngày tháng và loại chi tiêu từ hóa đơn.
3.  **Chia sẻ chi phí (Split Bill):** Tính năng chia tiền với bạn bè mạnh mẽ. Hỗ trợ chia theo số suất, chọn người trả tiền và theo dõi trạng thái thanh toán nợ.
4.  **Thống kê chuyên sâu:** Biểu đồ tròn (tỉ lệ theo loại) và biểu đồ cột (chi tiêu hàng ngày) giúp bạn cái nhìn tổng quan về tình hình tài chính. Hỗ trợ lọc theo Ngày, Tuần, Tháng, Năm hoặc Khoảng thời gian tùy chỉnh.
5.  **Quản lý nợ:** Theo dõi chi tiết ai đang nợ bạn và bạn đang nợ ai từ các khoản chi tiêu chung.
6.  **Chatbot Trợ lý Tài chính:** Hỏi đáp trực tiếp với AI về thói quen chi tiêu của bạn, nhờ AI tóm tắt tình hình nợ nần hoặc tư vấn tiết kiệm.
7.  **Bảo mật:** Bảo vệ dữ liệu bằng mã PIN (Passcode) 6 số và xác thực sinh trắc học (Vân tay/Khuôn mặt).
8.  **Đa ngôn ngữ:** Hỗ trợ hoàn toàn tiếng Việt và tiếng Anh.

## 🔄 Luồng hoạt động
1.  **Nhập liệu:** Người dùng nhập thủ công hoặc chụp ảnh hóa đơn -> AI xử lý dữ liệu -> Người dùng xác nhận và lưu.
2.  **Chia tiền:** Nếu chi tiêu chung, người dùng chọn bạn bè tham gia -> Thiết lập số suất -> AI tính toán số tiền mỗi người phải chịu.
3.  **Lưu trữ:** Dữ liệu được lưu trữ cục bộ (Local Database - Room) để đảm bảo tốc độ và quyền riêng tư. API Key được lưu an toàn trong EncryptedSharedPreferences.
4.  **Phân tích & Chat:** Khi người dùng mở màn hình Thống kê hoặc Chat, ứng dụng sẽ lấy dữ liệu từ DB, tính toán các chỉ số nợ/chi tiêu và gửi ngữ cảnh cho Gemini AI để trả lời các câu hỏi phức tạp.

## 🖼️ Ảnh minh hoạ
*(Lưu ý: Thay thế các đường dẫn dưới đây bằng ảnh thực tế của ứng dụng)*
- **Màn hình chính:** `[Link ảnh hoặc path: docs/screenshots/home.png]`
- **Thống kê:** `[Link ảnh hoặc path: docs/screenshots/stats.png]`
- **Chat với AI:** `[Link ảnh hoặc path: docs/screenshots/chat.png]`
- **Phân tích Bill:** `[Link ảnh hoặc path: docs/screenshots/ai_scan.png]`

## 🛠️ Cách triển khai, cập nhật và sửa lỗi

### Cách triển khai (Setup)
1.  **Yêu cầu:** Android Studio Koala+, JDK 17, Android SDK 34+.
2.  **Clone dự án:** `git clone <repository_url>`
3.  **Cấu hình API Key:**
    - Truy cập [Google AI Studio](https://aistudio.google.com/) để lấy Gemini API Key.
    - Mở ứng dụng, vào phần **Cài đặt** và dán API Key vào.
4.  **Build:** Nhấn `Sync Project with Gradle Files` và chạy ứng dụng trên máy ảo hoặc thiết bị thật.

### Cập nhật (Update)
- Để cập nhật phiên bản mới nhất, hãy thực hiện `git pull origin main`.
- Nếu có sự thay đổi về cấu trúc Database, Room sẽ tự động thực hiện Migration (đã được cấu hình trong `AppDatabase`).

### Sửa lỗi (Troubleshooting)
- **Lỗi "Cause: zip END header not found":** Do file Gradle tải về bị lỗi. Xóa thư mục `.gradle` trong `User` và Sync lại.
- **AI không phản hồi:** Kiểm tra lại API Key trong phần Cài đặt và đảm bảo thiết bị có kết nối Internet.
- **Lỗi crash khi chọn ngày:** Đã được khắc phục bằng cách sử dụng `Material3 DateRangePicker` bản địa của Compose thay vì `DatePickerDialog` cũ.
- **Ngôn ngữ không chuyển hết:** Đảm bảo tất cả các chuỗi text đều sử dụng `stringResource(R.string...)` thay vì viết cứng. Kiểm tra cả 2 file `strings.xml` (vi) và `values-en/strings.xml` (en).
