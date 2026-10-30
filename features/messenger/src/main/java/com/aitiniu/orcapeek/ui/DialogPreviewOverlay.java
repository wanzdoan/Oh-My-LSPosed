// language: Java, file: DialogPreviewOverlay.java, runtime: Android API 26+, target: OrcaPeek v0.8
// Attaches the iOS-style preview card + action menu to a Messenger dialog window.
// v0.8: Frosted-glass blur background — FLAG_BLUR_BEHIND on API 31+, FastBlur snapshot fallback below.
package com.aitiniu.orcapeek.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.aitiniu.orcapeek.hook.ReadReceiptGuard;
import com.aitiniu.orcapeek.hook.ReadReceiptProbe;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class DialogPreviewOverlay {
    private static final String TAG = "orcapeek.dialog.ios-preview.v08";
    private static final Map<Dialog, Boolean> ATTACHED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Dialog, Boolean> OPENING =
            Collections.synchronizedMap(new WeakHashMap<>());

    // Downscale factor for snapshot blur fallback (1/4 resolution → effective 4× blur radius)
    private static final int DOWNSCALE = 4;
    // Stack-blur radius on the downscaled image (equals ~80px blur at full resolution)
    private static final int BLUR_RADIUS = 20;
    // Window blur radius for FLAG_BLUR_BEHIND (pixels at native resolution, Android 12+)
    private static final int WINDOW_BLUR_RADIUS_DP = 70;

    private DialogPreviewOverlay() {
    }

    public static boolean attach(Dialog dialog) {
        Window window = dialog.getWindow();
        Activity activity = findActivity(dialog.getContext());
        if (window == null || !(window.getDecorView() instanceof ViewGroup)
                || activity == null) {
            return false;
        }
        ViewGroup decor = (ViewGroup) window.getDecorView();
        if (decor.findViewWithTag(TAG) != null) {
            return true;
        }
        PendingPreviewStore.Snapshot snapshot = PendingPreviewStore.peekFresh();
        if (snapshot == null) {
            return false;
        }

        IosActionMenu.Result actionMenu = IosActionMenu.create(activity, dialog, decor);
        if (actionMenu.view == null) {
            Log.w("OrcaPeek", "ORCAPEek_IOS_MENU_FALLBACK actions="
                    + actionMenu.discoveredActions);
            return attachCardOnly(dialog, activity, decor, snapshot);
        }

        ReadReceiptGuard.holdForPreview();
        ATTACHED.put(dialog, Boolean.TRUE);

        // --- Blur layer setup ---
        boolean nativeBlur = applyNativeBlur(window, activity);
        Bitmap blurBitmap = nativeBlur ? null : captureBlurSnapshot(activity, decor);

        // Transparent window background so our blur view shows through
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Hide original dialog children — we replace with our UI
        int originalChildCount = decor.getChildCount();
        for (int index = 0; index < originalChildCount; index++) {
            decor.getChildAt(index).setVisibility(View.INVISIBLE);
        }

        // Full-screen clickable overlay (tap outside = dismiss)
        FrameLayout overlay = new FrameLayout(activity);
        overlay.setTag(TAG);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.setOnClickListener(view -> dialog.dismiss());
        decor.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Fallback blur background image (only when native blur is unavailable)
        if (blurBitmap != null) {
            ImageView blurView = new ImageView(activity);
            blurView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            blurView.setImageDrawable(new BitmapDrawable(activity.getResources(), blurBitmap));
            // Tint overlay — translucent dark, lighter than old 0.52 dimAmount for iOS feel
            blurView.setColorFilter(Color.argb(60, 0, 0, 0));
            blurView.setAlpha(0f);
            overlay.addView(blurView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            blurView.animate().alpha(1f).setDuration(200L)
                    .setInterpolator(new DecelerateInterpolator()).start();
            Log.i("OrcaPeek", "ORCAPEek_BLUR_FALLBACK bitmap="
                    + blurBitmap.getWidth() + "x" + blurBitmap.getHeight());
        }

        // Conversation preview card
        Runnable opener = () -> {
            View source = snapshot.sourceView();
            OPENING.put(dialog, Boolean.TRUE);
            ReadReceiptProbe.armNextConversationClick();
            ReadReceiptGuard.onConversationOpened();
            dialog.dismiss();
            if (source != null) {
                source.postDelayed(source::performClick, 160L);
            }
        };
        View card = IosConversationPreview.create(activity, snapshot, opener);
        card.setClickable(true);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        android.view.WindowInsets insets = decor.getRootWindowInsets();
        int topInset = insets == null ? 0 : insets.getSystemWindowInsetTop();
        int bottomInset = insets == null ? 0 : insets.getSystemWindowInsetBottom();
        cardParams.topMargin = Math.max(dp(activity, 24), topInset + dp(activity, 8));
        overlay.addView(card, cardParams);

        // Action menu below the card
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                Math.min(dp(activity, 320),
                        activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 36)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        menuParams.leftMargin = dp(activity, 12);
        overlay.addView(actionMenu.view, menuParams);
        Runnable fitPreview = () -> {
            actionMenu.view.measure(
                    View.MeasureSpec.makeMeasureSpec(menuParams.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int height = overlay.getHeight() > 0 ? overlay.getHeight()
                    : activity.getResources().getDisplayMetrics().heightPixels;
            int available = height - cardParams.topMargin - bottomInset - dp(activity, 24)
                    - actionMenu.view.getMeasuredHeight() - dp(activity, 10 + 16);
            IosConversationPreview.limitHeight(card,
                    Math.min(dp(activity, 520), Math.max(dp(activity, 56), available)));
        };
        fitPreview.run();
        overlay.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> fitPreview.run());
        card.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            FrameLayout.LayoutParams positioned =
                    (FrameLayout.LayoutParams) actionMenu.view.getLayoutParams();
            int desiredTop = bottom + dp(activity, 10);
            if (positioned.topMargin != desiredTop) {
                positioned.topMargin = desiredTop;
                actionMenu.view.setLayoutParams(positioned);
            }
        });
        actionMenu.view.setOnClickListener(view -> { });
        Log.i("OrcaPeek", "ORCAPEek_IOS_MENU_READY actions="
                + actionMenu.discoveredActions
                + " nativeBlur=" + nativeBlur);
        return true;
    }

    /**
     * Apply Android 12+ window backdrop blur. Returns true if successfully applied.
     * On Android < 12 or when the device has cross-window blur disabled, returns false
     * so the snapshot fallback path is used.
     */
    @SuppressWarnings("deprecation")
    private static boolean applyNativeBlur(Window window, Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // FLAG_DIM_BEHIND as lightweight fallback for older builds
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.dimAmount = 0.18f;
            window.setAttributes(attributes);
            return false;
        }
        try {
            // Check if the device actually supports cross-window blur at runtime
            android.view.WindowManager wm =
                    (android.view.WindowManager) activity.getSystemService(Context.WINDOW_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    && !wm.isCrossWindowBlurEnabled()) {
                // ROM has disabled blur (MIUI/HyperOS battery saving mode)
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams attr = window.getAttributes();
                attr.dimAmount = 0.18f;
                window.setAttributes(attr);
                Log.w("OrcaPeek", "ORCAPEek_BLUR_DISABLED cross_window_blur=false");
                return false;
            }
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.setBlurBehindRadius(dp(activity, WINDOW_BLUR_RADIUS_DP));
            // Light translucent dim so the blur tones down naturally
            attributes.dimAmount = 0.15f;
            window.setAttributes(attributes);
            Log.i("OrcaPeek", "ORCAPEek_BLUR_NATIVE radius=" + WINDOW_BLUR_RADIUS_DP + "dp");
            return true;
        } catch (Throwable error) {
            Log.w("OrcaPeek", "ORCAPEek_BLUR_NATIVE_FAILED error="
                    + error.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * Capture a downscaled screenshot of the activity decor and apply StackBlur.
     * Runs synchronously — must be called before the dialog is shown over the activity.
     * Keeps the bitmap at 1/DOWNSCALE resolution; ImageView.CENTER_CROP stretches it back.
     */
    private static Bitmap captureBlurSnapshot(Activity activity, ViewGroup decor) {
        try {
            View root = activity.getWindow().getDecorView();
            int w = root.getWidth();
            int h = root.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            int sw = w / DOWNSCALE;
            int sh = h / DOWNSCALE;
            if (sw <= 0 || sh <= 0) {
                return null;
            }
            Bitmap bitmap = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(1f / DOWNSCALE, 1f / DOWNSCALE);
            root.draw(canvas);
            return FastBlur.blur(bitmap, BLUR_RADIUS);
        } catch (Throwable error) {
            Log.w("OrcaPeek", "ORCAPEek_BLUR_CAPTURE_FAILED error="
                    + error.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean attachCardOnly(Dialog dialog, Activity activity, ViewGroup decor,
            PendingPreviewStore.Snapshot snapshot) {
        Window window = dialog.getWindow();
        if (window != null) {
            applyNativeBlur(window, activity);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        ReadReceiptGuard.holdForPreview();
        ATTACHED.put(dialog, Boolean.TRUE);
        Runnable opener = () -> {
            View source = snapshot.sourceView();
            OPENING.put(dialog, Boolean.TRUE);
            ReadReceiptProbe.armNextConversationClick();
            ReadReceiptGuard.onConversationOpened();
            dialog.dismiss();
            if (source != null) {
                source.postDelayed(source::performClick, 160L);
            }
        };
        View card = IosConversationPreview.create(activity, snapshot, opener);
        card.setTag(TAG);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 24),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        params.topMargin = dp(activity, 24);
        decor.addView(card, params);
        return true;
    }

    public static boolean isAttached(Dialog dialog) {
        return ATTACHED.containsKey(dialog);
    }

    public static boolean consumeOpening(Dialog dialog) {
        return OPENING.remove(dialog) != null;
    }

    public static void clear(Dialog dialog) {
        ATTACHED.remove(dialog);
        OPENING.remove(dialog);
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) {
                break;
            }
            current = next;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
