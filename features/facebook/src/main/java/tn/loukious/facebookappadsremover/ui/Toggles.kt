package tn.loukious.facebookappadsremover.ui

import tn.loukious.facebookappadsremover.core.Settings

/**
 * The full toggle surface, in display order. Keys and defaults must stay in
 * sync with core.Settings (which documents the original-mod pref each one
 * mirrors). Subtitles record the hook-level effect, so the cost of each
 * switch is visible where it's flipped.
 *
 * A section may also carry non-toggle controls, declared through [extra]. That
 * is a typed field rather than a match on [ToggleSection.title] because the
 * title is display text: the session buttons were dispatched on the literal
 * "Account", and when no such section existed the code silently never ran.
 */
data class ToggleSpec(
    val key: String,
    val title: String,
    val subtitle: String,
    val default: Boolean,
)

/**
 * Non-toggle controls rendered inside a section, below its switches. [NONE] is
 * the common case: a section that is only switches.
 */
enum class SectionExtra {
    NONE,

    /** Free-text keyword list for the feed filter. */
    KEYWORDS,

    /** Session export/import buttons. */
    SESSION,

    /**
     * Launcher-icon switch. Module-local rather than a hook toggle — its value
     * lives in PackageManager's component state, so it works without the
     * framework service.
     */
    LAUNCHER,
}

data class ToggleSection(
    val title: String,
    val toggles: List<ToggleSpec>,
    val extra: SectionExtra = SectionExtra.NONE,
)

val TOGGLE_SECTIONS: List<ToggleSection> = listOf(
    ToggleSection("Quảng cáo", listOf(
        ToggleSpec(
            Settings.ADS_NEWS_FEED,
            "Chặn quảng cáo bảng tin", "Chặn bài được tài trợ. Bật thêm bảo vệ CSR để lọc quảng cáo đã lưu đệm.",
            true,
        ),
        ToggleSpec(
            Settings.ADS_STORIES,
            "Chặn quảng cáo Story", "Ẩn quảng cáo giữa các tin và trong trình xem tin.",
            true,
        ),
        ToggleSpec(
            Settings.ADS_REELS,
            "Chặn quảng cáo Reels", "Chặn quảng cáo Reels. Quảng cáo video dùng chung cần bật chặn quảng cáo Story.",
            true,
        ),
        ToggleSpec(
            Settings.ADS_MARKETPLACE,
            "Chặn quảng cáo Marketplace", "Ẩn mục được tài trợ, tin được đẩy và quảng cáo video.",
            true,
        ),
        ToggleSpec(
            Settings.ADS_GAME_ADS,
            "Chặn quảng cáo trò chơi", "Chặn quảng cáo toàn màn hình, biểu ngữ và quảng cáo nhận thưởng; vẫn trả kết quả nhận thưởng thành công.",
            true,
        ),
        ToggleSpec(
            Settings.ADS_FEED_GUARD,
            "Bảo vệ bảng tin bằng CSR", "Lọc thêm quảng cáo lưu đệm khi bật chặn quảng cáo bảng tin. Không ảnh hưởng bộ lọc AI và từ khóa.",
            true,
        ),
    )),
    ToggleSection("Bộ lọc bảng tin", listOf(
        ToggleSpec(Settings.FEED_THREADS, "Ẩn bài viết Threads", "Ẩn bài quảng bá Threads trên bảng tin.", false),
        ToggleSpec(Settings.FEED_REELS, "Ẩn Reels", "Ẩn mục Reels trên bảng tin.", false),
        ToggleSpec(
            Settings.FEED_SUGGESTIONS,
            "Ẩn nội dung đề xuất", "Có thể khiến bảng tin tải ít bài nếu cả nhóm bài đều là đề xuất.",
            false,
        ),
        ToggleSpec(Settings.FEED_PYMK, "Ẩn gợi ý kết bạn", "Ẩn mục Những người bạn có thể biết.", false),
        ToggleSpec(Settings.FEED_STORIES, "Ẩn tin trên bảng tin", "Ẩn khay tin và phần hiển thị tin trên bảng tin.", false),
        ToggleSpec(
            Settings.FEED_AI_CONTENT,
            "Ẩn nội dung tạo bằng AI", "Lọc bài có nhãn AI do người đăng hoặc Meta đánh dấu.",
            false,
        ),
        ToggleSpec(
            Settings.FEED_KEYWORDS_ENABLED,
            "Lọc theo từ khóa", "Ẩn bài chứa từ khóa bên dưới, không phân biệt chữ hoa và chữ thường.",
            false,
        ),
        ToggleSpec(
            Settings.FEED_AUTO_REFRESH_BLOCK,
            "Chặn tự làm mới bảng tin", "Ngăn tải lại khi mở hoặc quay lại Facebook. Đang thử nghiệm.",
            false,
        ),
    ), SectionExtra.KEYWORDS),
    ToggleSection("Tin", listOf(
        ToggleSpec(Settings.STORIES_HIDE_SEEN, "Xem tin không báo đã xem", "Ngăn gửi trạng thái đã xem tin.", false),
        ToggleSpec(
            Settings.STORIES_HIDE_TRAY,
            "Ẩn khay tin", "Loại bỏ thẻ tin khỏi danh sách bảng tin.",
            false,
        ),
    )),
    ToggleSection("Giao diện", listOf(
        ToggleSpec(Settings.APPEARANCE_DARK, "Buộc dùng chế độ tối", "Luôn bật giao diện tối trong Facebook.", false),
        ToggleSpec(
            Settings.APPEARANCE_AMOLED,
            "Nền đen AMOLED", "Dùng nền đen thuần. Cần bật chế độ tối trong Facebook hoặc tùy chọn bên trên.",
            false,
        ),
    )),
    ToggleSection("Quyền riêng tư", listOf(
        ToggleSpec(Settings.PRIVACY_ALLOW_CAPTURE, "Cho phép chụp và quay màn hình", "Cho phép ghi lại màn hình bị Facebook hạn chế.", false),
        ToggleSpec(Settings.PRIVACY_BLOCK_DETECTION, "Chặn phát hiện chụp màn hình", "Chặn bộ theo dõi chụp và quay màn hình của Facebook.", false),
    )),
    ToggleSection("Thanh điều hướng", listOf(
        ToggleSpec(
            Settings.NAV_HIDE_REELS_TAB,
            "Ẩn tab Reels", "Vẫn mở được Reels từ liên kết và menu.",
            false,
        ),
        ToggleSpec(
            Settings.NAV_HIDE_MARKETPLACE_TAB,
            "Ẩn tab Marketplace", "Vẫn truy cập được Marketplace từ các vị trí khác.",
            false,
        ),
        ToggleSpec(
            Settings.NAV_HIDE_GAMES_TAB,
            "Ẩn tab trò chơi", "Vẫn mở được trò chơi từ menu và liên kết.",
            false,
        ),
        ToggleSpec(
            Settings.NAVIGATION_ACTIVITY_LIST,
            "Ghi nhật ký màn hình", "Ghi Activity, action, URI và extras để kiểm tra lỗi.",
            false,
        ),
    )),
    ToggleSection("Liên kết", listOf(
        ToggleSpec(
            Settings.LINKS_EXTERNAL_BROWSER,
            "Mở bằng trình duyệt ngoài", "Liên kết web mở bằng trình duyệt mặc định; trang Facebook và đăng nhập vẫn mở trong app.",
            true,
        ),
        ToggleSpec(
            Settings.LINKS_CLEAN_URL,
            "Bỏ chuyển hướng liên kết", "Mở thẳng đích đến của liên kết l.php, bỏ tham số theo dõi fbclid.",
            false,
        ),
    )),
    ToggleSection("Tải xuống", listOf(
        ToggleSpec(Settings.DOWNLOAD_USE_BROWSER, "Tải bằng trình duyệt", "Chuyển liên kết media sang trình duyệt để tải xuống.", true),
        ToggleSpec(
            Settings.DOWNLOAD_SHOW_ICON,
            "Hiện thao tác tải xuống", "Hiện Tải xuống cho Reels và Lưu cho tin đang mở.",
            true,
        ),
        ToggleSpec(
            Settings.DOWNLOAD_CLIPBOARD,
            "Tải nhanh từ liên kết đã chép", "Mở lựa chọn chất lượng và tải nhanh khi sao chép liên kết web.",
            false,
        ),
    )),
    ToggleSection("Video", listOf(
        ToggleSpec(Settings.VIDEO_RESUME, "Tiếp tục vị trí video", "Khôi phục vị trí một lần khi mở lại; luôn ưu tiên thao tác tua của bạn.", false),
        ToggleSpec(Settings.VIDEO_BACKGROUND, "Phát video trong nền", "Tiếp tục phát khi rời app, không mở cửa sổ nổi.", false),
    )),
    // No toggles of its own: the export/import buttons are the whole section.
    ToggleSection("Tài khoản", emptyList(), SectionExtra.SESSION),
    // Module-local rather than a hook toggle — see SectionExtra.LAUNCHER.
    ToggleSection("Module", emptyList(), SectionExtra.LAUNCHER),
)
