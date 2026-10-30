# Hướng dẫn làm việc với OhMyLSPosed

## Mục tiêu và phạm vi

- Một APK quản lý nhiều module LSPosed, mỗi feature có thể thêm, bỏ hoặc sửa độc lập.
- Trao đổi và viết nội dung giao diện bằng tiếng Việt; thông báo ngắn, dễ hiểu.
- Đọc `docs/ARCHITECTURE.md` và `docs/FEATURE_WORKFLOW.md` trước khi đổi cấu trúc hoặc thêm/bỏ module. Khi sửa một feature, đọc thêm tài liệu riêng của nó nếu có.
- Ưu tiên thay đổi nhỏ trong đúng feature. Không nhân tiện viết lại các module đang hoạt động hoặc chuyển framework.

## Ranh giới kiến trúc

```text
app -> core
app -> features/<id> -> core
```

- `app/`: danh sách module, icon, công tắc tổng, điều hướng và khai báo tích hợp APK.
- `core/`: gate, kết nối service, adapter tương thích và UI thực sự dùng chung.
- `features/<id>/`: hook, tìm target, cấu hình, logic và tài nguyên riêng của module đó.
- Không cho feature phụ thuộc `app` hoặc feature khác; không đưa tên lớp obfuscated, điều kiện của một app hay khóa cài đặt riêng vào core.
- Phần dùng chung chỉ đưa vào core khi có nhu cầu sử dụng thực tế. Không tạo framework/plugin loader mới chỉ để dự phòng.
- Giữ namespace upstream của các feature hiện có. Tên hiển thị có thể đổi độc lập, ví dụ Better Messenger vẫn dùng namespace OrcaPeek.
- Với chức năng mới: tách tìm target, cài hook, logic quyết định và cấu hình khi chúng đủ phức tạp. Entry chỉ điều phối; Activity chỉ hiển thị/lưu cấu hình, không cài hook.
- Mã cũ được chỉnh dần tại phần đang làm; không ép mọi source upstream về cùng ngôn ngữ hoặc cùng thư mục trong một lần.

## Bản đồ mã nguồn

| Phần | Điểm bắt đầu |
| --- | --- |
| Danh sách và điều hướng | `app/src/main/java/dev/ohmy/lsposed/ModuleCatalog.java`, `MainActivity.java` |
| Gate và công tắc tổng | `core/src/main/java/dev/ohmy/core/GatedModule.java`, `ModuleIds.java` |
| Kết nối LSPosed của UI | `core/src/main/java/dev/ohmy/core/ModuleApplication.java` |
| Công tắc dùng chung | `core/src/main/java/dev/ohmy/core/ui/ModuleSwitch.java` |
| Better Zalo | `features/zalo/`; cấu hình `settings/ZaloOptions.java`; UI hiện tại ở `app/.../ZaloSettingsActivity.java` |
| Facebook | `features/facebook/`; `core/Settings.kt`, `ui/Toggles.kt`, `ui/MainActivity.kt` |
| Locket, TikTok, Messenger, Chrome | Thư mục tương ứng trong `features/` |
| Entry, scope, thuộc tính LSPosed | `app/src/main/resources/META-INF/xposed/` |

## Hợp đồng hook và cài đặt

- Dự án dùng modern LibXposed API **102**. API hook là `compileOnly`; không đóng gói API Xposed, legacy runtime hoặc runtime Yuki vào APK. Thư viện **service** là dependency runtime riêng, không nhầm với API hook.
- Entry của mỗi module kế thừa `GatedModule`, giữ lọc package/tiến trình chính và gate fail-closed. Công tắc tổng có hiệu lực sau khi tiến trình mục tiêu khởi động lại.
- Không dùng UI label làm ID, khóa preference hay điều kiện dispatch. Dùng ID ổn định, enum hoặc kiểu dữ liệu rõ ràng.
- `ohmy_modules` chỉ chứa `<id>.enabled`. Cấu hình từng feature nằm trong nhóm riêng; hiện tại Zalo là `better_zalo_settings`, Facebook là `fbar_settings`.
- UI quản lý dùng remote preferences qua `ModuleApplication.ServiceObserver`; đăng ký/hủy theo vòng đời và xử lý cả service đã kết nối sẵn lẫn mất kết nối. Không tạo kết nối service riêng cho từng Activity.
- Không cho hook đọc SharedPreferences riêng của APK quản lý. Giữ cơ chế dữ liệu trong host của TikTok/Chrome nếu chức năng đó đang dùng nó.
- Giữ khóa, kiểu dữ liệu và mặc định cũ. Nếu buộc đổi, viết migration có thể chạy lại an toàn và kiểm tra trạng thái người dùng đã lưu.
- Mỗi chức năng tùy chọn mới có khóa ổn định, mặc định rõ và gate riêng. Tắt chức năng phải ngừng tác động của nó, không chỉ đổi trạng thái UI.
- Tìm target obfuscated bằng dấu hiệu cấu trúc/DexKit phù hợp. Kiểm tra chữ ký và độ duy nhất; không chọn tùy tiện `firstOrNull()` để che kết quả mơ hồ.
- Không tìm thấy target thì bỏ qua chức năng đó và ghi log có tên feature/chức năng. Gate chỉ cô lập khởi tạo; callback runtime vẫn cần xử lý lỗi và giữ đường gọi original đúng.
- Cache tìm target phải phân biệt phiên bản host; tránh scan DEX trong callback chạy thường xuyên. Không ghi token, cookie hoặc dữ liệu phiên vào log.

## Quy ước giao diện

- Nền trắng, chữ tối, xanh dương `#2563EB`; tham chiếu màn hình Better Zalo và Facebook hiện tại. Lề ngang 24dp, tiêu đề khoảng 25sp, nhãn 16sp, chữ phụ 13sp, vùng chạm tối thiểu 48dp.
- Dùng `core.ui.ModuleSwitch` cho công tắc trong app quản lý. Không tạo thêm bản sao hoặc đổi về switch native/Material cho từng màn hình.
- Settings rời mở bằng vùng tên/icon/dấu `>`; công tắc là vùng thao tác độc lập. Settings nhúng trong app mục tiêu phải ở nguyên đó; vùng tên trong manager hiện mô tả gốc LSPosed.
- Không thêm dòng liên kết phụ kiểu “Cài đặt”, “Tùy chỉnh trong Zalo” hoặc hướng dẫn dài vào danh sách chính. Icon lấy từ package đích, có fallback khi chưa cài app.
- Màn hình settings mới nên nằm trong feature và khai báo Activity không exported. `ZaloSettingsActivity` đang ở app là cấu trúc hiện có; không di chuyển chỉ vì làm việc khác.
- Prefix tài nguyên theo feature (`facebook_`, `zalo_`, ...), dùng resource cho nhãn mới có thể dịch. Giữ đủ bản Việt hóa TikTok và tên Better Messenger.

## Thao tác và kiểm chứng

- Tìm trong source bằng `rg`, loại `**/build/**`, `.gradle/`, `dist/`, `.local-artifacts/` khi cần. Không sửa file sinh ra trong `build/`.
- Không chạy lại `import_sources.py`, `adapt_imports.py`, `namespace_resources.py` lên cây đang phát triển. Chúng là công cụ nhập source ban đầu, có thể ghi đè phần tích hợp.
- Không sửa các thư mục source gốc ngoài workspace. Giữ thông tin nguồn/license trong `features/<id>/upstream/`.
- Build dùng JDK 21, SDK 36, Build Tools 35; xem `scripts/build.ps1`. `local.properties` là cấu hình máy, không ghi đường dẫn máy cá nhân vào mã nguồn.
- Kiểm tra theo thay đổi: test feature khi sửa logic; thêm test cho migration/gate/target matching có rủi ro; test core và các nơi dùng khi sửa phần chung. Không viết test chỉ để kiểm tra câu chữ hoặc bản sao implementation.
- Khi giao APK: chạy `./scripts/build.ps1` (assemble, test, lint, audit). Có thể chạy Gradle có chọn lọc trong lúc sửa. Nếu cần chỉ định JDK trực tiếp, truyền `-Dorg.gradle.java.home=<JDK21>`; chỉ đặt JAVA_HOME có thể chưa đủ do cấu hình Gradle trên máy.
- Đổi UI cần xem screenshot trên thiết bị/emulator; build thành công không thay thế kiểm tra giao diện. Phân biệt kiểm tra UI manager với kiểm tra hook trong host.
- Giữ trạng thái cài đặt người dùng khi test; nếu cần đổi tạm thì ghi nhận và khôi phục. Không tự nhập/xuất phiên đăng nhập, gửi nội dung hoặc thay dữ liệu host để kiểm tra UI.
- Không hardcode serial/IP thiết bị vào script. Khi test hook, ghi rõ phiên bản host/Android, tình huống đã thử và phần chưa kiểm chứng.
- Thay đổi tài liệu thuần túy không cần build, tăng version hay cài APK. Khi thực sự phát hành APK mới, tăng version trong `app/build.gradle.kts`, lưu bản có phiên bản trong `dist/` và kết quả trong `verification/RESULTS.md`.
- Cập nhật `docs/ARCHITECTURE.md` khi đổi hợp đồng kiến trúc; tài liệu feature khi đổi hành vi. Kết luận nêu thay đổi, kiểm chứng và giới hạn thực tế, không tuyên bố hỗ trợ host chưa thử.
