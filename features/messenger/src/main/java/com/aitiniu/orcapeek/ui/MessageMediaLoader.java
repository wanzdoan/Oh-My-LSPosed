// language: Java, file: MessageMediaLoader.java, runtime: Android API 26+, target: OrcaPeek v0.10
// Exact-path / CDN image loader for Messenger attachment previews:
//   Disk: use only the attachment path, never guess images from other conversations.
//   Tier 2 — CDN:  download from preview_url via HttpURLConnection (no auth needed for fbcdn.net).
// All heavy work runs on a background thread; result is delivered on the main thread.
package com.aitiniu.orcapeek.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MessageMediaLoader {
    private static final String TAG = "OrcaPeek.MediaLoader";
    // Max decoded pixels before we subsample — keeps memory under ~8 MB per image
    private static final int MAX_PIXELS = 1200 * 900;
    // In-memory LRU cache: 6 MB (enough for ~10 thumbnail-sized bitmaps)
    private static final LruCache<String, Bitmap> MEM_CACHE = new LruCache<String, Bitmap>(6 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MessageMediaLoader() {
    }

    public interface Callback {
        void onBitmap(Bitmap bitmap);
    }

    /**
     * Load a preview image for a message attachment.
     *
     * @param context    Android context (used to locate Messenger's cache dir)
     * @param previewUrl URL from the {@code attachments.preview_url} column — may be null
     * @param maxWidthPx Maximum width to decode into (card width on screen)
     * @param callback   Called on main thread with the decoded Bitmap or null on failure
     */
    public static void load(Context context, String previewUrl,
            int maxWidthPx, Callback callback) {
        if (previewUrl == null || previewUrl.isEmpty()) {
            callback.onBitmap(null);
            return;
        }
        // Check memory cache first (main thread, zero cost)
        String cacheKey = previewUrl + "@" + maxWidthPx;
        Bitmap cached = MEM_CACHE.get(cacheKey);
        if (cached != null) {
            callback.onBitmap(cached);
            return;
        }
        Context appCtx = context.getApplicationContext();
        POOL.execute(() -> {
            Bitmap result = null;
            try {
                result = loadInternal(appCtx, previewUrl, maxWidthPx, cacheKey);
            } catch (Throwable error) {
                Log.w(TAG, "Image load failed: " + error.getClass().getSimpleName());
            }
            final Bitmap delivered = result;
            MAIN.post(() -> {
                try {
                    callback.onBitmap(delivered);
                } catch (Throwable error) {
                    Log.w(TAG, "Image rendering failed: " + error.getClass().getSimpleName());
                }
            });
        });
    }

    private static Bitmap loadInternal(Context context, String previewUrl,
            int maxWidthPx, String cacheKey) {
        // Direct local file path (persisted_path from E2EE attachments)
        if (previewUrl.startsWith("/")) {
            try {
                File f = new File(previewUrl).getCanonicalFile();
                File root = new File(context.getApplicationInfo().dataDir).getCanonicalFile();
                if (f.getPath().startsWith(root.getPath() + File.separator)
                        && f.isFile() && f.canRead() && f.length() <= MediaPolicy.MAX_BYTES) {
                    BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
                    boundsOpts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(f.getAbsolutePath(), boundsOpts);
                    BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                    decodeOpts.inSampleSize = computeSampleSize(
                            boundsOpts.outWidth, boundsOpts.outHeight, maxWidthPx);
                    decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), decodeOpts);
                    if (bmp != null) {
                        MEM_CACHE.put(cacheKey, bmp);
                        return bmp;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        // No heuristic cache scan: it could display another conversation's image.
        if (previewUrl.startsWith("/")) {
            return null;
        }
        Bitmap fromCdn = tryDownloadFromCdn(previewUrl, maxWidthPx);
        if (fromCdn != null) {
            MEM_CACHE.put(cacheKey, fromCdn);
            Log.d(TAG, "loaded from CDN previewUrl");
        }
        return fromCdn;
    }

    private static Bitmap tryDownloadFromCdn(String previewUrl, int maxWidthPx) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(previewUrl);
            for (int redirects = 0; ; redirects++) {
                if (!MediaPolicy.isAllowed(url)) {
                    return null;
                }
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(6_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("User-Agent", "OrcaPeek/0.10.2");
                int status = conn.getResponseCode();
                if (status != 301 && status != 302 && status != 303
                        && status != 307 && status != 308) {
                    break;
                }
                String location = conn.getHeaderField("Location");
                if (location == null || redirects >= 3) {
                    return null;
                }
                url = new URL(url, location);
                conn.disconnect();
            }
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                Log.w(TAG, "CDN returned HTTP " + status);
                return null;
            }
            try (InputStream stream = conn.getInputStream()) {
                // Two-pass: bounds first, then subsample decode
                byte[] data = MediaPolicy.readBounded(stream, conn.getContentLength());
                if (data == null || data.length == 0) {
                    return null;
                }
                BitmapFactory.Options boundsOpts = new BitmapFactory.Options();
                boundsOpts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, boundsOpts);
                BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                decodeOpts.inSampleSize = computeSampleSize(
                        boundsOpts.outWidth, boundsOpts.outHeight, maxWidthPx);
                decodeOpts.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeByteArray(data, 0, data.length, decodeOpts);
            }
        } catch (Throwable error) {
            Log.w(TAG, "CDN load failed: " + error.getClass().getSimpleName());
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static int computeSampleSize(int origW, int origH, int targetW) {
        if (origW <= 0 || origH <= 0 || targetW <= 0) return 1;
        int sample = 1;
        while ((origW / sample) > targetW || ((long) origW / sample * origH / sample) > MAX_PIXELS) {
            sample *= 2;
        }
        return sample;
    }
}
