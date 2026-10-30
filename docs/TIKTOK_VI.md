# Khôi phục tiếng Việt cho TikTok Patch

Bản Việt hóa có trong source đã giải biên dịch được cung cấp:

`H:/Projects/TikTok/TiktokPatchXposed-Project/Decompiled-Java-Raw/defpackage/T0.java`

Bảng `map.put("vi", ...)` có 313 chuỗi. Bảng tiếng Anh trong cùng file trùng toàn bộ 313 chuỗi, đúng thứ tự, với enum `I18n.S` và bảng tiếng Anh của source Gradle hiện tại. Vì vậy có thể phục hồi chính xác theo khóa mà không đoán thứ tự.

Đã đưa bảng `vi` vào `features/tiktok/.../I18n.java`, chuẩn hóa các từ còn để tiếng Anh trong giao diện và sửa một số nghĩa: Clear display là ẩn các nút/chú thích, không phải xóa phông; Picture in picture là Hình trong hình; streak là chuỗi trò chuyện; repost là đăng lại. Phần xem thử phông chữ có dấu tiếng Việt.

Ngôn ngữ vẫn theo ngôn ngữ app TikTok như bản gốc: locale `vi`/`vi-VN` dùng tiếng Việt. Không ép đổi ngôn ngữ TikTok hay các bảng ngôn ngữ khác. Màn hình setting vẫn nằm trong TikTok.

Test kiểm tra mọi bảng đủ khóa, không rỗng; locale vi-VN chọn bản Việt hóa; giữ nguyên toàn bộ placeholder `%d`, `%s`, `%1$d`, `%2$d`; và các mục đã sửa nghĩa. Chi tiết đối chiếu nguồn: `verification/tiktok-vi-recovery.json`.
