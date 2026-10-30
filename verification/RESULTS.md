# Kết quả kiểm chứng

Ghi chú dọn kho mã nguồn: giữ báo cáo audit/lint/unit test 1.3.1 và kết quả nghiên cứu TikTok/Zalo. Log build, báo cáo lặp của bản cũ, công cụ tạm và ảnh thiết bị đã được chuyển vào `.local-artifacts/` (chỉ lưu cục bộ, không commit); đường dẫn artifact cũ bên dưới là ghi nhận lịch sử. APK mới nhất giữ cục bộ trong `dist/`, không commit vào Git.

## Bản 1.3.1 — đồng bộ cài đặt Facebook

APK: `dist/OhMyLSPosed-1.3.1-debug.apk`. SHA-256: `2fc84febeb672766ec127d156c39377114eb7fab35686cc8eb5493fa0eefd6b2`.

- Nền trắng, chữ tối, màu xanh dương, tiêu đề và lề cùng kiểu Better Zalo. Việt hóa 31 tùy chọn và các nhóm; mô tả ngắn, nhấn giữ mô tả để đọc đầy đủ.
- Dùng chung `core/ui/ModuleSwitch` cho màn hình chính, Better Zalo và Facebook. Giữ khóa preference, mặc định, migration, kết nối remote settings và toàn bộ hook hiện có.
- Đồng bộ ô từ khóa, nút nhập/xuất phiên và hộp thoại biểu tượng ứng dụng. Giữ nguyên luồng chọn tệp, broadcast và xác nhận ẩn biểu tượng.
- Build thành công; **231 test / 48 suite, 0 failure/error**; lint **0 lỗi, 6 cảnh báo**. Audit APK và xác minh chữ ký qua.
- Đã cài APK trên Android 16, mở cài đặt Facebook và cuộn kiểm tra đầu/giữa/cuối: trạng thái LSPosed đã kết nối, công tắc bật/tắt hiển thị đúng, ô từ khóa và nút nhập/xuất không chồng chữ. Không thay giá trị công tắc, không nhập/xuất phiên, không mở app Facebook hoặc Zalo.
- Ảnh cục bộ: `verification/device/facebook-top-1.3.1.png`, `facebook-middle-1.3.1.png`, `facebook-bottom-1.3.1.png`. Báo cáo: `build-1.3.1.log`, `build-1.3.1-final.log`, `apk-audit-1.3.1.json`, `lint-results-1.3.1.txt`.

## Bản 1.3.0 — cài đặt Better Zalo và ảnh gốc

APK: `dist/OhMyLSPosed-1.3.0-debug.apk`. SHA-256: `01ae22fac2458b7acf60d57c89f46d3c453c212d2dc4a6d74198468fd4edc5c6`.

- Sáu công tắc riêng cho Better Zalo, lưu trong remote preference group độc lập; các công tắc chức năng cũ mặc định bật, ảnh gốc mặc định tắt khi chưa cấu hình.
- Hook ảnh gốc tìm cổng riêng của photo picker bằng DexKit; không sửa zCloud toàn cục. Luồng gửi và lựa chọn chất lượng vẫn native.
- Build thành công; **231 test / 48 suite, 0 failure/error**; lint **0 lỗi, 6 cảnh báo**; audit APK qua.
- Đã kiểm tra giao diện/kết nối/lưu cài đặt trên thiết bị. Người dùng xác nhận tự test hoạt động; giữ cấu hình người dùng hiện tại. Không thực hiện thêm thao tác trong Zalo sau xác nhận.
- Chi tiết và nguồn đối chiếu: `docs/ZALO_SETTINGS.md`. Ảnh cục bộ: `verification/device/zalo-settings-1.3.0.png`.

## Bản 1.2.1 — Better Messenger và Liquid Glass

APK: `dist/OhMyLSPosed-1.2.1-debug.apk`. SHA-256: `e5b0ca5f685b38a383fe5dc0bffa88f9e2f0f79504635c729b9d4dc9bb653675`.

- Đổi tên hiển thị OrcaPeek thành Better Messenger, giữ nguyên namespace và cấu hình.
- Sửa chuyển động/clipping và xử lý loading của Zalo. Bỏ fallback nền phẳng của bản thử 1.2.0 vì gây nháy; bản cuối giữ blur liên tục, chỉ thay cường độ khúc xạ một cách từ từ.
- **227 test, 47 suite, 0 failure/error**; lint **0 lỗi, 6 cảnh báo**. APK audit và chữ ký qua.
- Runtime thử trên Zalo 26.10.02 / Android 16, khởi động lại tiến trình và mở cả năm tab. Báo cáo `zalo-five-tabs-1.2.1.json`; chi tiết và giới hạn trong `docs/ZALO_GLASS_FIX.md`.

## Bản 1.1.1 — thay hẳn công tắc

APK: `dist/OhMyLSPosed-1.1.1-debug.apk`. SHA-256: `308d27b328ebd635dc9ac6529391b670cbb75038dea81f505320d718cdf238e7`.

- Thay SwitchCompat/Drawable bằng `ModuleSwitch`, kế thừa CompoundButton: thanh bo góc, nút trượt vuông bo nhẹ, dấu tích khi bật; màu xanh dương và xám khi tắt.
- Vị trí nút được vẽ trực tiếp từ `isChecked()`, không phụ thuộc padding/optical inset hay animation thumb của SwitchCompat. Vẫn hỗ trợ bàn phím, trạng thái checked của trợ năng và vùng chạm 64×48dp.
- Build và lint thành công: 0 lỗi, 6 cảnh báo như trước. Audit APK vẫn qua. Lần này chỉ đổi UI; không chạy lại 217 unit test đã qua ở bản 1.1.0.
- Đã kiểm tra trực tiếp trên Android 16: Locket tắt → nút nằm bên trái, bộ đếm 5/6; bật lại → dấu tích bên phải, bộ đếm 6/6; mở lại app vẫn đúng. Giữ trạng thái ban đầu của người dùng.
- Ảnh: `verification/device/main-1.1.1.png`, `toggle-off-1.1.1.png`. Toàn bộ icon, điều hướng mới và 313 chuỗi Việt hóa TikTok của 1.1.0 được giữ.

## Bản 1.1.0 — icon, điều hướng và tiếng Việt TikTok

APK: `dist/OhMyLSPosed-1.1.0-debug.apk`. SHA-256: `e324bacd38336dedf02e3ead086a16a436dcd6431a1d169da13d60f4cfe879da`.

- Build thành công; **217 test / 45 suite**, không failure/error; lint **0 lỗi, 6 cảnh báo** như bản trước. Báo cáo: `unit-tests-1.1.0.json`, `lint-results-1.1.0.txt`, `apk-audit-1.1.0.json`.
- Khôi phục đủ **313 chuỗi vi** từ `Decompiled-Java-Raw/defpackage/T0.java`. Cả 313 chuỗi tiếng Anh trùng thứ tự với source hiện tại. Đã chuẩn hóa từ ngữ còn tiếng Anh và sửa nghĩa Clear display; test locale vi-VN, đủ khóa và giữ placeholder. Xem `docs/TIKTOK_VI.md`.
- Trên Android 16: sáu icon của app đích hiện đúng; không có các liên kết phụ; dòng TikTok hiện nguyên mô tả gốc, dòng Facebook mở setting riêng; công tắc không mở setting/mô tả; công tắc thử được khôi phục về trạng thái ban đầu. Không đổi các lựa chọn module hiện có của người dùng.
- Giao diện Framed sheet trên điện thoại tiếp tục dùng nền trắng; icon thật, chữ tối, công tắc xanh dương/xám nhạt. Đã sửa kích thước thumb/track sau lượt xem screenshot. Hộp mô tả nền trắng. Không chạy inspector HTML cho Android native.
- Ảnh chụp cục bộ: `verification/device/main-1.1.png`, `description-1.1.png`, `toggle-off-1.1.png`.
- Chưa mở toàn bộ setting TikTok trong host để kiểm tra trực quan 313 chuỗi; việc chọn bản Việt hóa và placeholder đã được unit test. Không thay đổi logic hook.

## Bản 1.0.0

Ngày 30/10/2026. APK: `dist/OhMyLSPosed-1.0.0-debug.apk`.

SHA-256: `3A082AB03AC873D9CA8AC4912E7E92B4682A87EC732A20C193C5F47947F183D7`.

## Build và kiểm thử

- `:app:assembleDebug testDebugUnitTest :app:lintDebug`: **BUILD SUCCESSFUL**.
- **215 test / 45 suite**, 0 failure, 0 error: core 8, Chrome 15, Facebook 92, Messenger 9, TikTok 91. Zalo và Locket không có unit test upstream; hai phần này dùng adapter được kiểm thử ở core.
- Core kiểm tra gate theo package/tiến trình, trạng thái tắt, lỗi đọc cấu hình, early return, sửa args, exception gốc và callback lỗi.
- Lint: **0 lỗi, 6 cảnh báo**. Các cảnh báo gồm target SDK 35, ba dependency có bản mới, receiver kết quả phiên cần exported để Facebook gửi về, và backup rules. Service 101 được giữ để không nâng compile SDK lên 37. Receiver chỉ hiển thị thông báo kết quả, không thực hiện xuất/nhập phiên.
- `NotificationPermission` được bỏ qua riêng cho manifest trong `app/lint.xml`: thông báo streak chạy trong tiến trình TikTok, kiểm tra quyền runtime của TikTok, không đăng thông báo từ app quản lý.
- DexKit lint còn diagnostic về `firstOrNull()` tại một số hook Facebook từ source gốc. Chưa xác nhận runtime các điểm tìm hook này trên bản Facebook đang cài.
- Audit APK xác nhận đủ sáu entry, đúng bảy package scope, có DexKit cho bốn ABI, không có legacy descriptor, không đóng gói các class API Xposed/LibXposed/Yuki.
- Chữ ký APK v2 được `apksigner verify` xác nhận hợp lệ. Đây là chữ ký debug, chưa phải khóa phát hành.

Chi tiết máy đọc: [unit-tests.json](unit-tests.json), [apk-audit.json](apk-audit.json), [lint-results.txt](lint-results.txt), [signature.txt](signature.txt).

## Trên thiết bị

Thiết bị đang kết nối qua ADB: Android 16 / API 36, màn hình 1280×2772, density 520, LSPosed API 102.

Đã xác nhận:

- Cài/nâng cấp APK và mở app quản lý thành công, không có crash trong log AndroidRuntime được kiểm tra.
- Giao diện nền trắng; tiêu đề tránh thanh trạng thái, cuộn được, sáu công tắc và bộ đếm đúng dữ liệu.
- Trạng thái chưa kết nối khóa công tắc; khi dịch vụ kết nối, hiển thị LSPosed API 102 và mở khóa.
- Công tắc Locket chuyển 0/6 → 1/6, giữ trạng thái sau force-stop/mở lại app quản lý, sau đó được trả về tắt.
- Mở setting Facebook từ app quản lý; màn hình nhận ngay dịch vụ đã kết nối và mở khóa các tùy chọn. Đã sửa lỗi listener đăng ký muộn bằng kết nối chung trong core.
- Công tắc News Feed Facebook lưu trạng thái khi rời/mở lại màn hình, sau đó được trả về bật như mặc định. Công tắc tổng Facebook vẫn tắt trong lúc kiểm tra.
- Nút trở về danh sách hoạt động.

Ảnh chụp cục bộ trong `verification/device/`: `main.png`, `facebook.png`, `toggle-on.png`, `toggle-persisted.png`, `facebook-toggle-persisted.png`. Ảnh không được đưa vào git theo mặc định. Bộ kiểm tra HTML/browser của skill thiết kế không áp dụng cho UI Android native; đã xem trực tiếp screenshot điện thoại. Chưa kiểm tra tablet, xoay ngang hay cỡ chữ trợ năng lớn.

## Giới hạn

**Chưa chạy xác nhận từng hook trong cả sáu app mục tiêu.** Không thay scope hoặc vô hiệu hóa các module cũ trên thiết bị, không mở nội dung tài khoản để kiểm tra các chức năng. Build, unit test và kết nối cấu hình thành công không chứng minh mọi điểm hook tương thích với bản app mục tiêu.

Trước khi sử dụng: bật module/phạm vi trong LSPosed, tắt module cũ tương ứng, bật phần mong muốn trong OhMyLSPosed và khởi động lại app mục tiêu. Các phần Zalo/Locket đã chuyển backend nên đặc biệt cần kiểm tra runtime. Giữ source gốc trên đĩa để đối chiếu; không sửa các project nguồn.
