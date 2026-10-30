package dev.ohmy.core;

public final class ModuleIds {
    public static final String SETTINGS = "ohmy_modules";
    public static final String APP_ID = "dev.ohmy.lsposed";
    private ModuleIds() { }
    public static String enabledKey(String id) { return id + ".enabled"; }
}
