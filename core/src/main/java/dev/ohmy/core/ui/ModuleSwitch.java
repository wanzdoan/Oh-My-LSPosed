package dev.ohmy.core.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.CompoundButton;

/** One state source for drawing, touch, keyboard and accessibility. */
public final class ModuleSwitch extends CompoundButton {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final RectF thumb = new RectF();
    private final Path check = new Path();

    public ModuleSwitch(Context context) {
        super(context, null, 0);
        setButtonDrawable(null);
        setFocusable(true);
        setClickable(true);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(Math.max(dp(64), getSuggestedMinimumWidth()), widthSpec),
                resolveSize(Math.max(dp(48), getSuggestedMinimumHeight()), heightSpec));
    }

    @Override public void setChecked(boolean checked) {
        super.setChecked(checked);
        invalidate();
    }

    @Override public CharSequence getAccessibilityClassName() { return "android.widget.Switch"; }

    @Override protected void onDraw(Canvas canvas) {
        float left = (getWidth() - dp(52)) / 2f;
        float top = (getHeight() - dp(30)) / 2f;
        boolean checked = isChecked();
        track.set(left, top, left + dp(52), top + dp(30));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? Color.rgb(37, 99, 235) : Color.rgb(229, 234, 242));
        paint.setAlpha(isEnabled() ? (isPressed() ? 210 : 255) : 100);
        canvas.drawRoundRect(track, dp(9), dp(9), paint);

        float thumbLeft = checked ? track.right - dp(27) : track.left + dp(3);
        thumb.set(thumbLeft, top + dp(3), thumbLeft + dp(24), top + dp(27));
        paint.setColor(Color.WHITE);
        paint.setAlpha(isEnabled() ? 255 : 185);
        canvas.drawRoundRect(thumb, dp(6), dp(6), paint);

        if (checked) {
            float x = thumb.centerX(), y = thumb.centerY();
            paint.setColor(Color.rgb(37, 99, 235));
            paint.setAlpha(isEnabled() ? 255 : 110);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            check.reset();
            check.moveTo(x - dp(4), y);
            check.lineTo(x - dp(1), y + dp(3));
            check.lineTo(x + dp(5), y - dp(3));
            canvas.drawPath(check, paint);
        }
        if (isFocused()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(Color.rgb(37, 99, 235));
            paint.setAlpha(140);
            track.inset(-dp(3), -dp(3));
            canvas.drawRoundRect(track, dp(12), dp(12), paint);
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
