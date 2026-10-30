// language: Java, file: IosConversationPreview.java, runtime: Android API 26+, target: OrcaPeek v0.10
// v0.10: Renders image/GIF messages as ImageView bubbles via MessageMediaLoader.
//        Text messages render as before. Stickers rendered as small square ImageViews (no bubble bg).
// v0.9:  No header bar — messages start immediately inside the card.
//        Avatar shown only on the LAST message in each consecutive incoming run.
package com.aitiniu.orcapeek.ui;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.aitiniu.orcapeek.data.RecentMessagesRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class IosConversationPreview {
    // Glass white — 95% opacity so blur bleeds through
    private static final int CARD_WHITE  = Color.argb(242, 250, 250, 252);
    private static final int CARD_STROKE = Color.argb(40, 0, 0, 0);
    private static final int INCOMING    = Color.rgb(232, 232, 235);
    private static final int OUTGOING    = Color.rgb(10, 132, 255);

    // Max image height relative to card width — prevents portrait photos from eating the whole card
    private static final float MAX_IMG_ASPECT = 1.4f; // height / width

    private IosConversationPreview() {
    }

    public static View create(Activity activity, PendingPreviewStore.Snapshot snapshot,
            Runnable openConversation) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        card.setBackground(glassRect(dp(activity, 20), dp(activity, 1)));
        card.setElevation(dp(activity, 20));
        card.setClipToOutline(true);
        card.setAlpha(0f);
        card.setScaleX(0.96f);
        card.setScaleY(0.96f);

        BoundedScrollView scroller = new BoundedScrollView(activity);
        scroller.setTag("orcapeek.preview.scroll");
        scroller.setFillViewport(false);
        scroller.setVerticalScrollBarEnabled(false);

        LinearLayout messages = new LinearLayout(activity);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(0, dp(activity, 4), 0, dp(activity, 4));

        scroller.addView(messages, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // v0.9+: taller card — up to 65% screen height, WRAP_CONTENT when shorter
        int maxHeight = Math.min(dp(activity, 520),
                Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.65f));
        scroller.setMaximumHeight(maxHeight);
        card.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.setOnClickListener(view -> {
            if (openConversation != null) {
                openConversation.run();
            }
        });

        int cardContentWidthPx = activity.getResources().getDisplayMetrics().widthPixels
                - dp(activity, 24 + 16); // overlay margins + card padding

        if (snapshot != null) {
            addCenteredStatus(activity, messages, "Đang tải…");
            Bitmap croppedAvatar = cropAvatar(snapshot.bitmap, activity);
            RecentMessagesRepository.load(activity, snapshot.label, result -> {
                messages.removeAllViews();
                if (!result.success) {
                    addCenteredStatus(activity, messages, result.status);
                } else if (result.messages.isEmpty()) {
                    addCenteredStatus(activity, messages, "Chưa có tin nhắn để xem trước");
                } else {
                    List<RecentMessagesRepository.Message> msgs = result.messages;
                    long previousTime = 0L;
                    for (int i = 0; i < msgs.size(); i++) {
                        RecentMessagesRepository.Message msg = msgs.get(i);
                        if (previousTime == 0L
                                || msg.timestampMs - previousTime > 10 * 60_000L) {
                            addTime(activity, messages, msg.timestampMs);
                        }
                        // iOS: avatar only on LAST message in each consecutive incoming run
                        boolean isLastInRun = !msg.outgoing
                                && (i == msgs.size() - 1 || msgs.get(i + 1).outgoing);
                        addRow(activity, messages, msg, croppedAvatar,
                                result.title, isLastInRun, cardContentWidthPx);
                        previousTime = msg.timestampMs;
                    }
                    scroller.post(() -> scroller.fullScroll(View.FOCUS_DOWN));
                }
            });
        } else {
            addCenteredStatus(activity, messages, "Nhấn giữ một hội thoại để xem trước");
        }

        card.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(180L).setInterpolator(new DecelerateInterpolator()).start();
        return card;
    }

    public static void limitHeight(View card, int availableHeight) {
        View scroller = card.findViewWithTag("orcapeek.preview.scroll");
        if (scroller instanceof BoundedScrollView) {
            ((BoundedScrollView) scroller).setMaximumHeight(availableHeight);
        }
    }

    private static final class BoundedScrollView extends ScrollView {
        private int maximumHeight = Integer.MAX_VALUE;

        BoundedScrollView(android.content.Context context) {
            super(context);
        }

        void setMaximumHeight(int value) {
            int height = Math.max(1, value);
            if (maximumHeight != height) {
                maximumHeight = height;
                requestLayout();
            }
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int size = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED
                    ? maximumHeight : Math.min(maximumHeight, MeasureSpec.getSize(heightSpec));
            super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row dispatcher — picks text bubble, image bubble, or sticker
    // ─────────────────────────────────────────────────────────────────────────

    private static void addRow(Activity activity, LinearLayout parent,
            RecentMessagesRepository.Message message, Bitmap avatarBitmap,
            String title, boolean showAvatar, int cardWidthPx) {
        if (message.isSticker) {
            addStickerRow(activity, parent, message, avatarBitmap, title, showAvatar, cardWidthPx);
        } else if (message.mediaUrl != null) {
            addImageRow(activity, parent, message, avatarBitmap, title, showAvatar, cardWidthPx);
        } else {
            addTextBubble(activity, parent, message, avatarBitmap, title, showAvatar);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Text bubble
    // ─────────────────────────────────────────────────────────────────────────

    private static void addTextBubble(Activity activity, LinearLayout parent,
            RecentMessagesRepository.Message message, Bitmap avatarBitmap,
            String title, boolean showAvatar) {
        LinearLayout row = buildRow(activity, message.outgoing);
        addAvatarOrSpacer(activity, row, message.outgoing, showAvatar, avatarBitmap, title);

        TextView bubble = new TextView(activity);
        bubble.setText(message.body);
        bubble.setTextColor(message.outgoing ? Color.WHITE : Color.rgb(28, 28, 30));
        bubble.setTextSize(14f);
        bubble.setLineSpacing(0f, 1.05f);
        bubble.setPadding(
                dp(activity, 11), dp(activity, 7), dp(activity, 11), dp(activity, 7));
        bubble.setBackground(
                roundRect(message.outgoing ? OUTGOING : INCOMING, dp(activity, 17)));
        bubble.setMaxWidth(
                Math.round(activity.getResources().getDisplayMetrics().widthPixels * 0.68f));
        row.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addRowToParent(parent, row, showAvatar);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image / GIF bubble — loads via MessageMediaLoader
    // ─────────────────────────────────────────────────────────────────────────

    private static void addImageRow(Activity activity, LinearLayout parent,
            RecentMessagesRepository.Message message, Bitmap avatarBitmap,
            String title, boolean showAvatar, int cardWidthPx) {
        LinearLayout row = buildRow(activity, message.outgoing);
        addAvatarOrSpacer(activity, row, message.outgoing, showAvatar, avatarBitmap, title);

        // Compute display size from known dimensions (or use a 4:3 default)
        int dispW, dispH;
        int maxBubbleW = (int) (cardWidthPx * 0.72f);
        if (message.mediaWidth > 0 && message.mediaHeight > 0) {
            float aspect = (float) message.mediaHeight / message.mediaWidth;
            aspect = Math.min(aspect, MAX_IMG_ASPECT);
            dispW = maxBubbleW;
            dispH = Math.round(dispW * aspect);
        } else {
            dispW = maxBubbleW;
            dispH = (int) (dispW * 0.75f); // 4:3 placeholder
        }

        // Placeholder ImageView with rounded corners
        ImageView imageView = new ImageView(activity);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable placeholder = roundRect(Color.rgb(200, 200, 205), dp(activity, 14));
        imageView.setBackground(placeholder);
        imageView.setClipToOutline(true);

        // GIF badge overlay if needed
        if (message.isGif) {
            FrameLayout container = new FrameLayout(activity);
            container.addView(imageView,
                    new FrameLayout.LayoutParams(dispW, dispH));
            TextView gifBadge = new TextView(activity);
            gifBadge.setText("GIF");
            gifBadge.setTextColor(Color.WHITE);
            gifBadge.setTextSize(10f);
            gifBadge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            gifBadge.setBackground(roundRect(Color.argb(160, 0, 0, 0), dp(activity, 4)));
            gifBadge.setPadding(dp(activity, 4), dp(activity, 2), dp(activity, 4), dp(activity, 2));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeParams.gravity = Gravity.BOTTOM | Gravity.START;
            badgeParams.leftMargin = dp(activity, 6);
            badgeParams.bottomMargin = dp(activity, 6);
            container.addView(gifBadge, badgeParams);
            row.addView(container, new LinearLayout.LayoutParams(dispW, dispH));
        } else {
            row.addView(imageView, new LinearLayout.LayoutParams(dispW, dispH));
        }

        addRowToParent(parent, row, showAvatar);

        // Load image asynchronously — replace placeholder when ready
        MessageMediaLoader.load(activity, message.mediaUrl, dispW, bitmap -> {
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                imageView.setBackground(null);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sticker — transparent background, fixed 100dp size
    // ─────────────────────────────────────────────────────────────────────────

    private static void addStickerRow(Activity activity, LinearLayout parent,
            RecentMessagesRepository.Message message, Bitmap avatarBitmap,
            String title, boolean showAvatar, int cardWidthPx) {
        LinearLayout row = buildRow(activity, message.outgoing);
        addAvatarOrSpacer(activity, row, message.outgoing, showAvatar, avatarBitmap, title);

        int size = dp(activity, 96);
        ImageView stickerView = new ImageView(activity);
        stickerView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(stickerView, new LinearLayout.LayoutParams(size, size));
        addRowToParent(parent, row, showAvatar);

        if (message.mediaUrl != null) {
            MessageMediaLoader.load(activity, message.mediaUrl, size, bitmap -> {
                if (bitmap != null) {
                    stickerView.setImageBitmap(bitmap);
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static LinearLayout buildRow(Activity activity, boolean outgoing) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(outgoing ? Gravity.END | Gravity.BOTTOM : Gravity.START | Gravity.BOTTOM);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private static void addAvatarOrSpacer(Activity activity, LinearLayout row,
            boolean outgoing, boolean showAvatar, Bitmap avatarBitmap, String title) {
        if (outgoing) return;
        if (showAvatar) {
            TextView avatar = new TextView(activity);
            avatar.setText(initials(title));
            avatar.setTextColor(Color.WHITE);
            avatar.setTextSize(10f);
            avatar.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            avatar.setGravity(Gravity.CENTER);
            avatar.setBackground(roundRect(Color.rgb(142, 142, 147), dp(activity, 13)));
            if (avatarBitmap != null) {
                avatar.setText("");
                avatar.setBackground(new BitmapDrawable(activity.getResources(), avatarBitmap));
                avatar.setClipToOutline(true);
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 26));
            p.rightMargin = dp(activity, 6);
            row.addView(avatar, p);
        } else {
            View spacer = new View(activity);
            row.addView(spacer, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 26)));
        }
    }

    private static void addRowToParent(LinearLayout parent, LinearLayout row, boolean showAvatar) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = showAvatar ? dp(parent.getContext(), 6) : dp(parent.getContext(), 2);
        parent.addView(row, params);
    }

    private static void addTime(Activity activity, LinearLayout parent, long timestampMs) {
        if (timestampMs <= 0L) return;
        TextView time = new TextView(activity);
        time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date(timestampMs)));
        time.setTextColor(Color.rgb(142, 142, 147));
        time.setTextSize(10f);
        time.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, 4);
        params.bottomMargin = dp(activity, 6);
        parent.addView(time, params);
    }

    private static void addCenteredStatus(Activity activity, LinearLayout parent, String value) {
        TextView status = new TextView(activity);
        status.setText(value);
        status.setTextColor(Color.rgb(142, 142, 147));
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        parent.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56)));
    }

    private static Bitmap cropAvatar(Bitmap row, Activity activity) {
        if (row == null || row.getWidth() < 1 || row.getHeight() < 1) return null;
        try {
            int inset = Math.min(dp(activity, 16), row.getWidth() / 20);
            int size  = Math.min(row.getHeight() - dp(activity, 12), row.getWidth() / 4);
            size = Math.max(1, Math.min(size, row.getWidth() - inset));
            int top = Math.max(0, (row.getHeight() - size) / 2);
            return Bitmap.createBitmap(row, inset, top, size, size);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String initials(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) return "M";
        String[] words = value.split("\\s+");
        String first = words[0].substring(0, 1);
        if (words.length == 1) return first.toUpperCase(Locale.getDefault());
        return (first + words[words.length - 1].substring(0, 1)).toUpperCase(Locale.getDefault());
    }

    private static GradientDrawable roundRect(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }

    private static GradientDrawable glassRect(int radius, int strokeWidth) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_WHITE);
        bg.setCornerRadius(radius);
        bg.setStroke(strokeWidth, CARD_STROKE);
        return bg;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static int dp(android.content.Context ctx, int value) {
        return Math.round(value * ctx.getResources().getDisplayMetrics().density);
    }
}
