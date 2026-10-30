package dev.alastorkaneki.chromescript;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

final class Ui {
    private Ui() { }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static int color(Context context, int attribute, int fallback) {
        TypedValue out = new TypedValue();
        return context.getTheme().resolveAttribute(attribute, out, true)
                ? (out.resourceId != 0 ? context.getColor(out.resourceId) : out.data)
                : fallback;
    }

    static int text(Context context) {
        return color(context, android.R.attr.textColorPrimary, Color.rgb(32, 33, 36));
    }

    static int secondaryText(Context context) {
        return color(context, android.R.attr.textColorSecondary, Color.rgb(95, 99, 104));
    }

    static TextView label(Context context, String text, float size, boolean bold) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(text(context));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    static Button button(Context context, String title) {
        Button button = new Button(context);
        button.setText(title);
        button.setAllCaps(false);
        return button;
    }

    static LinearLayout row(Context context, View... children) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (View child : children) {
            row.addView(child, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        return row;
    }

    static void toast(Activity activity, String message) {
        activity.runOnUiThread(() -> Toast.makeText(
                activity,
                message == null ? "Unknown error" : message,
                Toast.LENGTH_LONG).show());
    }
}

