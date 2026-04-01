# Ví Thông Minh

<p align="center">
  <img src="app/src/main/res/drawable/image_0.jpg" alt="Ví Thông Minh" width="320" />
</p>

<p align="center">
  Ứng dụng Android quản lý chi tiêu cá nhân, chia tiền, theo dõi thẻ tín dụng và hỗ trợ AI.
</p>

## Giới thiệu

**Ví Thông Minh** là ứng dụng Android được xây dựng bằng Kotlin và Jetpack Compose, tập trung vào trải nghiệm quản lý tài chính cá nhân hiện đại và đồng bộ theo tài khoản người dùng.

Phiên bản hiện tại của dự án đang đi theo hướng:

- **Supabase là nguồn dữ liệu chính**
- **Room chỉ còn là lớp cache tạm cho UI**
- Khi mở app:
  - nếu không có session hợp lệ, cache local sẽ bị xóa
  - nếu có session hợp lệ, cache local sẽ bị xóa và nạp lại từ cloud

Mục tiêu của kiến trúc này là tránh việc dữ liệu cũ từ local hiển thị sai khi người dùng đổi tài khoản, đăng xuất hoặc cài lại ứng dụng.

## Ảnh minh họa

![Ảnh minh họa ứng dụng](app/src/main/res/drawable/image_0.jpg)

## Tính năng chính

- Quản lý thu chi: thêm, sửa, xóa giao dịch theo nhóm như ăn uống, di chuyển, mua sắm.
- Chia tiền với bạn bè: gắn bạn bè vào giao dịch, chia theo số suất, theo dõi trạng thái thanh toán.
- Quản lý nợ: tổng hợp ai đang nợ bạn và bạn đang nợ ai.
- Thẻ tín dụng: khai báo thẻ, gắn giao dịch với thẻ, theo dõi số tiền đến kỳ cần thanh toán.
- Thống kê chi tiêu: xem tổng quan theo ngày, tuần, tháng, năm.
- Chat AI: hỏi đáp về chi tiêu, nợ/chia tiền và giao dịch thẻ tín dụng.
- Quét hóa đơn bằng AI: nhận diện thông tin hóa đơn từ ảnh để điền nhanh giao dịch.
- Bảo mật: hỗ trợ PIN 6 số và sinh trắc học.
- Đa ngôn ngữ: hỗ trợ tiếng Việt và tiếng Anh.

## Công nghệ sử dụng

- Kotlin
- Jetpack Compose
- Material 3
- Room
- DataStore
- EncryptedSharedPreferences
- Supabase Auth, PostgREST, Realtime
- Ktor
- CameraX

## Kiến trúc dự án

Luồng chính của app:

`UI -> ViewModel -> Repository -> Supabase / Room`

Ý nghĩa thực tế trong phiên bản hiện tại:

- UI đọc dữ liệu từ `Flow`
- ViewModel điều phối trạng thái màn hình
- Repository kết nối giữa UI và tầng dữ liệu
- Supabase là nguồn dữ liệu chính để đồng bộ theo tài khoản
- Room được giữ lại để làm cache tạm, chưa bị loại bỏ hoàn toàn khỏi codebase

## Các màn hình chính

- `Home`: danh sách giao dịch, tìm kiếm, lọc, thêm chi tiêu
- `Stats`: thống kê và tổng hợp chi tiêu
- `Friends`: danh sách bạn bè và các khoản chia tiền
- `Credit Cards`: quản lý thẻ tín dụng và giao dịch theo thẻ
- `Chat`: chatbot AI về tài chính cá nhân
- `Settings`: giao diện, ngôn ngữ, bảo mật, cloud sync, tài khoản
- `Auth`: đăng nhập và đăng ký

## Cấu hình môi trường

Yêu cầu:

- Android Studio bản mới
- JDK 11 trở lên
- Android SDK 24 trở lên

Thông số hiện tại của dự án:

- `compileSdk = 36`
- `minSdk = 24`
- `targetSdk = 36`
- Kotlin `2.1.0`
- AGP `8.9.1`

## Chạy dự án

1. Clone repository.
2. Mở project bằng Android Studio.
3. Sync Gradle.
4. Build và chạy app trên emulator hoặc thiết bị thật.

Lệnh build debug:

```powershell
.\gradlew.bat :app:assembleDebug
```

## Supabase

Ứng dụng đang dùng Supabase cho các chức năng:

- Đăng nhập / đăng ký
- Đồng bộ dữ liệu người dùng
- Khôi phục dữ liệu khi đăng nhập lại hoặc cài lại app

Các file liên quan:

- [SupabaseConfig.kt](/C:/Users/nhatb/Downloads/LamViec/WalletAI/app/src/main/java/com/wallet/manager/data/remote/supabase/SupabaseConfig.kt)
- [SupabaseService.kt](/C:/Users/nhatb/Downloads/LamViec/WalletAI/app/src/main/java/com/wallet/manager/data/remote/supabase/SupabaseService.kt)
- [SupabaseRestoreManager.kt](/C:/Users/nhatb/Downloads/LamViec/WalletAI/app/src/main/java/com/wallet/manager/data/remote/supabase/SupabaseRestoreManager.kt)
- [supabase_rls_policies.sql](/C:/Users/nhatb/Downloads/LamViec/WalletAI/supabase_rls_policies.sql)

Lưu ý quan trọng:

- Cần cấu hình RLS đúng trên Supabase.
- Dữ liệu phải gắn với `user_id`.
- Policy cần kiểm tra theo `auth.uid()`.
- Nếu policy cấu hình sai, người dùng có thể nhìn thấy dữ liệu của tài khoản khác.

## AI

AI hiện được dùng cho hai nhóm tính năng chính:

- Quét hóa đơn từ ảnh
- Chatbot tài chính

API key Gemini được nhập trong màn hình `Settings` và lưu an toàn bằng `EncryptedSharedPreferences`.

## Cloud Sync

Hành vi hiện tại:

- Đăng nhập thành công: xóa cache local và nạp lại từ cloud
- Đăng xuất: xóa cache local
- Mở app khi có session: làm mới cache từ cloud
- Mở app khi không có session: xóa cache local

Thiết kế này giúp local DB không còn đóng vai trò nguồn dữ liệu chính.

## Cấu trúc thư mục

```text
app/
  src/main/java/com/wallet/manager/
    ai/
    data/
      local/
      mapper/
      prefs/
      remote/
      repository/
      secure/
    ui/
    viewmodel/
```

## Lưu ý khi phát triển

- UI được viết bằng Compose.
- Một số màn hình vẫn đang đọc dữ liệu qua `Flow` của Room.
- Nếu muốn bỏ hoàn toàn local DB, cần viết lại repository và ViewModel theo hướng gọi Supabase trực tiếp.

## Trạng thái hiện tại

Dự án đang trong giai đoạn chuyển từ mô hình **local-first** sang **cloud-first**.

Đã hoàn thành:

- Auth bằng Supabase
- Sync `expenses`, `friends`, `expense_friend_cross_ref`, `credit_cards`
- Restore dữ liệu từ cloud
- Màn hình đăng nhập / đăng ký
- Quản lý thẻ tín dụng

Đang cần tiếp tục:

- Hoàn thiện dữ liệu `user_id` cho toàn bộ dữ liệu cũ trên Supabase
- Giảm phụ thuộc vào Room trong repository
- Bổ sung test cho auth, sync và restore
