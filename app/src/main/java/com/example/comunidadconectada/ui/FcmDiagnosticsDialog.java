package com.example.comunidadconectada.ui;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.FcmTokenFingerprint;
import com.example.comunidadconectada.services.CommunityMessagingService;
import com.example.comunidadconectada.services.FcmDiagnostics;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public final class FcmDiagnosticsDialog extends DialogFragment {
    private static final String TAG = "fcm_diagnostics";

    static void open(FragmentManager manager) {
        if (!manager.isStateSaved() && manager.findFragmentByTag(TAG) == null) {
            new FcmDiagnosticsDialog().showNow(manager, TAG);
        }
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(context, 24);
        content.setPadding(padding, 0, padding, padding);
        scroll.addView(content);
        render(content);
        return new MaterialAlertDialogBuilder(context).setTitle(R.string.fcm_diagnostics)
                .setView(scroll).setPositiveButton(R.string.close, null).create();
    }

    private static void render(LinearLayout content) {
        Context context = content.getContext();
        FcmDiagnostics.Snapshot snapshot = FcmDiagnostics.snapshot(context);
        content.removeAllViews();
        Ui.paragraph(content, context.getString(R.string.fcm_diagnostics_retention));
        Ui.paragraph(content, context.getString(snapshot.recording()
                ? R.string.fcm_diagnostics_recording : R.string.fcm_diagnostics_stopped));
        Ui.button(content, snapshot.recording() ? R.string.fcm_diagnostics_stop : R.string.fcm_diagnostics_start, 0, () -> {
            if (snapshot.recording()) FcmDiagnostics.stop(context);
            else FcmDiagnostics.start(context);
            render(content);
        });
        Ui.button(content, R.string.fcm_diagnostics_refresh, 0, () -> render(content));
        Ui.button(content, R.string.fcm_renew_registration, 0, () -> confirmRenewal(context));
        if (!snapshot.summary().isEmpty()) {
            Ui.button(content, R.string.fcm_diagnostics_share, 0, () -> share(context));
        }
        Ui.button(content, R.string.fcm_diagnostics_clear, 0, () -> {
            FcmDiagnostics.clear(context);
            render(content);
        });
        Ui.paragraph(content, snapshot.summary().isEmpty()
                ? context.getString(R.string.fcm_diagnostics_empty) : snapshot.summary());
    }

    private static void confirmRenewal(Context context) {
        TextInputEditText fingerprint = new TextInputEditText(context);
        fingerprint.setHint(R.string.fcm_rejected_fingerprint);
        fingerprint.setSingleLine(true);
        fingerprint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        fingerprint.setSaveEnabled(false);
        fingerprint.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout confirmation = new LinearLayout(context);
        confirmation.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(context, 24);
        confirmation.setPadding(padding, 0, padding, 0);
        Ui.paragraph(confirmation, context.getString(R.string.fcm_renew_confirmation));
        confirmation.addView(fingerprint);
        var dialog = new MaterialAlertDialogBuilder(context).setTitle(R.string.fcm_renew_registration)
                .setView(confirmation).setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.fcm_renew_confirm, null).show();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(button -> {
            var input = fingerprint.getText();
            String rejected = input == null ? "" : input.toString().trim().toLowerCase(java.util.Locale.ROOT);
            if (!FcmTokenFingerprint.valid(rejected)) {
                fingerprint.setError(context.getString(R.string.fcm_fingerprint_invalid));
                return;
            }
            fingerprint.setText("");
            dialog.dismiss();
            Context application = context.getApplicationContext();
            Toast.makeText(application, R.string.fcm_renew_pending, Toast.LENGTH_SHORT).show();
            CommunityMessagingService.renewRegistration(application, rejected).addOnCompleteListener(completed -> {
                var result = completed.isSuccessful() ? completed.getResult() : FcmDiagnostics.Result.UNEXPECTED_ERROR;
                String message = result == FcmDiagnostics.Result.OK ? application.getString(R.string.fcm_renew_success)
                        : application.getString(R.string.fcm_renew_failure, result.name());
                Toast.makeText(application, message, Toast.LENGTH_LONG).show();
            });
        });
    }

    private static void share(Context context) {
        String summary = FcmDiagnostics.snapshot(context).summary();
        if (summary.isEmpty()) return;
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, summary);
        context.startActivity(Intent.createChooser(share, context.getString(R.string.fcm_diagnostics_share)));
    }
}
