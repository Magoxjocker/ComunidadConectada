package com.example.comunidadconectada.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.domain.InputValidation;
import com.example.comunidadconectada.viewmodel.OperationViewModel;
import com.example.comunidadconectada.viewmodel.SessionViewModel;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FormDialog extends DialogFragment {
    private static final String TAG = "record_form";
    private static final String FEEDBACK = "feedback";

    private record Editor(Field field, View control, Supplier<String> value, TextInputLayout input) {}

    private final List<Editor> editors = new ArrayList<>();
    private OperationViewModel model;
    private OperationViewModel.Key operation;
    private SessionViewModel sessionModel;
    private TextView status;
    private AlertDialog form;

    public static void show(FragmentManager manager, int title, int caption, int submit, int feedback,
                            String action, Bundle fixed, Field... fields) {
        if (manager.isStateSaved() || manager.findFragmentByTag(TAG) != null) return;
        Bundle arguments = new Bundle();
        arguments.putInt("title", title);
        arguments.putInt("caption", caption);
        arguments.putInt("submit", submit);
        arguments.putInt(FEEDBACK, feedback);
        arguments.putString("action", action);
        arguments.putBundle("fixed", fixed);
        ArrayList<Bundle> serialized = new ArrayList<>();
        for (Field field : fields) serialized.add(field.toBundle());
        arguments.putParcelableArrayList("fields", serialized);
        FormDialog dialog = new FormDialog();
        dialog.setArguments(arguments);
        dialog.show(manager, TAG);
    }

    public static Bundle target(String key, String value) {
        Bundle bundle = new Bundle();
        bundle.putString(key, value);
        return bundle;
    }

    @NonNull @Override public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        ViewModelProvider provider = new ViewModelProvider(requireActivity());
        model = provider.get(OperationViewModel.class);
        sessionModel = provider.get(SessionViewModel.class);
        Bundle arguments = requireArguments();
        operation = operationKey(arguments);
        setCancelable(false);
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(requireContext(), 20);
        panel.setPadding(padding, 0, padding, padding);
        scroll.addView(panel);
        int caption = arguments.getInt("caption");
        if (caption != 0) Ui.paragraph(panel, getText(caption));
        addEditors(panel, savedInstanceState);
        status = Ui.paragraph(panel, "");
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        Ui.status(status, null);
        form = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(arguments.getInt("title"))
                .setView(scroll)
                .setPositiveButton(arguments.getInt("submit"), null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        model.state(operation).observe(this, this::renderState);
        return form;
    }

    private OperationViewModel.Key operationKey(Bundle arguments) {
        SessionViewModel.Session session = sessionModel.session().getValue();
        Bundle fixed = arguments.getBundle("fixed");
        String target = fixed == null ? "" : fixed.getString("id", fixed.getString("uid", ""));
        return new OperationViewModel.Key(session.uid(), session.community(), arguments.getString("action"), target);
    }

    private void addEditors(LinearLayout panel, Bundle restored) {
        editors.clear();
        ArrayList<Bundle> fields = BundleCompat.getParcelableArrayList(requireArguments(), "fields", Bundle.class);
        Map<String, String> submitted = model.state(operation).getValue().draft();
        for (Bundle definition : fields) {
            Field field = Field.fromBundle(definition);
            String previous = submitted.getOrDefault(field.key(), field.initial());
            String initial = restored == null ? previous : restored.getString(field.key(), previous);
            editors.add(createEditor(panel, field, initial));
        }
    }

    private Editor createEditor(LinearLayout panel, Field field, String initial) {
        if (field.type() == Field.Type.CHOICE) return choice(panel, field, initial);
        if (field.type() == Field.Type.CHECK) {
            MaterialCheckBox check = new MaterialCheckBox(requireContext());
            check.setText(field.label());
            check.setMinHeight(Ui.dp(requireContext(), 48));
            check.setChecked(Boolean.parseBoolean(initial));
            panel.addView(check);
            return new Editor(field, check, () -> Boolean.toString(check.isChecked()), null);
        }
        int inputType = switch (field.type()) {
            case EMAIL -> InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS;
            case PRICE -> InputType.TYPE_CLASS_NUMBER;
            case DATE -> InputType.TYPE_CLASS_DATETIME;
            case MULTILINE, OPTIONS -> InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
            default -> InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;
        };
        int minLines = field.type() == Field.Type.OPTIONS || field.type() == Field.Type.MULTILINE ? 2 : 1;
        TextInputLayout input = FormViews.input(panel, getString(field.label()), inputType, minLines, View.generateViewId());
        input.getEditText().setText(initial);
        return new Editor(field, input.getEditText(), () -> FormViews.raw(input), input);
    }

    private Editor choice(LinearLayout panel, Field field, String initial) {
        TextView label = Ui.paragraph(panel, getText(field.label()));
        Spinner spinner = new Spinner(requireContext(), Spinner.MODE_DROPDOWN);
        spinner.setId(View.generateViewId());
        spinner.setMinimumHeight(Ui.dp(requireContext(), 48));
        label.setLabelFor(spinner.getId());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, field.labels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, field.values().indexOf(initial)));
        panel.addView(spinner, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return new Editor(field, spinner, () -> field.values().get(spinner.getSelectedItemPosition()), null);
    }

    @Override public void onStart() {
        super.onStart();
        form.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> submit());
        form.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(view -> cancel());
        model.setVisible(operation, true);
        renderState(model.state(operation).getValue());
    }

    @Override public void onStop() {
        model.setVisible(operation, false);
        super.onStop();
    }

    private void submit() {
        Bundle arguments = requireArguments();
        Map<String, Object> fields = new HashMap<>();

        Bundle fixed = arguments.getBundle("fixed");
        if (fixed != null) {
            for (String key : fixed.keySet()) {
                if ("active".equals(key)) fields.put(key, fixed.getBoolean(key));
                else fields.put(key, fixed.getString(key));
            }
        }
        boolean valid = true;
        for (Editor editor : editors) {
            try {
                fields.put(editor.field().key(), parse(editor.field(), editor.value().get()));
                if (editor.input() != null) editor.input().setError(null);
            } catch (IllegalArgumentException validation) {
                valid = false;
                if (editor.input() != null) editor.input().setError(validation.getMessage());
                else Ui.status(status, validation.getMessage());
            }
        }
        if (!valid || !validateReportResolution(fields)) return;
        SessionViewModel.Session session = sessionModel.session().getValue();
        if (session.stage() != SessionViewModel.Stage.ACTIVE || !operation.equals(operationKey(arguments))) {
            Ui.status(status, getText(R.string.denied));
            return;
        }
        Map<String, String> draft = new HashMap<>();
        for (Editor editor : editors) draft.put(editor.field().key(), editor.value().get());
        model.submit(operation, draft, arguments.getInt(FEEDBACK), requestId -> {
            fields.put("requestId", requestId);
            return sessionModel.repository().action(operation.action(), fields);
        });
    }

    private boolean validateReportResolution(Map<String, Object> fields) {
        if (!"updateReport".equals(operation.action()) ||
                InputValidation.validReportResolution((String) fields.get("status"), (String) fields.get("note"))) return true;
        for (Editor editor : editors) {
            if ("note".equals(editor.field().key()) && editor.input() != null) {
                editor.input().setError(getString(R.string.report_note_required));
                editor.input().getEditText().requestFocus();
                return false;
            }
        }
        Ui.status(status, getText(R.string.report_note_required));
        return false;
    }

    private Object parse(Field field, String raw) {
        String value = InputValidation.clean(raw);
        if (value.length() < field.minimum()) throw new IllegalArgumentException(getString(R.string.required));
        if (field.maximum() > 0 && field.type() != Field.Type.PRICE && value.length() > field.maximum()) {
            throw new IllegalArgumentException(getResources().getQuantityString(R.plurals.too_long, field.maximum(), field.maximum()));
        }
        return switch (field.type()) {
            case EMAIL -> validatedEmail(value);
            case PRICE -> validatedPrice(field, value);
            case DATE -> {
                Long timestamp = InputValidation.closeDate(value, System.currentTimeMillis());
                if (timestamp == null) throw new IllegalArgumentException(getString(R.string.invalid_date));
                yield timestamp;
            }
            case OPTIONS -> {
                List<String> options = InputValidation.pollOptions(value);
                if (options.isEmpty()) throw new IllegalArgumentException(getString(R.string.invalid_options));
                yield options;
            }
            case CHECK -> Boolean.parseBoolean(value);
            case CHOICE -> "choice".equals(field.key()) ? Integer.parseInt(value) : value;
            default -> value;
        };
    }

    private String validatedEmail(String value) {
        String email = InputValidation.email(value);
        if (!InputValidation.validEmail(email)) throw new IllegalArgumentException(getString(R.string.invalid_email));
        return email;
    }

    private int validatedPrice(Field field, String value) {
        try {
            int amount = Integer.parseInt(value);
            if (amount >= field.minimum() && amount <= field.maximum()) return amount;
        } catch (NumberFormatException ignored) {
            // The same message covers invalid format and values outside the supported range.
        }
        throw new IllegalArgumentException(getString(R.string.invalid_number, field.minimum(), field.maximum()));
    }

    private void renderState(OperationViewModel.State state) {
        if (state == null || form == null || !form.isShowing()) return;
        form.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!state.pending());
        form.getButton(AlertDialog.BUTTON_NEGATIVE).setText(state.pending() ? R.string.back : R.string.cancel);
        for (Editor editor : editors) editor.control().setEnabled(!state.pending());
        if (state.confirmed()) dismiss();
        else if (state.pending()) Ui.status(status, getText(R.string.operation_pending));
        else if (state.failure() != null) {
            String details = Errors.validationMessage(state.failure());
            Ui.status(status, details == null ? getText(Errors.message(state.failure(), R.string.generic_error)) : details);
        }
    }

    private void cancel() {
        if (model.state(operation).getValue().pending()) {
            model.setVisible(operation, false);
            Snackbar.make(requireActivity().findViewById(android.R.id.content),
                    R.string.operation_pending, Snackbar.LENGTH_LONG).show();
            dismiss();
            return;
        }
        boolean dirty = editors.stream().anyMatch(editor ->
                !editor.field().initial().equals(editor.value().get()) &&
                        !(editor.field().type() == Field.Type.CHOICE && editor.field().initial().isEmpty()
                                && editor.field().values().get(0).equals(editor.value().get())));
        if (!dirty) discardDraft();
        else Ui.confirm(requireContext(), R.string.discard_title, R.string.cancel_description, R.string.discard,
                this::discardDraft);
    }

    private void discardDraft() {
        model.forgetDraft(operation);
        dismiss();
    }

    @Override public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        for (Editor editor : editors) savedInstanceState.putString(editor.field().key(), editor.value().get());
    }
}
