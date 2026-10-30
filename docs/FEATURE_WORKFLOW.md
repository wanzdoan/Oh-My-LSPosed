# Thêm, bớt và sửa feature

Quy tắc chung ở [AGENTS.md](../AGENTS.md); mô tả cấu trúc hiện tại ở [ARCHITECTURE.md](ARCHITECTURE.md). Tài liệu này chỉ ra các điểm cần thay đổi để giữ source, UI, cấu hình và APK đồng bộ.

## Sửa hoặc thêm chức năng trong module hiện có

1. Xác định feature, entry, nơi tìm target, cấu hình và UI tương ứng. Đọc test/tài liệu hiện có trước khi sửa. Phân biệt lỗi UI manager, lưu cấu hình, tìm target và callback runtime.
2. Giữ sửa đổi trong feature đó. Nếu thêm chức năng, khai báo khóa/mặc định ở mô hình cấu hình của feature; UI và hook dùng chung định nghĩa đó. Ví dụ hiện có: `ZaloOptions`, Facebook `Settings` và `TOGGLE_SECTIONS`.
3. Cài hook qua entry hiện có, kiểm tra gate của chức năng trước khi gây tác động. Tách resolver/policy có thể kiểm thử khỏi callback khi logic đủ phức tạp. Giữ đường gọi original khi chức năng tắt hoặc không hỗ trợ phiên bản host.
4. Nếu có settings rời, thêm vào màn hình của feature. Nếu có settings nhúng, tiếp tục thêm trong host; không tạo bản sao ở manager. Mô tả phụ thuộc giữa các tùy chọn nếu có.
5. Test phần logic thay đổi và build/lint phù hợp. Với hook mới/sửa target, kiểm tra host đúng phiên bản; chỉ báo là đã kiểm chứng runtime nếu đã thực sự chạy tình huống đó.

Không đổi default của chức năng cũ để tiện test. Khi bỏ một chức năng, gỡ đồng thời nhãn/điều khiển, nơi đọc cấu hình và nơi cài hook; kiểm tra các chức năng dùng chung resolver/callback. Khóa cũ có thể để lại như dữ liệu không còn dùng; chỉ xóa khi có migration rõ ràng, không reset cả nhóm preferences.

## Thêm module mới

| Điểm thay đổi | Việc cần làm |
| --- | --- |
| `features/<id>/build.gradle.kts` | Android Library, namespace riêng, Java/Kotlin JVM 21, minSdk 28/compileSdk 36 theo dự án; phụ thuộc core và API 102 `compileOnly`. Chỉ thêm thư viện thực sự cần. |
| Source feature | Entry kế thừa `GatedModule("<id>", "<target.package>", ...)`, hook và cấu hình riêng; không tạo Application hoặc provider kết nối LSPosed trùng core. |
| `settings.gradle.kts` | Thêm project feature. |
| `app/build.gradle.kts` | Thêm dependency feature vào app. |
| `app/src/main/resources/META-INF/xposed/java_init.list` | Thêm đúng tên đầy đủ của entry; không trùng, không dùng legacy descriptor. |
| `app/src/main/resources/META-INF/xposed/scope.list` | Thêm package đích chưa có. |
| `app/src/main/AndroidManifest.xml` | Thêm package visibility trong `queries`; khai báo Activity settings rời không exported và component cần thiết. Không cấp thêm permission/export nếu chưa cần. |
| `ModuleCatalog.java` | ID ổn định, tên hiển thị, edition, kiểu settings, resource mô tả và các package đích. |
| `MainActivity.java` | Nếu thêm settings rời, bổ sung nhánh điều hướng và kiểu settings tương ứng trong catalog. Kiểu `NONE`/`IN_APP` hiện mở mô tả gốc, không tự mở host. |
| Tài nguyên | Prefix theo feature; giữ mô tả LSPosed gốc và bản dịch cần thiết; icon host được manager tự lấy theo package. |
| `scripts/audit_apk.py` | Cập nhật số entry và tập scope kỳ vọng. Audit hiện chốt sáu entry/bảy package; không bỏ assertion để làm audit qua. |
| Tài liệu | Cập nhật README, sơ đồ module, nguồn/license trong `upstream/` và hướng dẫn riêng nếu hành vi phức tạp. |

Với feature mới, có thể dùng các package `hooks/`, `discovery/`, `settings/`, `ui/` theo nhu cầu. Chỉ tạo lớp/thư mục khi có trách nhiệm thực tế; không tạo hàng loạt lớp trống. Source hiện có không cần đổi package theo mẫu này.

ID là hợp đồng lưu trữ: đổi tên hiển thị không đổi `<id>.enabled`, nhóm cấu hình hay namespace nguồn. Một module có nhiều package vẫn có một ID/công tắc tổng nếu đó là cùng tính năng; khai báo đầy đủ package trong gate, catalog, scope và visibility.

## Bỏ một module

1. Gỡ catalog và nhánh điều hướng/Activity riêng. Không xóa UI, permission, receiver hoặc tài nguyên dùng chung khi vẫn còn nơi sử dụng.
2. Gỡ entry khỏi `java_init.list`. Chỉ gỡ package khỏi scope/queries khi không còn feature nào dùng package đó; kiểm tra cả các package thay thế của module.
3. Gỡ dependency app và include Gradle, rồi xóa đúng thư mục feature đã xác định. Giữ các source gốc bên ngoài workspace.
4. Cập nhật kỳ vọng audit, README và tài liệu kiến trúc. Kiểm tra tham chiếu còn sót bằng `rg`, bao gồm R resources, tên entry, ID, component và dependency.
5. Không tái sử dụng ID hoặc khóa preference đã bỏ cho một chức năng khác. Không tự xóa cấu hình của module còn lại. Trường hợp cần dọn dữ liệu có migration riêng.
6. Build/test/lint/audit APK để xác nhận không còn entry/component thiếu lớp và những module giữ lại vẫn đóng gói đúng.

## Các hợp đồng cần giữ khi chỉnh phần dùng chung

| Thay đổi | Phạm vi phải kiểm tra |
| --- | --- |
| `GatedModule`, `ModuleIds` | Lọc package/tiến trình, default tắt của công tắc tổng, lỗi đọc cấu hình, callback entry của các feature bị tác động. |
| `ModuleApplication` | UI mở trước/sau service bind, service mất/kết nối lại, đăng ký/hủy observer, không ghi preference do đồng bộ trạng thái UI. |
| `ModuleSwitch` | Danh sách chính, Better Zalo, Facebook; bật/tắt, disabled, vùng chạm và trợ năng. Không trộn logic lưu preference vào widget. |
| `core/compat` | Hook Zalo/Locket đang dùng adapter; semantics before/after, result/throwable, gọi original và hành vi khi callback lỗi. |
| Descriptor/Gradle/manifest | Entry tồn tại trong DEX, đúng scope, không đóng gói runtime API, không trùng tài nguyên/component. |

## Kiểm chứng và giao bản

Chạy tại thư mục gốc bằng PowerShell:

```powershell
# Kiểm tra logic có chọn lọc trong lúc phát triển; ví dụ Facebook và core.
./gradlew.bat '-Dorg.gradle.java.home=<duong-dan-JDK-21>' :features:facebook:testDebugUnitTest :core:testDebugUnitTest --console=plain

# Kiểm tra đầy đủ trước khi giao APK debug.
./scripts/build.ps1
```

`scripts/build.ps1` chọn JDK 21 từ cache đã cấu hình nếu có, nếu không dùng `JAVA_HOME`; SDK lấy từ `local.properties`. Không thêm SDK/JDK tải mới nếu bản cài hiện tại đủ dùng. Trên nền tảng khác có thể dùng Gradle wrapper tương ứng với cùng task; tránh thay script Windows chỉ để sửa một feature.

APK build nằm ở `app/build/outputs/apk/debug/app-debug.apk`. Khi phát hành, sao chép vào `dist/OhMyLSPosed-<version>-debug.apk`, kiểm tra chữ ký bằng `apksigner verify`, ghi SHA-256 và kết quả thực tế vào `verification/RESULTS.md`. APK phát hành phải tương ứng source cuối cùng; sửa code sau kiểm chứng thì chạy lại các bước bị ảnh hưởng.

Với UI, kiểm tra đầu/giữa/cuối trang, tên dài, trạng thái bật/tắt/disabled và system insets trên thiết bị hoặc emulator. Screenshot thiết bị nằm trong `verification/device/` (đã gitignore). Với hook, nêu rõ host/Android đã thử, không dùng build/unit test làm bằng chứng hook hoạt động. Không cần tăng version/build APK cho chỉnh sửa tài liệu thuần túy.
