package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.comunidadconectada.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.function.IntConsumer;

public final class AccessibilityFragment extends PanelFragment {
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PreferenceStore preferences = new PreferenceStore(requireContext());
        binding.heading.setText(R.string.accessibility);
        Ui.status(binding.summary, getText(R.string.access_caption));
        options(R.string.text_size, new int[]{R.string.text_normal, R.string.text_large, R.string.text_xlarge},
                preferences.textSize(), selected -> {
                    preferences.setTextSize(selected);
                    host().recreate();
                });
        options(R.string.dark_mode, new int[]{R.string.follow_system, R.string.dark_enabled, R.string.dark_disabled},
                preferences.nightMode(), selected -> {
                    preferences.setNightMode(selected);
                    preferences.applyNightMode();
                });
        MaterialSwitch contrast = new MaterialSwitch(requireContext());
        contrast.setText(R.string.high_contrast);
        contrast.setMinHeight(Ui.dp(requireContext(), 48));
        contrast.setChecked(preferences.highContrast());
        contrast.setOnCheckedChangeListener((button, checked) -> {
            preferences.setHighContrast(checked);
            host().recreate();
        });
        binding.actions.addView(contrast);
    }

    private void options(int title, int[] choices, int selected, IntConsumer changed) {
        CharSequence[] labels = new CharSequence[choices.length];
        for (int index = 0; index < choices.length; index++) labels[index] = getText(choices[index]);
        com.google.android.material.button.MaterialButton button = Ui.button(binding.actions,
                getString(R.string.date_meta, getText(title), labels[selected]), R.drawable.ic_access, () -> {});
        int[] active = {selected};
        button.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title).setSingleChoiceItems(labels, active[0], (dialog, selection) -> {
                    dialog.dismiss();
                    if (selection != active[0]) {
                        active[0] = selection;
                        button.setText(getString(R.string.date_meta, getText(title), labels[selection]));
                        changed.accept(selection);
                    }
                }).setNegativeButton(R.string.cancel, null).show());
    }
}
