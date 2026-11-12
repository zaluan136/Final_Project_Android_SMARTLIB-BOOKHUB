# BookHub / SmartLib — Java + XML

Ứng dụng Android quản lý đặt lịch mượn và trả sách. Phát triển từ project Android Studio trên nhánh `main`; giữ package `com.example.smartlibandbookhub` và toolchain của dự án.

## Trạng thái thực tế

Đây là **bản demo có nghiệp vụ hoạt động và phần tích hợp Firebase ban đầu**, chưa hoàn thành toàn bộ phạm vi PDF và chưa phải sản phẩm sẵn sàng triển khai cho thư viện thật.

| Chức năng | Ngoại tuyến | Firebase |
|---|---|---|
| Danh mục, tìm tên/tác giả/ISBN không dấu, lọc thể loại | Có | Đọc tối đa 100 đầu sách, chưa tìm kiếm/phân trang |
| Đặt ngày nhận trong 7 ngày, chặn trùng, giới hạn phiếu | Có | API kiểm tra 7 ngày; giao diện hiện đặt nhận hôm nay |
| Hàng chờ FIFO và tự chuyển khi có sách | Có | Chưa triển khai; từ chối khi hết bản |
| Thủ thư bàn giao, nhận trả; độc giả gia hạn/hủy | Có | Đã viết client + callable functions; chưa xác nhận trên project thật |
| Phí quá hạn, snapshot ngày trả/đơn giá | Có | Đã viết kiểm tra phía máy chủ; phí ghi nhận, không phải đã thu |
| Thêm/sửa/ẩn sách, quy định mượn | Có | Khởi tạo từ công cụ quản trị; chưa có CRUD online trong ứng dụng |
| Đăng ký, xác minh email, đăng nhập, quên mật khẩu | Không; chỉ đổi vai trò demo | Đã tích hợp SDK, cần bật Auth và cấu hình project |
| Phân quyền và giao dịch tồn kho trên server | Không có bảo vệ đa người dùng | Có mã Functions + Rules; chưa kiểm thử tích hợp Firestore |
| Quét camera QR/barcode, RFID, push notifications | Chưa có | Chưa có |
| Admin Web, quản lý tài khoản, báo cáo nâng cao | Chưa có | Chưa có |

Giao diện tiếng Việt, chế độ sáng/tối theo thiết bị, màn hình rỗng, kiểm tra biểu mẫu và xác nhận thao tác. Bìa sách và vị trí thư viện là minh họa.

## Mở bằng Android Studio

1. Mở thư mục chứa `settings.gradle.kts`, không mở thư mục gitlink lồng bên trong.
2. Dùng Android Studio hỗ trợ AGP **9.3.3**, Gradle **9.5.0**, Gradle JDK **25**, SDK **37.0**. `minSdk = 24`; Java source/target 11 theo project gốc.
3. Sync Gradle rồi Run module `app`. Gradle dùng Kotlin DSL nhưng ứng dụng viết bằng Java + XML.
4. `app/google-services.json` là file cấu hình Firebase cục bộ, được Git ignore. Nếu chưa có file, demo vẫn build/chạy; màn hình Firebase sẽ hướng dẫn cấu hình.
5. Với Firebase, xem [hướng dẫn đầy đủ](docs/FIREBASE.md). Không đưa service-account key vào APK/repository.

## Chạy demo

- **Khám phá** → chọn sách → đặt ngày nhận hôm nay → **Phiếu mượn**.
- Chuyển vai trò demo **Thủ thư** → **Quản lý** → xác nhận bàn giao.
- Chuyển **Độc giả** → gia hạn; đổi về **Thủ thư** để nhận trả.
- **Quản trị** → **Thư viện** → điều chỉnh quy định.
- Sửa/ẩn sách bằng cách mở chi tiết sách khi đang ở vai trò thủ thư/quản trị.
- **Thư viện → Kết nối thư viện Firebase** mở chế độ online độc lập. Không sao chép dữ liệu demo vào Firestore.

Ngày demo dựa trên lịch thiết bị; ngày Firebase tính trên máy chủ theo `Asia/Ho_Chi_Minh` (UTC+7). Phiếu online hết hạn được xử lý theo lịch 30 phút, mỗi lượt tối đa 200 phiếu. Gia hạn online hiện chưa có kiểm tra hàng chờ vì tính năng hàng chờ online chưa triển khai.

## Kiểm tra

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
# Cần thiết bị hoặc emulator đang chạy; bộ test lưu trữ/giao diện dùng API 26+.
./gradlew :app:connectedDebugAndroidTest
cd firebase/functions
npm ci
npm test
npm run check
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Báo cáo Gradle nằm trong `app/build/reports/`.
Các test Java kiểm tra nghiệp vụ độc lập; test Android kiểm tra khởi động/điều hướng/lưu dữ liệu; test Node kiểm tra policy Firebase. Test Node không thay thế kiểm thử Firestore Rules, giao dịch đồng thời và App Check trên emulator/staging.

## Cấu trúc

- `domain/Library.java`: quy tắc demo, không phụ thuộc Android.
- `data/LibraryStore.java`: JSON có phiên bản, ghi nguyên tử bằng `AtomicFile`; lỗi đọc không tự xóa dữ liệu.
- `MainActivity.java` + `res/layout/`: giao diện demo; I/O ở executor riêng.
- `FirebaseActivity.java`: đăng nhập và các thao tác trực tuyến; không có bộ chọn vai trò.
- `firebase/functions/`: kiểm tra quyền, giao dịch nhiều tài liệu, khóa chống đặt trùng và receipt chống lặp yêu cầu.
- `firebase/firestore.rules`: client chỉ đọc theo quyền, mọi ghi bị từ chối; Functions dùng Admin SDK.

## Giới hạn an toàn và bước tiếp theo

Demo lưu dữ liệu cục bộ không mã hóa bổ sung, tắt Android backup; không dùng tài khoản hoặc dữ liệu cá nhân thật. Chọn vai trò demo không phải xác thực. Các trường công khai của mô hình demo chỉ phục vụ nội bộ, không phải ranh giới bảo mật.

Firebase cần kiểm thử tích hợp trước khi sử dụng thật, đặc biệt tranh chấp bản sách cuối cùng, phân quyền giữa hai độc giả, retry sau mất mạng và giới hạn truy vấn. Tiếp theo là hàng chờ online, CRUD online, phân trang, thông báo, QR, kiểm thử API 24/37 và màn hình lớn, accessibility, quản lý tài khoản và Admin Web. Không có cam kết “không có lỗ hổng” khi các kiểm tra đó chưa hoàn thành.
