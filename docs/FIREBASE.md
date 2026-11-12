# Kết nối Firebase cho BookHub

Project cấu hình đã nhận: `smartlib-bookhub`; Android package: `com.example.smartlibandbookhub`. File `app/google-services.json` được dùng cục bộ và không đưa vào Git. File này là cấu hình client, **không cấp quyền triển khai** Cloud Functions hay chỉnh Firestore Rules.

## Thiết lập trên Firebase Console

1. Authentication → Sign-in method → bật **Email/Password**. Bật chống dò email (email enumeration protection), đặt chính sách mật khẩu tối thiểu 12 ký tự phía Firebase. Kiểm tra mẫu email xác minh/đặt lại mật khẩu và ngôn ngữ phù hợp.
2. Tạo Cloud Firestore ở **production mode**. Chọn vùng phù hợp (ưu tiên Singapore khi phù hợp dữ liệu của bạn); vị trí database không đổi dễ dàng sau khi tạo.
3. Cloud Functions Gen 2 và lịch Cloud Scheduler cần project bật thanh toán **Blaze**. Xem giá và thiết lập cảnh báo ngân sách trước khi triển khai. Chưa có thao tác bật thanh toán hoặc triển khai từ phiên làm việc này.
4. App Check → đăng ký Android app. Bản release dùng **Play Integrity** với thông tin chứng thư SHA-256 và cấu hình phân phối phù hợp. Bản debug dùng Debug provider: đăng ký debug token hiển thị trong Logcat của máy phát triển vào Console; không gửi token lên Git hoặc chat.
5. Callable function luôn yêu cầu App Check. Có thể bật enforcement Firestore sau khi kiểm tra app đã lấy token thành công. Không tắt Rules hoặc mở `allow read, write: if true` để xử lý lỗi.

## Triển khai từ máy của chủ project

Cần Node.js 22, Firebase CLI và quyền triển khai trên project. Dùng đăng nhập tương tác trên máy của bạn, không gửi access token/service-account key cho người khác.

```sh
npm install -g firebase-tools
firebase login
cd firebase
firebase use smartlib-bookhub
cd functions
npm ci
npm test
npm run check
cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions --project smartlib-bookhub
```

Region Functions là `asia-southeast1`, khớp Android client. Chờ indexes hoàn thành. Rules chặn toàn bộ ghi trực tiếp từ app; không cho phép client tự cấp role, sửa tồn kho, ngày đến hạn hoặc phí.

## Dữ liệu đầu và quyền thủ thư

Công cụ `firebase/functions/tools/admin.js` chỉ chạy trên máy quản trị tin cậy, dùng Application Default Credentials (ADC). Ví dụ khi có Google Cloud CLI:

```sh
gcloud auth application-default login
gcloud auth application-default set-quota-project smartlib-bookhub
# Linux/macOS; PowerShell: $env:GOOGLE_CLOUD_PROJECT="smartlib-bookhub"
export GOOGLE_CLOUD_PROJECT=smartlib-bookhub
cd firebase/functions
node tools/admin.js seed
node tools/admin.js role UID_CUA_THU_THU librarian
```

`seed` dùng batch create, thất bại nếu dữ liệu đã tồn tại; không ghi đè danh mục hoặc số bản đang mượn. Đăng ký tài khoản qua ứng dụng và xác minh email trước, lấy UID từ Authentication trên Console. Role mặc định là `reader`; role staff hợp lệ là `librarian` hoặc `admin`. Role do custom claims cấp, không đọc từ trường do người dùng tự ghi. Người dùng cần đăng nhập lại sau đổi role; token đã phát hành có thể còn hiệu lực đến khi hết hạn, nên chưa coi đây là cơ chế thu hồi quyền tức thời.

## Kiểm thử bắt buộc trước khi dùng thật

- Hai độc giả tranh bản sách cuối cùng: chỉ một giao dịch thành công; tồn kho không âm.
- Hai thiết bị cùng một độc giả đặt cùng sách: chỉ một phiếu active.
- Độc giả không đọc phiếu người khác, không tự sửa role/stock/dueDay bằng SDK hay REST.
- Token chưa xác minh email và App Check thiếu/không hợp lệ phải bị từ chối.
- Mất mạng sau khi gửi thao tác: nút “Thử lại” dùng cùng request ID, không bàn giao/trả/giữ sách lần hai.
- Thử cấp/hạ quyền, mượn quá hạn, phí sát ranh giới ngày UTC+7 và job hết hạn.
- Không gọi staging là production trước khi test Rules và transaction bằng Firestore emulator hoặc project thử nghiệm.

Các test Node hiện kiểm tra policy thuần, **chưa kiểm tra các giao dịch hoặc Rules trên Firestore**. Chưa xác nhận backend đã triển khai hay Auth/App Check đã bật trên Console. Không dùng dữ liệu cá nhân thật trước khi hoàn thành các bước này.

## Phạm vi online hiện tại

Đăng nhập/đăng ký/xác minh/khôi phục mật khẩu, đọc danh mục/phiếu, đặt nhận hôm nay, bàn giao, hủy, gia hạn, trả sách. Callable hỗ trợ ngày hẹn trong 7 ngày, giao diện chưa có bộ chọn ngày online. Hàng chờ, CRUD online, thông báo đẩy, quản trị tài khoản trong UI và phân trang chưa triển khai. Cấu hình, dữ liệu demo và tài khoản online độc lập nhau.
