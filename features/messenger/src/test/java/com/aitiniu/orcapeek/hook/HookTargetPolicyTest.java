package com.aitiniu.orcapeek.hook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HookTargetPolicyTest {
    @Test
    public void targetPackage_isExact() {
        assertTrue(HookTargetPolicy.isTargetPackage("com.facebook.orca"));
        assertFalse(HookTargetPolicy.isTargetPackage("com.facebook.katana"));
        assertFalse(HookTargetPolicy.isTargetPackage(null));
    }
}
