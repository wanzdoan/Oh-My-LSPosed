package dev.ohmy.lsposed;

import dev.ohmy.core.ui.ModuleSwitch;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.betterzl.module.settings.ZaloOptions;
import dev.ohmy.core.ModuleApplication;
import io.github.libxposed.service.XposedService;
import java.util.ArrayList;
import java.util.List;

public final class ZaloSettingsActivity extends AppCompatActivity implements ModuleApplication.ServiceObserver {
    private record Option(String key, String title, boolean fallback) { }
    private static final int INK = Color.rgb(27, 34, 48);
    private static final int MUTED = Color.rgb(113, 122, 137);
    private static final Option[] OPTIONS = {
            new Option(ZaloOptions.GLASS, "LiquidGlass Bar", true),
            new Option(ZaloOptions.SEEN, "Ẩn trạng thái đã xem", true),
            new Option(ZaloOptions.TYPING, "Ẩn trạng thái đang nhập", true),
            new Option(ZaloOptions.RINGTONE, "Cho phép thay đổi nhạc chuông", true),
            new Option(ZaloOptions.RECALL, "Chống thu hồi tin nhắn", true),
            new Option(ZaloOptions.ORIGINAL, "Gửi ảnh chất lượng gốc", false)
    };
    private final List<ModuleSwitch> controls = new ArrayList<>();
    private SharedPreferences prefs;
    private TextView connection;
    private boolean syncing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(12), dp(24), dp(24));
        scroll.addView(content);
        TextView back = text("‹  Module", 15, Color.rgb(37, 99, 235), false);
        back.setMinHeight(dp(48)); back.setGravity(Gravity.CENTER_VERTICAL);
        ripple(back); back.setOnClickListener(v -> finish()); back.setFocusable(true);
        content.addView(back);
        TextView title = text("Better Zalo", 25, INK, true);
        title.setPadding(0, dp(16), 0, dp(8)); content.addView(title);
        TextView hint = text("Khởi động lại Zalo để áp dụng thay đổi.", 13, MUTED, false);
        content.addView(hint);
        connection = text("Đang kết nối LSPosed…", 13, MUTED, false);
        connection.setPadding(0, dp(8), 0, dp(20)); content.addView(connection);

        for (Option option : OPTIONS) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(12), 0, dp(12));
            TextView label = text(option.title(), 16, INK, false);
            label.setPadding(0, 0, dp(12), 0);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            ModuleSwitch toggle = new ModuleSwitch(this);
            toggle.setTag(option); toggle.setContentDescription(option.title()); toggle.setEnabled(false);
            toggle.setOnCheckedChangeListener((button, checked) -> save(toggle, option, checked));
            controls.add(toggle); row.addView(toggle);
            row.setMinimumHeight(dp(72));
            row.setOnClickListener(v -> { if (toggle.isEnabled()) toggle.toggle(); });
            ripple(row); content.addView(row);
            if (!option.key().equals(ZaloOptions.ORIGINAL)) {
                View divider = new View(this); divider.setBackgroundColor(Color.rgb(239, 242, 246));
                content.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        TextView note = text("Ảnh gốc: chọn Original trong bảng Chọn chế độ gửi.\nNhạc chuông: chọn tệp trong cài đặt cuộc gọi của Zalo.", 13, MUTED, false);
        note.setPadding(0, dp(20), 0, 0); note.setLineSpacing(dp(4), 1); content.addView(note);
        setContentView(scroll);
    }

    @Override protected void onStart() { super.onStart(); ModuleApplication.addObserver(this); }
    @Override protected void onStop() { ModuleApplication.removeObserver(this); super.onStop(); }
    @Override public void onXposedServiceChanged(XposedService service) {
        runOnUiThread(() -> {
            prefs = null;
            try { if (service != null && service.getApiVersion() >= 102) prefs = service.getRemotePreferences(ZaloOptions.GROUP); }
            catch (RuntimeException ignored) { }
            connection.setText(prefs == null ? "Chưa kết nối LSPosed" : "Đã kết nối LSPosed");
            syncing = true;
            for (ModuleSwitch toggle : controls) {
                Option option = (Option) toggle.getTag();
                toggle.setChecked(prefs != null && prefs.getBoolean(option.key(), option.fallback()));
                toggle.setEnabled(prefs != null);
            }
            syncing = false;
        });
    }

    private void save(ModuleSwitch toggle, Option option, boolean checked) {
        if (syncing || prefs == null) return;
        try {
            if (!prefs.edit().putBoolean(option.key(), checked).commit()) throw new IllegalStateException("Commit failed");
        } catch (RuntimeException error) {
            syncing = true; toggle.setChecked(!checked); syncing = false;
            Toast.makeText(this, "Chưa lưu được cài đặt. Hãy kiểm tra LSPosed.", Toast.LENGTH_SHORT).show();
        }
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return text;
    }
    private void ripple(View view) {
        android.util.TypedValue out = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        view.setBackgroundResource(out.resourceId);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
