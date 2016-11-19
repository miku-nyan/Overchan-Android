package nya.miku.wishmaster.ui.settings;

public class ImportExportConstants {
    public static final String JSON_KEY_VERSION = "version";
    public static final String JSON_KEY_HISTORY = "history";
    public static final String JSON_KEY_FAVORITES = "favorites";
    public static final String JSON_KEY_HIDDEN = "hidden";
    public static final String JSON_KEY_SUBSCRIPTIONS = "subscriptions";
    public static final String JSON_KEY_PREFERENCES = "preferences";
    public static final String JSON_KEY_TABS = "tabs";
    public static final String[] exclude = {
            "PREF_KEY_CLOUDFLARE_COOKIE",
            "PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN",
            "PREF_KEY_USE_PROXY",
            "PREF_KEY_PROXY_HOST",
            "PREF_KEY_PROXY_PORT",
            "PREF_KEY_PASSWORD",
            "PREF_KEY_USE_HTTPS",
            "PREF_KEY_ONLY_NEW_POSTS",
            "PREF_KEY_CAPTCHA_AUTO_UPDATE",
            "PREF_KEY_CACHE_MAXSIZE",
            "PREF_KEY_SETTINGS_IMPORT_OVERWRITE",
    };
}
