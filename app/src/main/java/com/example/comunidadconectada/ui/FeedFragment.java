package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.comunidadconectada.MainActivity;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.data.CommunityRepository;
import com.example.comunidadconectada.databinding.FragmentFeedBinding;
import com.example.comunidadconectada.viewmodel.FeedViewModel;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Session;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Stage;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public abstract class FeedFragment extends Fragment {
    protected FragmentFeedBinding binding;
    private RecordAdapter adapter;
    private FeedViewModel model;
    private MaterialButton retry;
    private MaterialButton loadOlder;
    private Boolean connected;
    private androidx.appcompat.app.AlertDialog activeDetail;
    private CommunityRepository.Subscription connectionWatcher;

    protected FeedFragment() { super(R.layout.fragment_feed); }

    protected abstract String collection();
    protected abstract int emptyMessage();
    protected abstract RecordAdapter.Row row(CommunityRecord record);
    protected void addControls(LinearLayout controls) {}
    protected boolean paginable() { return false; }
    protected boolean chronological() { return false; }
    protected boolean usesOwnReportPages() { return false; }
    protected List<CommunityRecord> filter(List<CommunityRecord> records) { return records; }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.bind(view);
        if (session() == null || session().stage() != Stage.ACTIVE) return;
        model = new ViewModelProvider(this).get(FeedViewModel.class);
        adapter = new RecordAdapter();
        LinearLayoutManager layout = new LinearLayoutManager(requireContext());
        layout.setStackFromEnd(chronological());
        binding.list.setLayoutManager(layout);
        binding.list.setAdapter(adapter);
        addControls(binding.controls);
        retry = Ui.button(binding.controls, R.string.retry, R.drawable.ic_tools, this::load);
        loadOlder = Ui.button(binding.controls, R.string.load_older, R.drawable.ic_more, model::loadOlder);
        load();
        model.state().observe(getViewLifecycleOwner(), this::render);
        connectionWatcher = CommunityRepository.watch(host().repository().connected(), snapshot -> {
            connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
            refreshRows();
        }, error -> {});
    }

    protected final MainActivity host() { return (MainActivity) requireActivity(); }
    protected final Session session() { return host().currentSession(); }
    protected final DatabaseReference records() { return host().repository().community(session().community()).child(collection()); }

    protected Query listQuery(DatabaseReference collection) { return CommunityRepository.latest(collection); }

    protected Query historyQuery(DatabaseReference collection, CommunityRecord boundary) {
        return collection.orderByChild("createdAt").endBefore((double) boundary.number("createdAt"), boundary.id())
                .limitToLast(CommunityRepository.PAGE_SIZE);
    }

    protected final void refreshRows() {
        if (model != null && binding != null) render(model.state().getValue());
    }

    private void load() {
        DatabaseReference reference = records();
        if (usesOwnReportPages()) model.loadOwnReports(reference, session().uid());
        else model.load(listQuery(reference), paginable() ? boundary -> historyQuery(reference, boundary) : null, chronological());
    }

    private void render(FeedViewModel.State state) {
        List<CommunityRecord> visible = filter(state.records());
        List<CommunityRecord> ordered = new ArrayList<>(visible);
        if (!chronological()) ordered.sort(order());
        List<RecordAdapter.Row> rows = new ArrayList<>();
        for (CommunityRecord record : ordered) rows.add(row(record));
        boolean scrollToEnd = chronological() && !rows.isEmpty()
                && (adapter.getItemCount() == 0 || !binding.list.canScrollVertically(1));
        adapter.submitList(rows, () -> {
            if (binding != null && scrollToEnd) binding.list.scrollToPosition(rows.size() - 1);
        });
        boolean offline = Boolean.FALSE.equals(connected);
        binding.progress.setVisibility((state.loading() && !offline) || state.paging() ? View.VISIBLE : View.GONE);
        retry.setVisibility(state.failed() ? View.VISIBLE : View.GONE);
        loadOlder.setVisibility(state.older() ? View.VISIBLE : View.GONE);
        loadOlder.setEnabled(!state.paging());
        int status = offline ? R.string.offline : state.failed() ? R.string.generic_error
                : state.loading() ? R.string.progress : visible.isEmpty() ? emptyMessage() : 0;
        Ui.status(binding.status, status == 0 ? null : getText(status));
    }

    protected Comparator<CommunityRecord> order() {
        return Comparator.comparingLong((CommunityRecord record) -> record.number("createdAt")).reversed();
    }

    protected final String author(CommunityRecord record) {
        return getString(R.string.attribution, record.text("author"), record.text("unit"));
    }

    protected final void showForm(int title, int caption, int submit, int feedback, String action, Bundle fixed, Field... fields) {
        if (activeDetail != null) activeDetail.dismiss();
        FormDialog.show(getParentFragmentManager(), title, caption, submit, feedback, action, fixed, fields);
    }

    protected final void detail(CharSequence title, CharSequence summary, Consumer<LinearLayout> actions) {
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Ui.dp(requireContext(), 20);
        content.setPadding(padding, 0, padding, padding);
        scroll.addView(content);
        TextView message = Ui.paragraph(content, summary);
        message.setTextIsSelectable(true);
        actions.accept(content);
        if (activeDetail != null) activeDetail.dismiss();
        activeDetail = new MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(scroll)
                .setPositiveButton(R.string.close, null).show();
    }

    protected final void feedback(int message) {
        if (binding != null) Snackbar.make(binding.getRoot(), message, Snackbar.LENGTH_LONG).show();
    }

    @Override public void onDestroyView() {
        if (connectionWatcher != null) connectionWatcher.close();
        if (activeDetail != null) activeDetail.dismiss();
        activeDetail = null;
        if (binding != null) binding.list.setAdapter(null);
        binding = null;
        super.onDestroyView();
    }
}
