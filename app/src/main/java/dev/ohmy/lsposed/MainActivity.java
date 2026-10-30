package dev.ohmy.lsposed;

import dev.ohmy.core.ui.ModuleSwitch;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import dev.ohmy.core.ModuleApplication;
import dev.ohmy.core.ModuleIds;
import io.github.libxposed.service.XposedService;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends AppCompatActivity implements ModuleApplication.ServiceObserver {
    private static final int INK = Color.rgb(27, 34, 48);
    private static final int MUTED = Color.rgb(113, 122, 137);
    private static final int ACCENT = Color.rgb(37, 99, 235);
    private final List<ModuleSwitch> switches = new ArrayList<>();
    private SharedPreferences prefs;
    private TextView status;
    private TextView count;
    private boolean syncing;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout content = column();
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(content);

        LinearLayout brand = row();
        android.widget.ImageView mark = new android.widget.ImageView(this);
        mark.setImageResource(R.drawable.ic_ohmy);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView name = text("Oh My LSPosed", 23, INK, true);
        LinearLayout.LayoutParams nameLayout = new LinearLayout.LayoutParams(0, -2, 1);
        nameLayout.leftMargin = dp(10);
        brand.addView(name, nameLayout);
        content.addView(brand);

        LinearLayout connection = row();
        connection.setPadding(dp(16), dp(8), dp(12), dp(8));
        connection.setBackground(surface(Color.rgb(246, 248, 251), 16));
        status = text("Đang kết nối LSPosed…", 15, INK, true);
        connection.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        TextView manager = action("Mở  ↗", this::openManager);
        manager.setContentDescription("Mở LSPosed");
        manager.setPadding(dp(12), 0, dp(4), 0);
        connection.addView(manager);
        add(content, connection, 22);

        LinearLayout heading = row();
        heading.addView(text("Module", 20, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        count = text("", 13, MUTED, false);
        heading.addView(count);
        add(content, heading, 24);

        for (ModuleCatalog.Feature feature : ModuleCatalog.ALL) {
            LinearLayout titleRow = row();
            titleRow.setPadding(0, dp(12), 0, dp(12));
            LinearLayout entry = row();
            entry.setMinimumHeight(dp(64));
            ripple(entry);
            entry.setFocusable(true);
            entry.setContentDescription(feature.name() + ", " + feature.edition());
            entry.setOnClickListener(view -> openFeature(feature));
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(targetIcon(feature));
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            entry.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));
            LinearLayout identity = column();
            identity.addView(text(feature.name(), 17, INK, true));
            TextView sub = text(feature.edition(), 13, MUTED, false);
            sub.setPadding(0, dp(4), 0, 0);
            identity.addView(sub);
            LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(0, -2, 1);
            identityParams.leftMargin = dp(14);
            entry.addView(identity, identityParams);
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.ic_chevron_right);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            arrowParams.leftMargin = dp(8);
            arrowParams.rightMargin = dp(10);
            entry.addView(chevron, arrowParams);
            titleRow.addView(entry, new LinearLayout.LayoutParams(0, -2, 1));
            ModuleSwitch toggle = new ModuleSwitch(this);
            toggle.setContentDescription("Bật " + feature.name());
            toggle.setMinHeight(dp(48));
            toggle.setMinWidth(dp(56));
            toggle.setEnabled(false);
            toggle.setTag(feature);
            toggle.setOnCheckedChangeListener((button, checked) -> saveToggle(toggle, feature, checked));
            switches.add(toggle);
            titleRow.addView(toggle);
            content.addView(titleRow);
            if (!feature.id().equals("chrome")) {
                View divider = new View(this);
                divider.setBackgroundColor(Color.rgb(239, 242, 246));
                LinearLayout.LayoutParams rule = new LinearLayout.LayoutParams(-1, dp(1));
                rule.leftMargin = dp(58);
                content.addView(divider, rule);
            }
        }
        setContentView(scroll);
        updateService(ModuleApplication.getService());
    }

    @Override protected void onStart() { super.onStart(); ModuleApplication.addObserver(this); }
    @Override protected void onStop() { ModuleApplication.removeObserver(this); super.onStop(); }
    @Override public void onXposedServiceChanged(XposedService service) { runOnUiThread(() -> updateService(service)); }

    private void updateService(XposedService service) {
        prefs = null;
        if (service != null) {
            try {
                if (service.getApiVersion() >= 102) {
                    prefs = service.getRemotePreferences(ModuleIds.SETTINGS);
                    status.setText(getString(R.string.ohmy_framework, service.getFrameworkName(), service.getApiVersion()));
                    status.setTextColor(ACCENT);
                } else {
                    status.setText(R.string.ohmy_api_required);
                }
            } catch (RuntimeException error) { status.setText(R.string.ohmy_read_failed); }
        } else { status.setText(R.string.ohmy_disconnected); status.setTextColor(MUTED); }
        syncing = true;
        int enabled = 0;
        for (ModuleSwitch toggle : switches) {
            ModuleCatalog.Feature feature = (ModuleCatalog.Feature) toggle.getTag();
            boolean checked = prefs != null && prefs.getBoolean(ModuleIds.enabledKey(feature.id()), false);
            toggle.setChecked(checked);
            toggle.setEnabled(prefs != null);
            if (checked) enabled++;
        }
        syncing = false;
        count.setText(getString(R.string.ohmy_count, enabled, ModuleCatalog.ALL.size()));
    }

    private void saveToggle(ModuleSwitch toggle, ModuleCatalog.Feature feature, boolean checked) {
        if (syncing || prefs == null) return;
        try {
            boolean saved = prefs.edit().putBoolean(ModuleIds.enabledKey(feature.id()), checked).commit();
            if (!saved) throw new IllegalStateException("Preference commit failed");
            int enabled = 0;
            for (ModuleSwitch control : switches) if (control.isChecked()) enabled++;
            count.setText(getString(R.string.ohmy_count, enabled, switches.size()));
        } catch (RuntimeException error) {
            syncing = true;
            toggle.setChecked(!checked);
            syncing = false;
            Toast.makeText(this, "Chưa lưu được. Hãy kiểm tra kết nối LSPosed.", Toast.LENGTH_LONG).show();
        }
    }

    private void openFeature(ModuleCatalog.Feature feature) {
        if (feature.settings() == ModuleCatalog.Settings.ZALO) {
            startActivity(new Intent(this, ZaloSettingsActivity.class));
            return;
        }
        if (feature.settings() == ModuleCatalog.Settings.FACEBOOK) {
            startActivity(new Intent(this, tn.loukious.facebookappadsremover.ui.MainActivity.class));
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setBackground(surface(Color.WHITE, 24))
                .setTitle(feature.edition())
                .setMessage(feature.description())
                .setPositiveButton("Đóng", null)
                .show();
    }

    private Drawable targetIcon(ModuleCatalog.Feature feature) {
        for (String pkg : feature.packages()) {
            try { return getPackageManager().getApplicationIcon(pkg); }
            catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
        }
        return getPackageManager().getDefaultActivityIcon();
    }

    private void openManager() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("org.lsposed.manager");
        if (launch == null) {
            Toast.makeText(this, "Mở LSPosed từ thông báo hoặc lối tắt hệ thống.", Toast.LENGTH_LONG).show();
        } else { try { startActivity(launch); } catch (RuntimeException ignored) { Toast.makeText(this, "Không mở được LSPosed", Toast.LENGTH_SHORT).show(); } }
    }

    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
    private TextView action(String label, Runnable callback) {
        TextView v = text(label, 14, ACCENT, false);
        v.setGravity(Gravity.CENTER_VERTICAL); v.setMinHeight(dp(48));
        ripple(v);
        v.setOnClickListener(view -> callback.run());
        v.setFocusable(true);
        return v;
    }
    private void ripple(View view) {
        android.util.TypedValue out = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true);
        view.setBackgroundResource(out.resourceId);
    }
    private GradientDrawable surface(int color, int radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg; }
    private void add(LinearLayout parent, View child, int top) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(child, p); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
