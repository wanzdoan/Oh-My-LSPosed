// language: Java, file: FastBlur.java, runtime: Android API 26+, target: OrcaPeek
// Stack-based Gaussian approximation blur for downscaled bitmaps.
// Runs on caller thread — always call on a background thread or use with postDelayed.
package com.aitiniu.orcapeek.ui;

import android.graphics.Bitmap;

public final class FastBlur {

    private FastBlur() {
    }

    /**
     * Applies a fast stack blur in-place.
     *
     * @param bitmap mutable ARGB_8888 bitmap (modify in-place, returns same object)
     * @param radius blur radius in pixels (1..25 practical range)
     * @return the same bitmap for chaining
     */
    public static Bitmap blur(Bitmap bitmap, int radius) {
        if (bitmap == null || bitmap.isRecycled() || radius < 1) {
            return bitmap;
        }
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        int[] pixels = new int[w * h];
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
        stackBlur(pixels, w, h, radius);
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h);
        return bitmap;
    }

    // StackBlur — Robert Penner's algorithm, Java port
    private static void stackBlur(int[] pixels, int w, int h, int r) {
        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = r + r + 1;
        int[] rsum = new int[wh];
        int[] gsum = new int[wh];
        int[] bsum = new int[wh];
        int[] vmin = new int[Math.max(w, h)];
        int[] dvcount = new int[256 * div];
        for (int i = 0; i < 256 * div; i++) {
            dvcount[i] = i / div;
        }
        int[] stack = new int[div];
        int rix, rix2, gix, bix, rout, gout, bout, rin, gin, bin, yp, yi, yw;
        yw = yi = 0;
        for (int y = 0; y < h; y++) {
            rin = gin = bin = rout = gout = bout = 0;
            rix2 = pixels[yi];
            int r1 = (rix2 >> 16) & 0xff;
            int g1 = (rix2 >> 8) & 0xff;
            int b1 = rix2 & 0xff;
            for (int i = 0; i <= r; i++) {
                stack[i] = rix2;
                rout += (r1 * (i + 1));
                gout += (g1 * (i + 1));
                bout += (b1 * (i + 1));
            }
            for (int i = 1; i <= r; i++) {
                int p = pixels[yi + (Math.min(i, wm))];
                stack[i + r] = p;
                int ri = (r - i + 1);
                rout += ((p >> 16) & 0xff) * ri;
                gout += ((p >> 8) & 0xff) * ri;
                bout += (p & 0xff) * ri;
                rin += ((p >> 16) & 0xff);
                gin += ((p >> 8) & 0xff);
                bin += (p & 0xff);
            }
            int sir = r;
            for (int x = 0; x < w; x++) {
                rsum[yi] = dvcount[rout];
                gsum[yi] = dvcount[gout];
                bsum[yi] = dvcount[bout];
                rout -= rout - rin;
                gout -= gout - gin;
                bout -= bout - bin;
                int stackStart = (sir + div - r) % div;
                int p = stack[stackStart];
                rout -= (p >> 16) & 0xff;
                gout -= (p >> 8) & 0xff;
                bout -= p & 0xff;
                if (y == 0) {
                    vmin[x] = Math.min(x + r + 1, wm);
                }
                int p2 = pixels[yw + vmin[x]];
                stack[stackStart] = p2;
                rin += (p2 >> 16) & 0xff;
                gin += (p2 >> 8) & 0xff;
                bin += p2 & 0xff;
                rout += rin;
                gout += gin;
                bout += bin;
                sir = (sir + 1) % div;
                p = stack[sir];
                rix = (p >> 16) & 0xff;
                gix = (p >> 8) & 0xff;
                bix = p & 0xff;
                rin -= rix;
                gin -= gix;
                bin -= bix;
                yi++;
            }
            yw += w;
        }
        for (int x = 0; x < w; x++) {
            rin = gin = bin = rout = gout = bout = 0;
            yp = -r * w;
            for (int i = 0; i <= r; i++) {
                yi = Math.max(0, yp) + x;
                int rs = rsum[yi];
                int gs = gsum[yi];
                int bs = bsum[yi];
                stack[i] = (rs << 16) | (gs << 8) | bs;
                rout += rs * (i + 1);
                gout += gs * (i + 1);
                bout += bs * (i + 1);
                if (i > 0) {
                    rin += rs;
                    gin += gs;
                    bin += bs;
                }
                yp += w;
            }
            yi = x;
            int sir = r;
            for (int y = 0; y < h; y++) {
                pixels[yi] = (0xff000000)
                        | (dvcount[rout] << 16)
                        | (dvcount[gout] << 8)
                        | dvcount[bout];
                rout -= rout - rin;
                gout -= gout - gin;
                bout -= bout - bin;
                int stackStart = (sir + div - r) % div;
                int p = stack[stackStart];
                rout -= (p >> 16) & 0xff;
                gout -= (p >> 8) & 0xff;
                bout -= p & 0xff;
                if (x == 0) {
                    vmin[y] = Math.min(y + r + 1, hm) * w;
                }
                int p2 = rsum[vmin[y] + x];
                int q2 = gsum[vmin[y] + x];
                int s2 = bsum[vmin[y] + x];
                stack[stackStart] = (p2 << 16) | (q2 << 8) | s2;
                rin += p2;
                gin += q2;
                bin += s2;
                rout += rin;
                gout += gin;
                bout += bin;
                sir = (sir + 1) % div;
                p = stack[sir];
                rix = (p >> 16) & 0xff;
                gix = (p >> 8) & 0xff;
                bix = p & 0xff;
                rin -= rix;
                gin -= gix;
                bin -= bix;
                yi += w;
            }
        }
    }
}
