package com.example.comunidadconectada.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import com.example.comunidadconectada.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Ui {
    private static final int PREVIEW_LENGTH = 160;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.forLanguageTag("es-CL"))
            .withZone(ZoneId.of("America/Santiago"));

    private Ui() {}

    public static MaterialButton button(LinearLayout parent, @StringRes int title, @DrawableRes int icon, Runnable action) {
        return button(parent, parent.getContext().getString(title), icon, action);
    }

    public static MaterialButton button(LinearLayout parent, CharSequence title, @DrawableRes int icon, Runnable action) {
        MaterialButton button = new MaterialButton(parent.getContext());
        button.setText(title);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMinHeight(dp(parent.getContext(), 48));
        if (icon != 0) button.setIconResource(icon);
        button.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.topMargin = dp(parent.getContext(), 4);
        parent.addView(button, layout);
        return button;
    }

    public static void emergency(MaterialButton button) {
        int color = button.getContext().getColor(R.color.danger);
        int foreground = button.getContext().getColor(R.color.on_error);
        button.setBackgroundTintList(ColorStateList.valueOf(color));
        button.setTextColor(foreground);
        button.setIconTint(ColorStateList.valueOf(foreground));
    }

    public static TextView heading(LinearLayout parent, int title) {
        TextView heading = new TextView(parent.getContext());
        heading.setText(title);
        heading.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        heading.setPadding(0, dp(parent.getContext(), 14), 0, dp(parent.getContext(), 4));
        ViewCompat.setAccessibilityHeading(heading, true);
        parent.addView(heading);
        return heading;
    }

    public static TextView paragraph(LinearLayout parent, CharSequence content) {
        TextView paragraph = new TextView(parent.getContext());
        paragraph.setText(content);
        paragraph.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        paragraph.setPadding(0, dp(parent.getContext(), 4), 0, dp(parent.getContext(), 8));
        parent.addView(paragraph);
        return paragraph;
    }

    public static void status(TextView view, CharSequence content) {
        view.setText(content);
        view.setVisibility(content == null || content.length() == 0 ? View.GONE : View.VISIBLE);
    }

    public static void confirm(Context context, int title, int message, int action, Runnable confirmed) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title).setMessage(message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(action, (dialog, selected) -> confirmed.run())
                .show();
    }

    public static String preview(String text) {
        int length = text.codePointCount(0, text.length());
        return length <= PREVIEW_LENGTH ? text : text.substring(0, text.offsetByCodePoints(0, PREVIEW_LENGTH - 1)) + "…";
    }

    public static String date(long timestamp) {
        return timestamp > 0 ? DATE.format(Instant.ofEpochMilli(timestamp)) : "";
    }

    public static int dp(Context context, int amount) {
        return Math.round(amount * context.getResources().getDisplayMetrics().density);
    }
}
