package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.comunidadconectada.MainActivity;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.domain.InputValidation;
import com.example.comunidadconectada.viewmodel.SessionViewModel;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Session;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Stage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

public final class AuthFragment extends PanelFragment {
    private static final String REGISTER_FORM = "register";

    private final List<View> controls = new ArrayList<>();
    private final Map<MaterialButton, BooleanSupplier> formActions = new LinkedHashMap<>();
    private final Map<TextInputLayout, IntSupplier> fieldErrors = new LinkedHashMap<>();
    private SessionViewModel model;
    private Session current;
    private boolean registration;

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        registration = savedInstanceState != null && savedInstanceState.getBoolean(REGISTER_FORM);
        model = new ViewModelProvider(requireActivity()).get(SessionViewModel.class);
        current = model.session().getValue();
        if (current != null) render();
        model.session().observe(getViewLifecycleOwner(), session -> {
            if (!session.equals(current)) {
                current = session;
                render();
            }
        });
        model.working().observe(getViewLifecycleOwner(), this::showWorking);
        model.feedback().observe(getViewLifecycleOwner(), message ->
                Ui.status(binding.status, message == null ? null : getString(message)));
    }

    @Override public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putBoolean(REGISTER_FORM, registration);
    }

    private void render() {
        controls.clear();
        formActions.clear();
        fieldErrors.clear();
        binding.actions.removeAllViews();
        switch (current.stage()) {
            case CONFIGURATION, SIGN_IN -> showSignIn();
            case LOADING -> screen(R.string.app_name, R.string.session_loading);
            case VERIFY -> showVerification();
            case PROFILE -> showProfile();
            case PENDING -> showMembershipRequest();
            case SUSPENDED -> showPending(R.string.suspended_title, R.string.suspended_caption);
            case ERROR -> showPending(R.string.session_error_title, R.string.session_error_caption);
            case ACTIVE -> { }
        }
        if (current.stage() != Stage.ACTIVE && current.stage() != Stage.LOADING) {
            button(R.string.accessibility, R.drawable.ic_access, () -> host().open(MainActivity.Destination.ACCESSIBILITY));
        }
        showWorking(Boolean.TRUE.equals(model.working().getValue()));
    }

    private void screen(int title, int caption) {
        binding.heading.setText(title);
        Ui.status(binding.summary, caption == 0 ? null : getString(caption));
    }

    private void showSignIn() {
        boolean configured = current.stage() != Stage.CONFIGURATION;
        screen(registration ? R.string.create_account : R.string.app_name,
                configured ? R.string.sign_in_caption : model.configurationMessage());
        if (!configured) Ui.heading(binding.actions, R.string.config_title);
        if (!registration && configured) {
            ImageView icon = new ImageView(requireContext());
            icon.setImageResource(R.drawable.ic_home);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            binding.actions.addView(icon, new LinearLayout.LayoutParams(Ui.dp(requireContext(), 56), Ui.dp(requireContext(), 56)));
        }
        TextInputLayout name = registration ? field(R.string.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, R.id.auth_name) : null;
        TextInputLayout unit = registration ? field(R.string.unit, InputType.TYPE_CLASS_TEXT, R.id.auth_unit) : null;
        TextInputLayout email = field(R.string.email, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, R.id.auth_email);
        TextInputLayout password = field(R.string.password, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, R.id.auth_password);
        TextInputLayout confirmation = registration
                ? field(R.string.password_confirm, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, R.id.auth_confirmation) : null;
        password.getEditText().setSaveEnabled(false);
        if (confirmation != null) {
            confirmation.getEditText().setSaveEnabled(false);
            password.setHelperText(getString(R.string.password_hint));
        }
        if (registration) {
            formButton(R.string.create_account, R.drawable.ic_profile,
                    () -> validLength(name, 2, 100) && validLength(unit, 1, 30) && validAddress(email)
                            && InputValidation.strongPassword(FormViews.raw(password))
                            && FormViews.raw(password).equals(FormViews.raw(confirmation)),
                    () -> submitRegistration(name, unit, email, password, confirmation));
            watch(name, () -> validLength(name, 2, 100), R.string.invalid_name);
            watch(unit, () -> validLength(unit, 1, 30), R.string.invalid_unit);
            watch(password, () -> InputValidation.strongPassword(FormViews.raw(password)), R.string.invalid_password);
            watch(confirmation, () -> FormViews.raw(password).equals(FormViews.raw(confirmation)), R.string.password_mismatch);
            button(R.string.back, R.drawable.ic_arrow_back, this::toggleRegistration);
        } else {
            formButton(R.string.login, R.drawable.ic_profile,
                    () -> validAddress(email) && !FormViews.raw(password).isEmpty(), () -> submitLogin(email, password));
            button(R.string.create_account, R.drawable.ic_add, this::toggleRegistration);
            formButton(R.string.forgot_password, 0, () -> validAddress(email),
                    () -> model.resetPassword(InputValidation.email(FormViews.raw(email))));
            watch(password, () -> true, 0);
        }
        watch(email, () -> validAddress(email), R.string.invalid_email);
    }

    private void submitLogin(TextInputLayout email, TextInputLayout password) {
        String address = checkedEmail(email);
        String secret = FormViews.raw(password);
        password.setError(secret.isEmpty() ? getString(R.string.required) : null);
        if (address != null && !secret.isEmpty()) model.signIn(address, secret);
    }

    private void submitRegistration(TextInputLayout name, TextInputLayout unit, TextInputLayout email,
                                    TextInputLayout password, TextInputLayout confirmation) {
        String fullName = checkedText(name, 2, 100);
        String dwelling = checkedText(unit, 1, 30);
        String address = checkedEmail(email);
        String secret = FormViews.raw(password);
        boolean strong = InputValidation.strongPassword(secret);
        boolean matches = secret.equals(FormViews.raw(confirmation)) && !secret.isEmpty();
        password.setError(strong ? null : getString(R.string.invalid_password));
        confirmation.setError(matches ? null : getString(R.string.password_mismatch));
        if (fullName != null && dwelling != null && address != null && strong && matches) {
            model.register(fullName, dwelling, address, secret);
        }
    }

    private void showVerification() {
        screen(R.string.verification_title, R.string.verification_caption);
        button(R.string.check_verification, R.drawable.ic_profile, () -> model.refresh(true));
        button(R.string.resend_verification, R.drawable.ic_announce, model::resendVerification);
        showSignOut();
    }

    private void showProfile() {
        screen(R.string.complete_profile, 0);
        TextInputLayout name = field(R.string.name, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS, R.id.auth_name);
        TextInputLayout unit = field(R.string.unit, InputType.TYPE_CLASS_TEXT, R.id.auth_unit);
        button(R.string.save, R.drawable.ic_profile, () -> {
            String fullName = checkedText(name, 2, 100);
            String dwelling = checkedText(unit, 1, 30);
            if (fullName != null && dwelling != null) model.completeProfile(fullName, dwelling);
        });
        showSignOut();
    }

    private void showMembershipRequest() {
        screen(R.string.pending_title, R.string.pending_caption);
        Ui.paragraph(binding.actions, getString(R.string.membership_uid, current.uid())).setTextIsSelectable(true);
        TextInputLayout community = field(R.string.membership_community, InputType.TYPE_CLASS_TEXT, View.generateViewId());
        BooleanSupplier validCode = () -> FormViews.raw(community).trim().matches("[A-Za-z0-9_-]{1,120}");
        formButton(R.string.membership_request, R.drawable.ic_profile, validCode,
                () -> model.requestMembership(FormViews.raw(community).trim()));
        watch(community, validCode, R.string.membership_invalid);
        button(R.string.refresh, R.drawable.ic_profile, () -> model.refresh(true));
        showSignOut();
    }

    private void showPending(int title, int caption) {
        screen(title, caption);
        button(R.string.refresh, R.drawable.ic_profile, () -> model.refresh(true));
        showSignOut();
    }

    private void showSignOut() {
        button(R.string.sign_out, R.drawable.ic_logout, model::signOut);
    }

    private TextInputLayout field(int title, int inputType, int id) {
        TextInputLayout field = FormViews.input(binding.actions, getString(title), inputType, 1, id);
        controls.add(field);
        controls.add(field.getEditText());
        return field;
    }

    private void button(int title, int icon, Runnable operation) {
        controls.add(Ui.button(binding.actions, title, icon, operation));
    }

    private void formButton(int title, int icon, BooleanSupplier valid, Runnable operation) {
        formActions.put(Ui.button(binding.actions, title, icon, operation), valid);
    }

    private void watch(TextInputLayout field, BooleanSupplier valid, int error) {
        fieldErrors.put(field, () -> error != 0 && !FormViews.raw(field).isEmpty() && !valid.getAsBoolean() ? error : 0);
        field.getEditText().addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable value) {
                fieldErrors.forEach((input, validation) -> {
                    int message = validation.getAsInt();
                    input.setError(message == 0 ? null : getString(message));
                });
                updateFormActions(Boolean.TRUE.equals(model.working().getValue()));
            }
        });
    }

    private static boolean validAddress(TextInputLayout email) {
        return InputValidation.validEmail(InputValidation.email(FormViews.raw(email)));
    }

    private static boolean validLength(TextInputLayout field, int minimum, int maximum) {
        int length = InputValidation.clean(FormViews.raw(field)).length();
        return length >= minimum && length <= maximum;
    }

    private void updateFormActions(boolean working) {
        formActions.forEach((button, valid) -> button.setEnabled(!working && valid.getAsBoolean()));
    }

    private void showWorking(boolean working) {
        binding.progress.setVisibility(working || current == null || current.stage() == Stage.LOADING ? View.VISIBLE : View.GONE);
        for (View control : controls) control.setEnabled(!working);
        updateFormActions(working);
    }

    private void toggleRegistration() {
        registration = !registration;
        model.clearFeedback();
        render();
    }

    private String checkedEmail(TextInputLayout field) {
        String email = InputValidation.email(FormViews.raw(field));
        boolean valid = InputValidation.validEmail(email);
        field.setError(valid ? null : getString(R.string.invalid_email));
        return valid ? email : null;
    }

    private String checkedText(TextInputLayout field, int minimum, int maximum) {
        String clean = InputValidation.clean(FormViews.raw(field));
        if (clean.length() < minimum) field.setError(getString(R.string.required));
        else if (clean.length() > maximum) field.setError(getResources().getQuantityString(R.plurals.too_long, maximum, maximum));
        else {
            field.setError(null);
            return clean;
        }
        return null;
    }
}
