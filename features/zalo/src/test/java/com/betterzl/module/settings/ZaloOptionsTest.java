package com.betterzl.module.settings;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class ZaloOptionsTest {
    @Test public void emptySettingsPreserveExistingFeaturesAndOriginalIsOptIn() {
        ZaloOptions o = ZaloOptions.from(new HashMap<>());
        assertTrue(o.liquidGlass()); assertTrue(o.hideSeen()); assertTrue(o.hideTyping());
        assertTrue(o.customRingtone()); assertTrue(o.antiRecall()); assertFalse(o.originalPhoto());
    }
    @Test public void privacySwitchesAreIndependent() {
        Map<String, Object> values = new HashMap<>();
        values.put(ZaloOptions.SEEN, false); values.put(ZaloOptions.TYPING, true);
        ZaloOptions o = ZaloOptions.from(values);
        assertFalse(o.hideSeen()); assertTrue(o.hideTyping()); assertTrue(o.liquidGlass());
    }
    @Test public void disablingEverythingIsRespected() {
        Map<String, Object> values = new HashMap<>();
        for (String key : new String[]{ZaloOptions.GLASS, ZaloOptions.SEEN, ZaloOptions.TYPING,
                ZaloOptions.RINGTONE, ZaloOptions.RECALL, ZaloOptions.ORIGINAL}) values.put(key, false);
        assertEquals(new ZaloOptions(false, false, false, false, false, false), ZaloOptions.from(values));
    }
    @Test public void originalPhotoCanBeEnabledWithoutChangingPrivacy() {
        Map<String, Object> values = new HashMap<>(); values.put(ZaloOptions.ORIGINAL, true);
        assertTrue(ZaloOptions.from(values).originalPhoto());
        assertTrue(ZaloOptions.from(values).hideSeen());
    }
}
