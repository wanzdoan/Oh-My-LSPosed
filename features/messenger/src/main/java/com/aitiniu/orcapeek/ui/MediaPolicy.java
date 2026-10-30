package com.aitiniu.orcapeek.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;

final class MediaPolicy {
    static final int MAX_BYTES = 8 * 1024 * 1024;
    private MediaPolicy() {}

    static boolean isAllowed(URL url) {
        String host = url.getHost().toLowerCase(Locale.ROOT);
        return "https".equalsIgnoreCase(url.getProtocol())
                && url.getUserInfo() == null
                && (url.getPort() == -1 || url.getPort() == 443)
                && (host.equals("fbcdn.net") || host.endsWith(".fbcdn.net"));
    }

    static byte[] readBounded(InputStream stream, int hint) throws IOException {
        if (hint > MAX_BYTES) {
            throw new IOException("Image exceeds size limit");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(
                hint > 0 ? Math.min(hint, 128 * 1024) : 8192);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (count > MAX_BYTES - output.size()) {
                throw new IOException("Image exceeds size limit");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
