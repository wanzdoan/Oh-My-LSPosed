package dev.alastorkaneki.chromescript;

import org.junit.Test;
import static org.junit.Assert.*;

public class UrlPatternMatcherTest {
    private Userscript adblock() {
        return UserscriptMetadataParser.parse("// ==UserScript==\n"
                + "// @name Adblock regression\n"
                + "// @match *://goctruyentranhvui*.com/*\n"
                + "// @match *://*.goctruyentranhvui*.com/*\n"
                + "// @match *://goctruyentranh*.com/*\n"
                + "// @match *://*.goctruyentranh*.com/*\n"
                + "// @run-at document-start\n"
                + "// ==/UserScript==\n", "tampermonkey_adblock.user.js", "");
    }

    @Test public void installedAdblockPatternsMatchRotatingDomains() {
        Userscript s = adblock();
        for (String url : new String[]{"https://goctruyentranhvui30.com/",
                "https://goctruyentranhvui31.com/truyen/123?chapter=2",
                "http://goctruyentranh.com/", "https://www.goctruyentranhvui30.com/",
                "https://a.b.goctruyentranh99.com/"}) {
            assertTrue(url, UrlPatternMatcher.matches(s, url));
        }
    }

    @Test public void hostGlobDoesNotMatchUnrelatedHostsOrUrlText() {
        Userscript s = adblock();
        for (String url : new String[]{"https://example.com/goctruyentranhvui30.com/",
                "https://goctruyentranhvui30.com.evil.com/",
                "https://goctruyentranhvui30.com@evil.com/",
                "https://goctruyentranhvui.evil.com/", "https://evilgoctruyentranhvui30.com/",
                "https://goctruyentranhvui30.net/", "ftp://goctruyentranhvui30.com/"}) {
            assertFalse(url, UrlPatternMatcher.matches(s, url));
        }
    }

    @Test public void standardHostPatternsKeepTheirMeaning() {
        assertTrue(UrlPatternMatcher.matchesChromePattern("https://*.example.com/*", "https://example.com/"));
        assertTrue(UrlPatternMatcher.matchesChromePattern("https://*.example.com/*", "https://a.b.example.com/"));
        assertFalse(UrlPatternMatcher.matchesChromePattern("https://example.com/*", "https://a.example.com/"));
        assertFalse(UrlPatternMatcher.matchesChromePattern("https://*.example.com/*", "https://badexample.com/"));
    }

    @Test public void excludedOrDisabledAdblockStillDoesNotRun() {
        Userscript s = adblock();
        s.excludeMatches.add("*://goctruyentranhvui*.com/private/*");
        assertFalse(UrlPatternMatcher.matches(s, "https://goctruyentranhvui30.com/private/1"));
        s.enabled = false;
        assertFalse(UrlPatternMatcher.matches(s, "https://goctruyentranhvui30.com/"));
    }
}
