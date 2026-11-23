package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.viewmodel.AlertChannelViewModel;

public final class AlertsFragment extends FeedFragment {
    private AlertChannelViewModel channel;
    private TextView mqtt;

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        channel = new ViewModelProvider(requireActivity()).get(AlertChannelViewModel.class);
        super.onViewCreated(view, savedInstanceState);
        if (mqtt == null) return;
        channel.status().observe(getViewLifecycleOwner(), status -> mqtt.setText(status));
        channel.receipts().observe(getViewLifecycleOwner(), receipt -> refreshRows());
    }

    @Override protected String collection() { return "alerts"; }
    @Override protected int emptyMessage() { return R.string.no_alerts; }
    @Override protected boolean paginable() { return true; }

    @Override protected void addControls(LinearLayout controls) {
        Ui.emergency(Ui.button(controls, R.string.alert_new, R.drawable.ic_alert,
                () -> openSos(getParentFragmentManager(), requireContext())));
        mqtt = Ui.paragraph(controls, getText(R.string.mqtt_connecting));
        mqtt.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    static void openSos(androidx.fragment.app.FragmentManager manager, android.content.Context context) {
        FormDialog.show(manager, R.string.alert_confirm, R.string.alert_guidance, R.string.send, R.string.alert_saved,
                "createAlert", null, Labels.ALERT_CATEGORIES.field(context, "category", R.string.alert_type, ""),
                Field.paragraph("description", R.string.description, "", 0, 1000));
    }

    @Override protected RecordAdapter.Row row(CommunityRecord alert) {
        String category = Labels.ALERT_CATEGORIES.of(requireContext(), alert.text("category"));
        String status = Labels.ALERT_STATES.of(requireContext(), alert.text("status"));
        String metadata = getString(R.string.date_meta, status, delivery(alert));
        return new RecordAdapter.Row(alert.id(), category, Ui.preview(alert.text("description")), metadata,
                () -> openAlert(alert, category, metadata));
    }

    private String delivery(CommunityRecord alert) {
        if (channel.received(alert.id())) return getString(R.string.delivery_received);
        return getString("published".equals(alert.text("delivery")) ? R.string.delivery_published : R.string.delivery_pending);
    }

    private void openAlert(CommunityRecord alert, String title, String metadata) {
        String details = metadata + "\n\n" + alert.text("description") + "\n\n"
                + author(alert) + "\n" + Ui.date(alert.number("createdAt"));
        detail(title, details, actions -> {
            String state = alert.text("status");
            if (!session().administrator() || "closed".equals(state)) return;
            if ("active".equals(state)) {
                Ui.button(actions, R.string.alert_attend, R.drawable.ic_alert, () -> update(alert, "attended", R.string.alert_attend));
            }
            Ui.button(actions, R.string.alert_close, R.drawable.ic_close, () -> update(alert, "closed", R.string.alert_close));
        });
    }

    private void update(CommunityRecord alert, String state, int title) {
        Bundle target = FormDialog.target("id", alert.id());
        target.putString("status", state);
        showForm(title, R.string.alert_update_confirm, R.string.confirm, R.string.saved, "setAlertStatus", target);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        mqtt = null;
    }
}
