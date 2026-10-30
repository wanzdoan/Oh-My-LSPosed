# Better Zalo: ổn định Liquid Glass khi tab tải lần đầu

## Phạm vi

Sửa trong `features/zalo`, không đổi logic tin nhắn/nhạc chuông hoặc feature khác. Tên hiển thị Messenger đổi thành Better Messenger; package, khóa cấu hình và entry cũ được giữ để không mất trạng thái.

## Nguyên nhân tìm thấy trong mã

- Indicator phóng gần 1,4 lần khi nhấn nhưng chỉ có clipping của root, không có clipping theo hình thanh bar. Khi tab đích nở chiều rộng còn tab cũ co lại, phần hình đang lấy mẫu có thể tràn mép.
- Tọa độ chạy bằng spring, chiều rộng chạy bằng phép nội suy khác. Hai cạnh có thể đi lệch nhịp khi native tab vừa đổi kích thước. Việc cắt dt ở 32ms cũng không xử lý đúng thời gian trễ của frame nặng.
- Chọn tab ngay trong từng callback progress có thể đọc trạng thái trung gian, trước khi cả năm native item được cập nhật.
- Renderer tạo đối tượng RenderEffect cả khi thông số không đổi và invalidate root trong mỗi frame animation của indicator.

Đây là các lỗi/rủi ro xác định được trong mã; việc Zalo đang tải trang là điều kiện làm chúng dễ thấy hơn, không có kết luận rằng toàn bộ frame chậm đều do module.

## Bản cuối 1.2.1

- `GlassMotion`: tích phân lò xo tắt dần tới hạn bằng công thức giải tích, giới hạn x/width trong bar, không overshoot press, xử lý tab chưa đo và thay đổi kích thước.
- Resolve selected một lần lúc vẽ, sau nhóm callback native. Giữ native icons, badge, click và cách chia chiều rộng tab.
- Clip indicator trong viên bar trước transform; mức phóng khi nhấn còn tối đa 6%.
- Hủy trạng thái nhấn/kéo khi native bar bị ẩn, mất focus hoặc detach giữa chừng, kể cả khi không nhận được ACTION_UP.
- **Blur, ambient và lớp material luôn dùng cùng pipeline. Không thay live backdrop bằng nền phẳng, không fade opacity của blur.** Bản thử 1.2.0 dùng fallback phẳng đã gây nháy và bị loại bỏ.
- Trong lúc loading/bố cục chưa ổn định, chỉ hạ dần cường độ khúc xạ xuống 35%; khi ổn định thì tăng lại từ từ. Bố cục host được gọi lại nhưng không đổi hình học không reset hiệu ứng.
- Giữ blur 4dp và material xuyên suốt. Cache RenderEffect theo thông số thật sự thay đổi. Animation indicator không yêu cầu invalidate toàn root.

## Kiểm chứng

- 227 test qua; thêm 7 test chuyển động và 3 test loading/đổi bố cục. Kiểm tra bỏ frame 160–500ms, đổi hướng nhanh, width thu từ 314px về 196px ở tab mép phải, layout không hợp lệ, idle, press-release, reset giữa lúc nhấn và loading kéo dài.
- Build/lint thành công; APK audit đủ sáu entry, không đóng gói API framework; chữ ký debug hợp lệ.
- Thiết bị thử Android 16 / API 36, Zalo 26.10.02. Đã khởi động lại tiến trình Zalo để các tab được dựng lần đầu rồi chuyển qua cả năm tab. Runtime có đúng revision `1.2.1-glass-continuous`, policy `continuous-blur`, không có shaderError.
- Diagnostic chỉ ghi hình học, frame, selected và trạng thái renderer; không ghi nội dung tin nhắn. Ảnh trong `verification/device/` chỉ phục vụ kiểm tra cục bộ và bị gitignore.
- Các lần đo gfx trước/sau có cache, mạng và số frame khác nhau; không dùng để tuyên bố tốc độ tăng theo phần trăm. Chưa kiểm chứng mọi ROM, GPU, chế độ tối hoặc mọi loại loading riêng của Zalo.

Tài liệu tham khảo: [RenderNode](https://developer.android.com/reference/android/graphics/RenderNode), [RenderEffect](https://developer.android.com/reference/android/graphics/RenderEffect), [Slow rendering](https://developer.android.com/topic/performance/vitals/render). Giữ nguyên nguồn shader và attribution upstream.
