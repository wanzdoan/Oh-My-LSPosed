# Kiến trúc

Quy ước dành cho agent và người phát triển nằm trong [AGENTS.md](../AGENTS.md). Khi thêm, bớt hoặc sửa feature, làm theo [FEATURE_WORKFLOW.md](FEATURE_WORKFLOW.md), bao gồm các điểm cần cập nhật trong Gradle, catalog, manifest, descriptor và audit.

```text
app/                 Danh sách module, công tắc, điều hướng
core/                Gate, khóa cài đặt, adapter callback cổ điển
features/zalo/       BetterZalo, thanh kính và nhạc chuông
features/locket/     Locket Gold, bundle và runtime hooks
features/tiktok/     TikTok Patch, toàn bộ UI nhúng trong TikTok
features/facebook/   Hooks, setting riêng, tải media, phiên đăng nhập
features/messenger/  Better Messenger, menu và preview trong Messenger
features/chrome/     ChromeScript, userscript manager trong Chrome
```

`features/*` là sáu Android Library, không phụ thuộc lẫn nhau. Mỗi thư viện giữ package mã nguồn riêng và toàn bộ test có sẵn. `upstream/source.json` ghi source và phiên bản gốc; license có trong `upstream/` nếu source cung cấp. Bản gốc trên đĩa không bị sửa.

## Vòng đời và cô lập

APK sử dụng duy nhất modern LibXposed API 102, sáu entry riêng trong `app/src/main/resources/META-INF/xposed/java_init.list`. Không trộn modern và legacy descriptor. API framework là `compileOnly`; không đóng gói Xposed API vào APK.

Mọi entry kế thừa `GatedModule`. Gate lọc package và tiến trình chính, đọc `<id>.enabled` từ nhóm `ohmy_modules`, rồi mới chuyển tiếp callback. Nếu không đọc được cấu hình thì không cài hook. Lỗi khởi tạo được ghi dưới tag `OhMy/<id>` và bắt riêng cho entry đó. Các callback sau khi cài vẫn sử dụng lớp xử lý lỗi có sẵn của từng feature; đây không phải cơ chế sandbox chống mọi crash từ hook.

Công tắc tổng được chụp lúc tiến trình khởi động, tránh tháo một phần các hook đang chạy. Tắt module và khởi động lại app mục tiêu sẽ không cài hook của phần đó. Không dùng hot reload cho sáu nguồn có nhiều trạng thái tĩnh. Nhánh tiến trình phụ không được khởi tạo.

Facebook/TikTok/Chrome/Messenger giữ API hiện đại. Zalo/Locket chuyển lớp kết nối sang API 102 bằng `dev.ohmy.core.compat`, một adapter nội bộ có before/after, thay kết quả, truyền exception và gọi original qua invoker ORIGIN. Adapter không định nghĩa hay gọi các class `de.robv.android.xposed`. Zalo có thêm `HookSession` để giữ cấu trúc callback ban đầu, bỏ runtime và bộ sinh entry Yuki. Tên class/method và chiến lược tìm hook từ source gốc được giữ lại, kể cả các fallback phụ thuộc phiên bản; không tự tuyên bố hỗ trợ mọi bản app tương lai.

## Cài đặt và tài nguyên

`core.ModuleApplication` quản lý một kết nối service chung. UI tổng, Better Zalo và Facebook dùng remote preferences qua service; hook đọc qua framework. `ohmy_modules` chỉ chứa công tắc tổng, `better_zalo_settings` riêng cho Zalo, `fbar_settings` riêng cho Facebook. TikTok/Chrome giữ dữ liệu trong app mục tiêu; chọn tệp nhạc chuông Zalo vẫn nằm trong app mục tiêu như trước.

Tài nguyên từng feature được prefix theo id, kể cả view id và drawable. Khi thêm tài nguyên, tiếp tục dùng prefix này để tránh ghi đè lúc APK merge. Broadcast kết quả phiên Facebook trỏ về application id mới, action vẫn có namespace riêng. Alias ẩn icon của Facebook hiện điều khiển icon OhMyLSPosed và vẫn có đường mở lại qua LSPosed.

## Thêm module

1. Tạo Android Library `features/<id>`; phụ thuộc `:core` và `compileOnly` API 102. Không phụ thuộc feature khác.
2. Viết entry kế thừa `GatedModule("<id>", "<target.package>")`; cài hook trong `featureReady`, hoặc dùng `featurePackageLoaded` khi cần sớm hơn.
3. Thêm project vào `settings.gradle.kts` và dependency `app/build.gradle.kts`.
4. Thêm entry vào `java_init.list`, package vào `scope.list`, package visibility trong manifest và một mục `ModuleCatalog`.
5. UI riêng: khai báo activity không exported và mở khi nhấn dòng tên trong app quản lý. UI nhúng: giữ trong host; dòng tên chỉ hiển thị resource mô tả gốc trong LSPosed.
6. Prefix tài nguyên và nhóm preference bằng id mới. Chạy test của feature, test core, lint và audit APK.

`scripts/import_sources.py`, `adapt_imports.py`, `namespace_resources.py` ghi lại bước nhập source ban đầu. **Không chạy lại lên cây đang phát triển**: chúng không thay thế việc review/diff bản upstream mới. Cập nhật feature bằng diff trong thư mục của nó, giữ các thay đổi tích hợp được mô tả ở trên.

## Thiết kế

Màn hình chính trả lời “module nào đã bật?”. Sáu dòng có icon thật lấy bằng PackageManager từ package đích đã cài; nếu không tìm thấy app thì dùng icon mặc định hệ thống. Tên nguồn ở dòng phụ, dấu `>` bên trái công tắc. Vùng tên/icon/dấu `>` và vùng công tắc là hai thao tác độc lập. Zalo/Facebook mở setting riêng; các mục khác hiện nguyên resource mô tả gốc của module trong LSPosed, không mở app mục tiêu. Nền trắng, chữ tối, xanh dương cho trạng thái và công tắc, vùng chạm ít nhất 48dp. Không có dòng liên kết phụ hoặc thông báo buộc dừng ở màn hình chính. Bộ đếm lấy từ chính trạng thái công tắc.

Tham chiếu skill: Framed sheet, seed 1844187577, bố cục điện thoại bỏ sidebar. Lượt đầu Data terminal bị thay vì theme tối trái yêu cầu nền trắng. Native Android dùng font hệ thống và điều khiển Material; inspector HTML không áp dụng. Kiểm tra hình ảnh qua screenshot trên thiết bị Android, ghi rõ phạm vi trong RESULTS.md.

Từ 1.1.1, công tắc tổng dùng `ModuleSwitch` kế thừa CompoundButton, tự vẽ track/thumb từ trạng thái checked. Không dùng SwitchCompat cho các công tắc này để tránh lỗi optical inset và vị trí thumb. Phần lưu cấu hình vẫn nằm trong MainActivity và core như trước.

Từ 1.3.1, `ModuleSwitch` nằm tại `core/src/main/java/dev/ohmy/core/ui/ModuleSwitch.java`, dùng chung cho danh sách module, Better Zalo và cài đặt Facebook. Feature chỉ phụ thuộc core; không phụ thuộc module app hoặc feature khác. Nhãn cài đặt Facebook là tiếng Việt; khóa preference, mặc định và hook giữ nguyên.
