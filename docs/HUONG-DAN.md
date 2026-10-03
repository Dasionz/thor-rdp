# Thor RDP 0.1.0 — bản thử nghiệm cho AYN Thor

Đây là APK Android độc lập dùng giao thức RDP, được phát triển từ aRDP/FreeRDP. Ứng dụng có bàn phím riêng cho màn dưới và joystick điều khiển chuột. Đây không phải Windows App do Microsoft phát hành và không dùng tài khoản hay dữ liệu đã lưu trong Windows App.

## Cài và kết nối

1. Chép `thor-rdp-0.1.0-arm64.apk` sang Thor và cài. Nếu Android hỏi, cho phép trình quản lý tệp mở APK bạn vừa chép.
2. Mở **Thor RDP** trên màn trên. Dùng nút thêm kết nối, chọn thiết lập thủ công.
3. Nhập địa chỉ máy Windows và cổng RDP của bạn (mặc định 3389), tên đăng nhập, mật khẩu và domain nếu có. Dùng thông tin kết nối bạn đang dùng với Windows App. Windows Hello PIN thường không phải mật khẩu đăng nhập RDP.
4. Kết nối. Nếu máy dùng chứng chỉ tự ký, kiểm tra thông tin chứng chỉ trong hộp thoại trước khi chấp nhận. Không có cơ chế tự bỏ qua cảnh báo chứng chỉ trong phần thay đổi của bản Thor.
5. Khi desktop hiện ra ở màn trên, bàn phím sẽ xuất hiện trên màn dưới. App tự tìm màn thứ hai, không gắn cứng vào số thứ tự 1.

Máy Windows phải hỗ trợ và đã bật nhận kết nối RDP; Windows Home không có RDP host tích hợp. Máy ở nhà cần đang bật, có mạng và không ngủ. Nếu bạn kết nối từ bên ngoài, giữ đường VPN/kết nối mạng hiện dùng với Windows App. APK này không tự cấu hình máy Windows, router hay VPN.

## Điều khiển

| Điều khiển | Chức năng |
|---|---|
| Joystick trái | Di chuyển chuột; đẩy nhẹ để đi chậm |
| Joystick phải lên/xuống | Cuộn trang |
| A | Nhấn/giữ chuột phải |
| B | Nhấn/giữ chuột trái; giữ B và di chuyển joystick để kéo |
| Nút `A:right · B:left` trên màn dưới | Đảo A/B, dùng khi firmware Nintendo/Xbox khiến chữ in trên máy bị nhận ngược |
| Ctrl, Alt, Shift, Win | Chạm để giữ; chạm lần nữa để nhả. Màu nút cho biết đang giữ |
| `Release keys` | Nhả các phím và nút chuột do bàn phím/tay cầm riêng đang giữ |
| `Touchpad` / `Keyboard` | Chuyển màn dưới giữa bàn phím và touchpad |
| `Disconnect` | Đóng phiên RDP và trở lại danh sách kết nối |

Trong touchpad: một ngón di chuyển, chạm để click trái, hai ngón chạm để click phải, hai ngón kéo để cuộn. Có hai nút chuột riêng để nhấn giữ.

Bàn phím dưới gửi sự kiện thẳng tới phiên RDP; không cần Wayfinder, Accessibility, Shizuku hay bàn phím Android. Các phím Ctrl/Alt/Shift/Win được xử lý riêng để hoạt động trên máy không có bàn phím vật lý. Nếu không tìm thấy màn thứ hai phù hợp, vẫn có thể dùng bàn phím Android từ nút bàn phím của app ở màn trên.

Bàn phím riêng là bố cục PC QWERTY. Để gõ tiếng Việt kiểu Telex/VNI, bật bộ gõ tương ứng trên máy Windows; bản này chưa có bộ gõ tiếng Việt riêng trên Android.

## Giới hạn của bản thử nghiệm

- Chưa chạy trên AYN Thor vật lý. Chế độ hai màn INTERNAL, thứ tự nút theo firmware và thời gian dùng dài cần kiểm tra trên máy của bạn.
- Các phiên Azure Virtual Desktop/Windows 365, đăng nhập qua trình môi giới của Windows App và quy trình đăng nhập doanh nghiệp không nằm trong phạm vi bản này. Mục tiêu là kết nối RDP trực tiếp tới máy tính.
- Máy chủ Windows và phương thức xác thực cụ thể của bạn cần kiểm tra thực tế; báo cáo kèm APK ghi rõ môi trường đã kiểm tra.
- Nền native dùng thư viện dựng sẵn của upstream: FreeRDP 2.11.7, OpenSSL 1.1.1w. Đây là thử nghiệm tính năng hai màn, chưa nâng cấp hay kiểm toán bảo mật các thư viện đó.
- Các thư viện ARM64 hiện có căn chỉnh 4 KiB; chưa xác nhận tương thích Android dùng trang nhớ 16 KiB.

## Mã nguồn và ghi công

- Nền ứng dụng: https://github.com/iiordanov/remote-desktop-clients, commit `fd308d8596d95e221b388ff0172d7533cb255f31`.
- RDP: https://github.com/FreeRDP/FreeRDP/tree/2.11.7.
- Mã nguồn tùy biến và hướng dẫn build nằm trong `thor-rdp-0.1.0-source.zip`. Giữ các tệp LICENSE/COPYRIGHT và thông báo giấy phép của dự án gốc khi phân phối lại.
- App ID: `dev.thor.freerdp`. Khóa ký riêng được giữ ngoài gói mã nguồn; các lần cập nhật cần dùng cùng khóa.
