[README.md](https://github.com/user-attachments/files/27143819/README.md)
# HotelGo

HotelGo là ứng dụng Android hỗ trợ đặt phòng và quản lý khách sạn. Ứng dụng được xây dựng cho môn CSC13009 - Mobile Application Development, nhóm 06 - ChưaBiếtTên.

Repository: https://github.com/jerrylemin/HotelGo_ChuaBietTen

## 1. Giới thiệu

HotelGo hướng tới hai nhóm người dùng chính:

- Client: khách hàng đặt phòng, xem phòng, thanh toán, dùng voucher, gửi đánh giá và báo cáo sự cố.
- Admin: quản trị khách sạn, quản lý phòng, xác nhận đặt phòng, xử lý check-in/check-out, quản lý voucher, add-on, poster và thông báo.

Mục tiêu của ứng dụng là giảm thao tác quản lý thủ công trong nghiệp vụ khách sạn, giúp theo dõi trạng thái phòng, đặt phòng, thanh toán và phản hồi khách hàng trên một hệ thống Android thống nhất.

## 2. Thành viên nhóm

| MSSV | Họ tên | Phần việc chính |
|---|---|---|
| 21127224 | Nguyễn Vũ Bách | Xác thực, phân quyền, hồ sơ người dùng, thông báo, tìm kiếm, lọc/sắp xếp, chi tiết phòng và ảnh |
| 21127645 | Lê Minh | Quản lý phòng, dịch vụ thêm, lịch sử đặt phòng, poster tìm phòng, poster giới thiệu |
| 20127119 | Phạm Nguyễn Gia Bảo | Đặt phòng, quản lý đặt phòng, thanh toán, voucher, đánh giá, báo cáo vấn đề |

## 3. Công nghệ sử dụng

- Ngôn ngữ: Kotlin.
- Nền tảng: Android Native.
- UI: XML Layout, Material Components, RecyclerView, ViewPager2, ConstraintLayout.
- Backend/Database: Supabase.
- Authentication: Supabase Auth, email/password, Google Sign-In qua Android Credential Manager.
- Image loading: Coil.
- Build system: Gradle Kotlin DSL.
- Android SDK:
  - minSdk: 28.
  - targetSdk: 36.
  - compileSdk: 36.
- Android Gradle Plugin: 9.0.0.

## 4. Kiến trúc tổng quan

Ứng dụng dùng mô hình Android Native nhiều Activity. Mỗi nghiệp vụ lớn có một Activity riêng. Màn hình chính dùng `FeatureRegistry` để hiển thị chức năng theo vai trò người dùng.

Luồng tổng quát:

1. Người dùng mở app.
2. App vào màn hình Splash.
3. Nếu chưa đăng nhập, app chuyển sang Auth.
4. Người dùng đăng nhập hoặc đăng ký.
5. App đọc hồ sơ người dùng từ Supabase.
6. App xác định vai trò `admin` hoặc `client`.
7. Màn hình chính hiển thị danh sách chức năng phù hợp.
8. Người dùng thao tác với các nghiệp vụ như tìm phòng, đặt phòng, thanh toán, voucher, báo cáo, đánh giá, check-in/check-out.

## 5. Cấu trúc thư mục chính

```text
HotelGo_ChuaBietTen/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/hotelapp_test2/
│   │   │   ├── MainActivity.kt
│   │   │   ├── HotelApp.kt
│   │   │   ├── core/
│   │   │   ├── data/
│   │   │   └── ui/
│   │   └── res/
│   └── build.gradle.kts
├── HotelList/
│   └── AmayaSaigonBoutiqueHotel/
├── gradle/
│   └── libs.versions.toml
├── supabase/
├── scripts/
└── README.md
```

## 6. Các module quan trọng

### 6.1. Authentication

File chính:

- `ui/auth/AuthActivity.kt`
- `ui/auth/LoginFragment.kt`
- `ui/auth/RegisterFragment.kt`
- `data/SupabaseRepository.kt`
- `data/SessionManager.kt`

Chức năng:

- Đăng nhập bằng email và mật khẩu.
- Đăng ký tài khoản client.
- Đăng nhập Google bằng Credential Manager.
- Lưu phiên đăng nhập.
- Đọc role từ bảng user profile.
- Điều hướng người dùng vào màn hình chính sau khi đăng nhập.

### 6.2. Dashboard và phân quyền

File chính:

- `MainActivity.kt`
- `core/FeatureRegistry.kt`
- `core/FeatureItem.kt`
- `core/FeatureRole.kt`

Chức năng:

- Hiển thị danh sách chức năng theo role.
- Admin thấy các chức năng quản trị.
- Client thấy các chức năng đặt phòng, thanh toán, đánh giá, báo cáo và lịch sử cá nhân.
- Nút Profile và Log out nằm trên màn hình chính.

### 6.3. Tìm kiếm khách sạn và phòng

File chính:

- `ui/features/HotelSearchActivity.kt`
- `ui/features/HotelDetailActivity.kt`
- `ui/features/HotelRoomDetailActivity.kt`
- `ui/features/RoomSearchActivity.kt`
- `ui/features/RoomDetailActivity.kt`

Chức năng:

- Tìm khách sạn theo tên, thành phố, khu vực.
- Xem danh sách phòng theo khách sạn.
- Tìm phòng theo khu vực, tên khách sạn, loại phòng hoặc mã phòng.
- Lọc phòng theo loại.
- Sắp xếp theo giá, đánh giá, sức chứa, thời gian cập nhật hoặc tên loại phòng.
- Xem chi tiết phòng, ảnh phòng, giá phòng và trạng thái phòng.

### 6.4. Quản lý phòng

File chính:

- `ui/features/RoomCrudActivity.kt`

Chức năng dành cho admin:

- Thêm phòng.
- Cập nhật thông tin phòng.
- Xóa phòng.
- Cập nhật trạng thái phòng.
- Nhập ảnh bằng URL hoặc chọn ảnh từ thiết bị.
- Upload ảnh lên Supabase Storage bucket `rooms`.

### 6.5. Đặt phòng và add-on

File chính:

- `ui/features/BookingActivity.kt`
- `ui/features/AddOnItemsActivity.kt`

Chức năng client:

- Nhập mã phòng.
- Chọn ngày nhận phòng và ngày trả phòng.
- Chọn số khách.
- Chọn add-on như snack hoặc đồ uống.
- Xem tổng tiền phòng, tổng tiền add-on và tổng tiền dự kiến.
- Gửi yêu cầu đặt phòng.

Chức năng admin:

- Tạo, sửa, xóa add-on.
- Bật hoặc tắt trạng thái bán add-on.
- Phân loại add-on thành snack hoặc drink.

## 7. Quản lý đặt phòng

File chính:

- `ui/features/BookingHistoryActivity.kt`
- `ui/features/BookingHistoryAdapter.kt`

Chức năng client:

- Xem lịch sử đặt phòng của bản thân.
- Hủy phòng trước ngày check-in theo điều kiện.
- Hệ thống ghi nhận hoàn tiền mô phỏng khi hủy hợp lệ.

Chức năng admin:

- Xem lịch sử đặt phòng của khách hàng.
- Xác nhận đặt phòng.
- Hủy đặt phòng.
- Gửi thông báo trạng thái đặt phòng cho client.

## 8. Thanh toán

File chính:

- `ui/features/PaymentActivity.kt`

Chức năng:

- Client chọn booking chưa thanh toán.
- Chọn voucher hợp lệ.
- Xem chi tiết:
  - Tiền phòng.
  - Tiền add-on.
  - Số tiền giảm.
  - Tổng tiền cuối.
- Chọn phương thức thanh toán:
  - Cash on check-in.
  - QR Banking transfer.
  - Visa / Debit card.
- Lưu thông tin thanh toán vào Supabase.
- Cập nhật trạng thái booking.
- Gửi thông báo thanh toán cho admin và client.

Ghi chú: thanh toán hiện là mô phỏng trong app. PayOS chưa được tích hợp thành công trong proposal.

## 9. Voucher

File chính:

- `ui/features/VoucherActivity.kt`

Chức năng client:

- Xem voucher hợp lệ.
- Kiểm tra mã voucher.
- Dùng voucher khi thanh toán.

Chức năng admin:

- Tạo voucher.
- Cập nhật voucher.
- Xóa voucher.
- Bật hoặc tắt voucher.
- Thiết lập loại giảm giá, giá trị giảm, điều kiện tối thiểu, hạn dùng và giới hạn lượt dùng.

## 10. Đánh giá và bình luận

File chính:

- `ui/features/ReviewActivity.kt`

Chức năng:

- Client xem danh sách booking đủ điều kiện đánh giá.
- Client chọn phòng đã đặt trước đó.
- Gửi đánh giá từ 1 đến 5 sao.
- Gửi bình luận cho phòng.
- App tránh đánh giá trùng cho cùng booking.
- Sau khi gửi, app cập nhật lại điểm đánh giá và số lượt đánh giá của phòng.

## 11. Báo cáo vấn đề

File chính:

- `ui/features/IssueReportActivity.kt`

Chức năng client:

- Chọn phòng đã đặt.
- Nhập loại vấn đề.
- Nhập mô tả chi tiết.
- Gửi báo cáo cho admin.

Chức năng admin:

- Xem danh sách báo cáo.
- Cập nhật trạng thái:
  - New.
  - Processing.
  - Resolved.
- Gửi thông báo phản hồi cho client.

## 12. Check-in và check-out

File chính:

- `ui/features/CheckInOutActivity.kt`
- `ui/features/AdminBookingAdapter.kt`

Chức năng dành cho admin:

- Xem danh sách booking liên quan đến lưu trú.
- Lọc theo trạng thái:
  - Chờ nhận phòng.
  - Đã check-in.
  - Đã check-out.
  - Quá hạn.
  - Đã hủy.
- Thực hiện check-in cho booking hợp lệ.
- Thực hiện check-out cho booking đang lưu trú.
- Khi check-out, app cập nhật lại trạng thái phòng thành `available`.

## 13. Thông báo trong app

File chính:

- `ui/features/NotificationsActivity.kt`
- `data/model/Models.kt`
- `data/SupabaseRepository.kt`

Chức năng:

- Hiển thị thông báo theo user hoặc theo role.
- Đánh dấu đã đọc hoặc chưa đọc.
- Đánh dấu tất cả là đã đọc.
- Cài đặt bật/tắt nhóm thông báo:
  - Booking.
  - Review.
  - Issue.
  - Payment.
  - Check-in.
  - Room status.
  - Promotion.

## 14. Poster

### 14.1. Poster giới thiệu phòng

File chính:

- `ui/features/RecommendationPosterActivity.kt`

Chức năng:

- Admin tạo poster giới thiệu phòng.
- Poster liên kết trực tiếp với phòng.
- Client xem poster đang active.
- Người dùng chạm vào poster để mở chi tiết phòng.

### 14.2. Poster tìm phòng

File chính:

- `ui/features/SearchPosterActivity.kt`

Chức năng client:

- Gửi yêu cầu tìm phòng theo khu vực, loại phòng, ngân sách, số khách, ngày và ghi chú.
- Theo dõi trạng thái phản hồi từ admin.

Chức năng admin:

- Xem yêu cầu tìm phòng của client.
- Cập nhật trạng thái yêu cầu.
- Gửi phản hồi cho client.

## 15. Dữ liệu khách sạn mẫu

Repo có thư mục `HotelList/AmayaSaigonBoutiqueHotel`. Thư mục này chứa dữ liệu seed cho khách sạn, phòng, giá, rating, số review, sức chứa và danh sách ảnh.

Ví dụ dữ liệu phòng gồm:

- Deluxe Room No Window.
- Signature Room Window City View.
- Standard Double or Twin Room.
- Premium Room Window City View.
- Superior Room Internal Window.
- Premium Room Window Alley View.
- Deluxe Room Internal Window.
- Signature Room Internal Window.
- Double or Twin Room.

Ứng dụng cũng cấu hình assets để đưa dữ liệu trong `HotelList` vào app thông qua Gradle.

## 16. Database và model dữ liệu

Các model chính nằm trong `data/model/Models.kt`.

Một số model quan trọng:

- `UserProfile`: hồ sơ người dùng.
- `Room`: phòng.
- `HotelCatalogItem`: khách sạn trong catalog.
- `HotelCatalogRoom`: phòng thuộc khách sạn trong catalog.
- `Booking`: đặt phòng.
- `BookingAddOn`: add-on gắn với booking.
- `Review`: đánh giá.
- `IssueReport`: báo cáo vấn đề.
- `Voucher`: mã giảm giá.
- `Poster`: poster giới thiệu.
- `RoomRequest`: yêu cầu tìm phòng.
- `AddOnItem`: mặt hàng phụ.
- `AppNotification`: thông báo trong app.
- `Payment`: thanh toán.
- `NotificationSettings`: cài đặt thông báo.

## 17. Cách chạy project

### 17.1. Yêu cầu môi trường

- Android Studio bản mới.
- JDK 17 hoặc bản phù hợp với Android Studio.
- Android SDK có API 36.
- Thiết bị ảo hoặc thiết bị thật chạy Android API 28 trở lên.
- Tài khoản Supabase và database đã tạo đủ bảng theo logic app.

### 17.2. Clone repository

```bash
git clone https://github.com/jerrylemin/HotelGo_ChuaBietTen.git
cd HotelGo_ChuaBietTen
```

### 17.3. Tạo file local.properties

Tạo file `local.properties` ở thư mục gốc project. Thêm các biến sau:

```properties
SUPABASE_URL=your_supabase_project_url
SUPABASE_ANON_KEY=your_supabase_anon_key
SUPABASE_AUTH_REDIRECT_URL=https://jerrylemin.github.io/HotelGo_ChuaBietTen/confirm.html
GOOGLE_WEB_CLIENT_ID=your_google_web_client_id
SUPABASE_NOTIFICATION_EMAIL_FUNCTION=send-notification-email
```

Trong đó:

- `SUPABASE_URL`: URL project Supabase.
- `SUPABASE_ANON_KEY`: anon key của Supabase.
- `SUPABASE_AUTH_REDIRECT_URL`: URL nhận redirect sau khi xác nhận email.
- `GOOGLE_WEB_CLIENT_ID`: client id dùng cho Google Sign-In.
- `SUPABASE_NOTIFICATION_EMAIL_FUNCTION`: tên Edge Function gửi email thông báo, nếu dùng.

### 17.4. Mở bằng Android Studio

1. Mở Android Studio.
2. Chọn `Open`.
3. Trỏ tới thư mục `HotelGo_ChuaBietTen`.
4. Đợi Gradle sync hoàn tất.
5. Chọn thiết bị chạy app.
6. Nhấn Run.

## 18. Trạng thái hoàn thành theo proposal

| Chức năng | Vai trò | Trạng thái |
|---|---|---|
| Đăng nhập / đăng ký | Admin, Client | Done |
| Tìm kiếm phòng | Admin, Client | Done |
| Lọc loại phòng và sắp xếp theo giá | Admin, Client | Done |
| Thêm / sửa / xóa phòng | Admin | Done |
| Đặt phòng | Client | Done |
| Đánh giá và bình luận | Client | Done |
| Báo cáo vấn đề | Client, Admin | Done |
| Add-on | Admin, Client | Client dùng được, admin từng có lỗi tạo mặt hàng theo proposal |
| Voucher | Admin, Client | Done |
| Thanh toán | Client | Mô phỏng, chưa tích hợp PayOS |
| Poster giới thiệu phòng | Admin, Client | Done |
| Poster tìm phòng | Client, Admin | Done |
| Profile người dùng | Admin, Client | Done |
| Lịch sử đặt phòng | Admin, Client | Done |
| Check-in và check-out | Admin | Done |
| Thông báo trong app | Admin, Client | Done |

## 19. Điểm mạnh của ứng dụng

- Có phân quyền admin và client.
- Nhiều nghiệp vụ khách sạn được gom trong một app Android.
- Dữ liệu lưu trữ qua Supabase thay vì hard-code toàn bộ.
- UI tách theo từng Activity nên dễ kiểm thử từng chức năng.
- Có ảnh phòng, catalog khách sạn và dữ liệu phòng mẫu.
- Có luồng đặt phòng, thanh toán mô phỏng, voucher, add-on và thông báo.
- Có hỗ trợ Google Sign-In.
- Có cơ chế fallback dữ liệu phòng từ catalog local nếu dữ liệu Supabase rỗng.

## 20. Hạn chế hiện tại

- Thanh toán PayOS chưa tích hợp thành công, app đang dùng mô phỏng.
- Một số luồng admin phụ thuộc cấu trúc bảng Supabase đúng với repository.
- Role admin cần được thiết lập từ database hoặc bằng thao tác quản trị.
- Dữ liệu khách sạn mẫu hiện tập trung vào một khách sạn trong thư mục `HotelList`.
- Một số thông báo là thông báo trong app, chưa phải push notification hoàn chỉnh qua Firebase Cloud Messaging.

## 21. Hướng phát triển tiếp theo

- Tích hợp PayOS hoặc cổng thanh toán thật.
- Bổ sung nhiều khách sạn và nhiều khu vực hơn.
- Hoàn thiện quản trị người dùng cho admin.
- Thêm dashboard thống kê doanh thu, số lượt đặt phòng, phòng trống và phòng đang ở.
- Tích hợp Firebase Cloud Messaging cho push notification.
- Tối ưu UI cho tablet và màn hình nhỏ.
- Viết test cho repository, auth flow và booking flow.
- Chuẩn hóa schema Supabase bằng migration script.

## 22. Kết luận

HotelGo là một ứng dụng đặt phòng và quản lý khách sạn trên Android. Ứng dụng đã triển khai hầu hết chức năng trong proposal, gồm xác thực, phân quyền, tìm kiếm phòng, quản lý phòng, đặt phòng, add-on, voucher, thanh toán mô phỏng, đánh giá, báo cáo vấn đề, poster, lịch sử đặt phòng, check-in/check-out và thông báo trong app. Dự án phù hợp để trình bày trong môn Phát triển phần mềm cho thiết bị di động vì thể hiện rõ luồng nghiệp vụ, kết nối backend, quản lý dữ liệu, giao diện người dùng và phân quyền.
