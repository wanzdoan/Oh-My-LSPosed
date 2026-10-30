package com.aitiniu.orcapeek.ui;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import static org.junit.Assert.*;

public final class MediaPolicyTest {
    @Test public void allowsOnlyHttpsCdnWithoutCredentialsOrCustomPort() throws Exception {
        assertTrue(MediaPolicy.isAllowed(new URL("https://scontent.xx.fbcdn.net/image")));
        assertTrue(MediaPolicy.isAllowed(new URL("https://fbcdn.net:443/image")));
        for (String url : new String[]{"http://fbcdn.net/image",
                "https://fbcdn.net.attacker.test/image", "https://evilfbcdn.net/image",
                "https://user:secret@fbcdn.net/image", "https://fbcdn.net:8443/image",
                "https://127.0.0.1/image"}) {
            assertFalse(url, MediaPolicy.isAllowed(new URL(url)));
        }
    }

    @Test public void readsUnknownLengthAndExactLimit() throws Exception {
        assertArrayEquals(new byte[]{1, 2, 3}, MediaPolicy.readBounded(
                new ByteArrayInputStream(new byte[]{1, 2, 3}), -1));
        assertEquals(MediaPolicy.MAX_BYTES, MediaPolicy.readBounded(
                new ByteArrayInputStream(new byte[MediaPolicy.MAX_BYTES]), -1).length);
    }

    @Test(expected = IOException.class) public void rejectsOversizedChunkedResponse() throws Exception {
        MediaPolicy.readBounded(new ByteArrayInputStream(new byte[MediaPolicy.MAX_BYTES + 1]), -1);
    }

    @Test(expected = IOException.class) public void rejectsOversizedDeclaredLength() throws Exception {
        MediaPolicy.readBounded(new ByteArrayInputStream(new byte[0]), Integer.MAX_VALUE);
    }
}
