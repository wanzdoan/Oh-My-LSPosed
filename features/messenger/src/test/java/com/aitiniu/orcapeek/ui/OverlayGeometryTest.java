package com.aitiniu.orcapeek.ui;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OverlayGeometryTest {
    @Test
    public void clampTop_keepsCropInsideDecor() {
        assertEquals(0, OverlayGeometry.clampTop(-20, 1000));
        assertEquals(400, OverlayGeometry.clampTop(400, 1000));
        assertEquals(999, OverlayGeometry.clampTop(1200, 1000));
    }

    @Test
    public void clampHeight_usesFallbackAndAvailableSpace() {
        assertEquals(76, OverlayGeometry.clampHeight(0, 100, 1000, 76));
        assertEquals(40, OverlayGeometry.clampHeight(200, 960, 1000, 76));
        assertEquals(1, OverlayGeometry.clampHeight(50, 999, 1000, 76));
    }
}
