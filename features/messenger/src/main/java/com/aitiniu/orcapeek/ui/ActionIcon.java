package com.aitiniu.orcapeek.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.Locale;

/** A single outline icon family, independent of emoji fonts and module resources. */
final class ActionIcon extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String action;

    ActionIcon(Context context, String action) {
        super(context);
        this.action = action.toLowerCase(Locale.ROOT);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        paint.setColor(Color.rgb(28, 28, 30));
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 24f, getHeight() / 24f);
        if (action.contains("unread") || action.contains("chưa đọc")) {
            canvas.drawRoundRect(3, 5, 21, 19, 2, 2, paint);
            path(canvas, 3, 6, 12, 12, 21, 6);
        } else if (action.contains("read") || action.contains("đã đọc")) {
            path(canvas, 4, 12, 9, 17, 20, 6);
        } else if (action.startsWith("pin") || action.startsWith("ghim")) {
            path(canvas, 8, 3, 16, 3, 15, 9, 19, 13, 5, 13, 9, 9, 8, 3);
            path(canvas, 12, 13, 12, 21);
        } else if (action.contains("mute") || action.contains("thông báo")) {
            path(canvas, 5, 16, 6, 14, 6, 10, 7, 6, 10, 4, 14, 4, 17, 6, 18, 10, 18, 14, 19, 16, 5, 16);
            canvas.drawArc(10, 17, 14, 21, 0, 180, false, paint);
            path(canvas, 3, 3, 21, 21);
        } else if (action.equals("archive") || action.equals("lưu trữ")) {
            canvas.drawRoundRect(3, 4, 21, 8, 1, 1, paint);
            path(canvas, 5, 8, 5, 20, 19, 20, 19, 8);
            path(canvas, 10, 12, 14, 12);
        } else if (action.equals("delete") || action.equals("xóa")) {
            path(canvas, 4, 6, 20, 6);
            path(canvas, 9, 6, 9, 3, 15, 3, 15, 6);
            path(canvas, 6, 6, 7, 21, 17, 21, 18, 6);
            path(canvas, 10, 10, 10.5f, 17);
            path(canvas, 14, 10, 13.5f, 17);
        } else {
            canvas.drawCircle(5, 12, 0.8f, paint);
            canvas.drawCircle(12, 12, 0.8f, paint);
            canvas.drawCircle(19, 12, 0.8f, paint);
        }
        canvas.restore();
    }

    private void path(Canvas canvas, float... points) {
        Path path = new Path();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) {
            path.lineTo(points[index], points[index + 1]);
        }
        canvas.drawPath(path, paint);
    }
}
