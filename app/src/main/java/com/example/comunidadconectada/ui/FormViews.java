package com.example.comunidadconectada.ui;

import android.content.Context;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public final class FormViews {
    private FormViews() {}

    public static TextInputLayout input(LinearLayout parent, String label, int inputType, int minLines, int viewId) {
        Context context = parent.getContext();
        TextInputLayout field = new TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        field.setHint(label);
        if ((inputType & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_PASSWORD) {
            field.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        }
        TextInputEditText editor = new TextInputEditText(field.getContext());
        editor.setId(viewId);
        editor.setInputType(inputType);
        editor.setMinLines(minLines);
        editor.setMinHeight(Ui.dp(context, 48));
        field.addView(editor, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.topMargin = Ui.dp(context, 8);
        parent.addView(field, layout);
        return field;
    }

    public static String raw(TextInputLayout field) {
        return field.getEditText().getText() == null ? "" : field.getEditText().getText().toString();
    }
}
