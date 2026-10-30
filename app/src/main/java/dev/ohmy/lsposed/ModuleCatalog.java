package dev.ohmy.lsposed;

import java.util.List;
import java.util.Arrays;
import java.util.Collections;

/** Add a feature here and its gated entry in META-INF/xposed/java_init.list. */
public final class ModuleCatalog {
    public enum Settings { NONE, ZALO, FACEBOOK, IN_APP }
    public record Feature(String id, String name, String edition, Settings settings, int description, String... packages) { }
    public static final List<Feature> ALL = Collections.unmodifiableList(Arrays.asList(
            new Feature("zalo", "Zalo", "BetterZalo", Settings.ZALO, com.betterzl.module.R.string.zalo_xposed_description, "com.zing.zalo"),
            new Feature("locket", "Locket", "Locket Gold", Settings.NONE, com.locket.gold.R.string.locket_module_description, "com.locket.Locket"),
            new Feature("tiktok", "TikTok", "TikTok Patch", Settings.IN_APP, com.golda.patchertiktok.R.string.tiktok_module_description, "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            new Feature("facebook", "Facebook", "App Ads Remover", Settings.FACEBOOK, tn.loukious.facebookappadsremover.R.string.facebook_module_description, "com.facebook.katana"),
            new Feature("messenger", "Messenger", "Better Messenger", Settings.NONE, com.aitiniu.orcapeek.R.string.messenger_module_description, "com.facebook.orca"),
            new Feature("chrome", "Chrome", "ChromeScript", Settings.IN_APP, dev.alastorkaneki.chromescript.R.string.chrome_module_description, "com.android.chrome")
    ));
    private ModuleCatalog() { }
}
