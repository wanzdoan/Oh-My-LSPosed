package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import org.json.JSONObject;

import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Greasy Fork/Sleazy Fork discovery plus a hash-based install handoff. */
final class ForkSupport {
    private static final Pattern SCRIPT_ID = Pattern.compile("/scripts/(\\d+)(?:[-/]|$)");
    private static final Pattern HREF = Pattern.compile("(?is)href\\s*=\\s*([\"'])(.*?)\\1");
    private static final Map<Activity, String> LAST_PROMPT = new WeakHashMap<>();

    private ForkSupport() { }

    static boolean isInstallablePage(String url) {
        return ViolentmonkeyCompat.isDirectUserscriptUrl(url)
                || ViolentmonkeyCompat.isForkScriptPage(url)
                || ViolentmonkeyCompat.hasInstallMarker(url);
    }

    static String installUrlFromMarker(String url) {
        return ViolentmonkeyCompat.installUrlFromMarker(url);
    }

    static void openSite(Activity activity, String host) {
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://" + host + "/en/scripts"));
        intent.setPackage("com.android.chrome");
        activity.startActivity(intent);
    }

    static String resolveInstallUrl(String pageUrl) throws Exception {
        String marked = installUrlFromMarker(pageUrl);
        if (marked != null) return marked;
        if (ViolentmonkeyCompat.isDirectUserscriptUrl(pageUrl)) return pageUrl;
        if (!ViolentmonkeyCompat.isForkScriptPage(pageUrl)) {
            throw new Exception("This is not a supported userscript page");
        }

        String api = apiUrl(pageUrl);
        if (api != null) {
            try {
                String codeUrl = new JSONObject(MonkeyStore.fetch(api))
                        .optString("code_url", "").trim();
                if (ViolentmonkeyCompat.isDirectUserscriptUrl(codeUrl)) return codeUrl;
            } catch (Throwable ignored) { }
        }

        String html = MonkeyStore.fetch(pageUrl);
        Matcher links = HREF.matcher(html);
        while (links.find()) {
            String value = htmlDecode(links.group(2));
            try {
                String resolved = new URL(new URL(pageUrl), value).toString();
                if (ViolentmonkeyCompat.isDirectUserscriptUrl(resolved)) return resolved;
            } catch (Throwable ignored) { }
        }
        return ViolentmonkeyCompat.fallbackForkInstallUrl(pageUrl);
    }

    static void injectInstallClickBridge(ChromeBridge.Page page) {
        if (page == null || page.incognito || !isForkPage(page.url)) return;
        String payload = "(function(){"
                + "if(window.__ChromeScriptInstallBridge)return;window.__ChromeScriptInstallBridge=true;"
                + "const direct=u=>/\\.user\\.(?:js|css)(?:[?#]|$)/i.test(u||'');"
                + "document.addEventListener('click',e=>{const a=e.target&&e.target.closest?"
                + "e.target.closest('a[href],[data-code-url]'):null;if(!a)return;let u='';"
                + "try{u=new URL(a.href||(a.dataset&&a.dataset.codeUrl)||'',location.href).href}catch(x){}"
                + "if(!direct(u))return;e.preventDefault();e.stopPropagation();"
                + "history.replaceState(history.state,'',location.pathname+location.search"
                + "+'#monkeyscript-install='+encodeURIComponent(u));},true);})();";
        ChromeBridge.exec(page, payload);
    }

    static boolean consumeInstallMarker(Activity activity, ChromeBridge.Page page) {
        String target = page == null ? null : installUrlFromMarker(page.url);
        if (target == null) return false;
        synchronized (LAST_PROMPT) {
            if (target.equals(LAST_PROMPT.get(activity))) return false;
            LAST_PROMPT.put(activity, target);
        }
        ChromeBridge.exec(page,
                "try{history.replaceState(history.state,'',location.pathname+location.search)}catch(e){};");
        InstallDialog.show(activity, target);
        return true;
    }

    static void maybePromptDirectInstall(Activity activity, String url) {
        if (!ViolentmonkeyCompat.isDirectUserscriptUrl(url)) return;
        synchronized (LAST_PROMPT) {
            if (url.equals(LAST_PROMPT.get(activity))) return;
            LAST_PROMPT.put(activity, url);
        }
        InstallDialog.show(activity, url);
    }

    private static String apiUrl(String pageUrl) {
        try {
            URI uri = new URI(pageUrl);
            String host = ViolentmonkeyCompat.normalizeHost(uri.getHost());
            Matcher matcher = SCRIPT_ID.matcher(uri.getPath() == null ? "" : uri.getPath());
            if (!matcher.find()) return null;
            String apiHost = "greasyfork.org".equals(host) ? "api.greasyfork.org" : host;
            return "https://" + apiHost + "/en/scripts/" + matcher.group(1) + ".json";
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isForkPage(String url) {
        try {
            return ViolentmonkeyCompat.isForkHost(new URI(url).getHost());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String htmlDecode(String value) {
        return value.replace("&amp;", "&").replace("&#39;", "'")
                .replace("&#x27;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">");
    }
}

