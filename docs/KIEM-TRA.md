# Kiểm tra Thor RDP 0.1.0

## Phạm vi

Kiểm tra trên Android Emulator API 34 x86_64, hai màn hình: màn chính 1280×720 và màn phụ 960×544. Màn phụ có Android display ID 2, giúp xác nhận mã không gắn cứng vào display ID 1.

Máy chủ là xrdp/Xorg trong container riêng, dùng tài khoản giả lập dành cho QA. Cổng chỉ gắn vào loopback của máy phát triển; không bật dịch vụ RDP hay đổi firewall Windows của người dùng. Các sự kiện dưới đây được ghi nhận ở phía máy chủ sau khi đi qua kết nối RDP thật.

## Kết quả đã xác nhận

- Kết nối RDP, trao đổi TLS và kiểm tra dấu vân tay chứng chỉ tự ký khớp với máy chủ thử nghiệm.
- Bàn phím màn dưới gửi đúng Q/W/E, Enter và Backspace; máy chủ nhận đủ sự kiện nhấn/nhả.
- Chạm bàn phím màn dưới giữ focus của desktop ở màn chính (display 0).
- Joystick di chuyển con trỏ và dừng khi trở về giữa; đầu vào thử nghiệm tạo chuỗi vị trí thực ở máy chủ.
- Nút Android A gửi chuột phải; B gửi chuột trái, mỗi lần có cả nhấn và nhả.
- Ctrl+A, Ctrl+click và Win có sự kiện đúng ở máy chủ. `Release keys` nhả Ctrl đang giữ.
- Bàn phím hiển thị đủ hàng phím ở kích thước 960×544, density 240.
- Bản release đã ký được cài mới và kiểm tra lại: hủy ở hộp thoại chứng chỉ, kết nối lại/chấp nhận chứng chỉ, xác thực và vào desktop, gõ `release` + Enter từ màn dưới, di chuyển joystick và nhấn A/B đúng loại chuột.
- Chuyển app ra nền rồi quay lại tiếp tục được phiên đã mở. Thử thay đổi cấu hình màn phụ vẫn giữ được bàn phím và nhập chữ; kiểm tra hiển thị tại density 320 không thấy cắt hàng phím.

Kiểm tra chức năng đầy đủ dùng bản x86_64 trên emulator. APK giao cho Thor là ARM64 từ cùng mã nguồn và cấu hình release, đã kiểm tra chữ ký/căn chỉnh ZIP nhưng chưa chạy trên phần cứng ARM64.

## Nhận dạng bản build

- App ID: `dev.thor.freerdp`; phiên bản `0.1.0-thor`, version code `1`.
- APK ARM64: `thor-rdp-0.1.0-arm64.apk`, 27.368.124 byte.
- SHA-256 ARM64: `6cbe946dd8f868ccb5036855df45b72018da8ce52179b516f7dcf1c4a25b7956`.
- SHA-256 bản x86_64 release đã chạy QA: `f66e18601d3e792c32ecf72f4e5fc18a223d76c8e21e997328ec78d155641c22`.
- SHA-256 chứng chỉ ký: `f8f6d0138ae97d22595fe3ec2c581f82b3ff1d8fa31a81fb8d4758ab25b1ee35`.
- APK được kiểm tra bằng Android SDK `apksigner` và `zipalign`; không kèm khóa ký riêng hoặc mật khẩu của khóa.

## Chưa xác nhận

- Chưa chạy APK ARM64 trên AYN Thor vật lý. Nhãn A/B theo firmware có thể cần đảo bằng nút trên bàn phím.
- Chưa kết nối tới máy Windows của người dùng, chưa kiểm tra NLA, tài khoản Microsoft/Entra, RD Gateway, Windows 365 hoặc Azure Virtual Desktop.
- Chưa đo pin, độ trễ khi đi xa, dùng liên tục nhiều giờ, âm thanh/microphone và chuyển hướng thiết bị.
- Emulator dùng một màn phụ VIRTUAL. Thor dùng hai màn INTERNAL; mã đã xét tất cả màn và xử lý lỗi Android không cho mở màn phụ, nhưng cần thử trên Thor để xác nhận.

## Thay đổi đáng chú ý

- Một phiên RDP dùng chung cho hình ảnh, bàn phím và tay cầm; cửa sổ bàn phím không nhận focus bàn phím hệ thống.
- Phím bổ trợ từ bàn phím riêng không bị bộ lọc IME bỏ qua khi thiết bị không có bàn phím QWERTY vật lý.
- Nhả các phím/nút đang giữ khi chuyển ứng dụng, mất màn phụ hoặc đóng phiên; xử lý hủy joystick để tránh con trỏ tiếp tục chạy.
- Đưa các sự kiện nhả vào hàng đợi trước khi ngắt RDP; dừng executor nhập liệu khi đóng.
- Hủy lúc đang chờ chấp nhận chứng chỉ sẽ đánh thức phiên đang chờ; phiên kết nối tới muộn sau khi hủy được đóng lại.

Thư viện native kế thừa FreeRDP 2.11.7 và OpenSSL 1.1.1w từ gói dựng sẵn của upstream. Kiểm tra này xác nhận tính năng trong môi trường nêu trên, không phải kiểm toán bảo mật hay bảo đảm tương thích mọi máy chủ Windows.
