package com.aitiniu.orcapeek.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;

import java.lang.ref.WeakReference;

public final class PendingPreviewStore {
    private static final long MAX_AGE_MS = 5_000L;
    private static volatile Snapshot current;

    private PendingPreviewStore() {
    }

    public static Candidate captureCandidate(View view) {
        if (view == null
                || !"com.facebook.orca".equals(view.getContext().getPackageName())
                || view.getWidth() < 300
                || view.getHeight() < 60
                || view.getHeight() > 500) {
            return null;
        }
        try {
            Bitmap bitmap = Bitmap.createBitmap(
                    view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            CharSequence description = view.getContentDescription();
            String label = description == null ? "Conversation preview" : description.toString();
            return new Candidate(bitmap, label, view);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void commit(Candidate candidate) {
        current = new Snapshot(candidate.bitmap, candidate.label, candidate.source,
                SystemClock.elapsedRealtime());
    }

    public static Snapshot peekFresh() {
        Snapshot value = current;
        if (value == null || SystemClock.elapsedRealtime() - value.capturedAt > MAX_AGE_MS) {
            return null;
        }
        return value;
    }

    public static Snapshot consumeFresh() {
        Snapshot value = current;
        current = null;
        if (value == null || SystemClock.elapsedRealtime() - value.capturedAt > MAX_AGE_MS) {
            return null;
        }
        return value;
    }

    public static final class Candidate {
        private final Bitmap bitmap;
        private final String label;
        private final WeakReference<View> source;

        private Candidate(Bitmap bitmap, String label, View source) {
            this.bitmap = bitmap;
            this.label = label;
            this.source = new WeakReference<>(source);
        }
    }

    public static final class Snapshot {
        public final Bitmap bitmap;
        public final String label;
        private final WeakReference<View> source;
        private final long capturedAt;

        private Snapshot(Bitmap bitmap, String label, WeakReference<View> source, long capturedAt) {
            this.bitmap = bitmap;
            this.label = label;
            this.source = source;
            this.capturedAt = capturedAt;
        }

        public View sourceView() {
            return source == null ? null : source.get();
        }
    }
}
