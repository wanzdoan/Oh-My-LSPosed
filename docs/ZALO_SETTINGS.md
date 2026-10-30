# Better Zalo: cài đặt và ảnh gốc

## Màn hình cài đặt

Nhấn dòng Zalo trong OhMyLSPosed để mở `ZaloSettingsActivity`. Cấu hình lưu qua service LSPosed trong nhóm riêng `better_zalo_settings`; hook đọc một bản chụp lúc tiến trình Zalo khởi động. Sau khi đổi cần khởi động lại tiến trình Zalo.

| Khóa | Công tắc | Mặc định khi chưa có cấu hình |
| --- | --- | --- |
| liquid_glass | LiquidGlass Bar | Bật |
| hide_seen | Ẩn trạng thái đã xem | Bật |
| hide_typing | Ẩn trạng thái đang nhập | Bật |
| custom_ringtone | Cho phép thay đổi nhạc chuông | Bật |
| anti_recall | Chống thu hồi tin nhắn | Bật |
| original_photo | Gửi ảnh chất lượng gốc | Tắt |

Các chức năng cũ giữ mặc định cũ; ảnh gốc là tùy chọn mới. Không di chuyển bộ chọn tệp nhạc chuông ra khỏi Zalo. Tắt chức năng nhạc chuông không xóa tệp hay đổi nhạc chuông người dùng đã chọn.

`ZaloOptions` là mô hình không phụ thuộc Android, kiểm thử được độc lập. Hook entry chỉ cài những nhóm chức năng đang bật. Hai nhóm đã xem/đang nhập được tách riêng. Không thay đổi cấu hình hoặc hooks của các feature khác.

## Nghiên cứu luồng Original

Đã đối chiếu source của Zalo 26.10.02 tại `H:/Projects/BetterZl/decompiled_source/jadx_java/sources/` và source 26.08.02 tại `H:/Projects/BetterZl/device_zalo/jadx_out/sources/`.

Ở 26.10.02, `BottomSheetSelectQualityView.BottomSheetSelectQuality.I6(int)` xử lý ba lựa chọn STANDARD/HD/ORIGINAL. Khi chọn ORIGINAL, nó kiểm tra `xl1.u.e()`; nếu không được phép thì mở luồng quảng bá zCloud. Chính kiểm tra này cũng hạ lựa chọn Original cũ xuống HD khi mở lại bảng.

`xl1.u.e()` là phép AND giữa cờ chức năng `b()` và kiểm tra tài khoản `f()`. Lớp này còn lưu lựa chọn theo khóa `LAST_SELECTION_MEDIA_QUALITY_` và theo `threadId`. Ở bản cũ, cùng cấu trúc nằm trong `fi1.u`; tên obfuscated không được hardcode trong hook mới.

`OriginalPhotoHook` dùng DexKit 2.2.0 để tìm theo khóa lưu lựa chọn, chữ ký lớp và call graph. Chỉ khi tìm duy nhất lớp/method đúng cấu trúc mới cài hook. Hai method được mở là cờ chức năng của picker và điều kiện được chọn Original. Không sửa kiểm tra zCloud toàn cục, không cấp dung lượng lưu trữ hay quyền dịch vụ khác. Nếu không tìm duy nhất, chỉ chức năng ảnh gốc bỏ qua và ghi log `BetterZalo.OriginalPhoto`; các hook cũ tiếp tục chạy.

Người dùng vẫn chọn Original trong bảng Chọn chế độ gửi. Chất lượng mặc định Tiêu chuẩn/HD và quá trình gửi/xử lý ảnh gốc do Zalo quản lý; module không tự gửi ảnh, không đổi mọi ảnh thành Original và không thay thế upload endpoint.

## Kiểm chứng 1.3.0

- Build, lint và audit APK qua; 231 test / 48 suite, không failure/error. Lint 0 lỗi, 6 cảnh báo như bản trước.
- Thêm bốn test cho mặc định tương thích, tắt toàn bộ, tách hai công tắc riêng tư và bật ảnh gốc độc lập.
- Màn hình cài đặt đã kiểm tra trên Android 16: nhận kết nối service có sẵn, hiển thị đúng sáu công tắc, mở từ dòng Zalo và lưu cấu hình.
- Người dùng xác nhận đã tự test thấy hoạt động trong phiên làm việc. Agent không gửi ảnh hoặc tin nhắn thử đến người khác.
- Giữ nguyên cài đặt hiện tại sau khi người dùng xác nhận; không tự đưa công tắc ảnh gốc về tắt.

APK: `dist/OhMyLSPosed-1.3.0-debug.apk`.
