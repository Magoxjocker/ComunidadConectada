package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.text.InputType;
import android.widget.LinearLayout;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.CommunityRecord;
import com.example.comunidadconectada.domain.InputValidation;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

public final class SalesFragment extends FeedFragment {
    private static final String SELECTION = "categoryFilter";
    private TextInputLayout search;
    private int category;

    @Override protected String collection() { return "marketplace"; }
    @Override protected int emptyMessage() { return R.string.no_sales; }
    @Override protected boolean paginable() { return true; }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) category = savedInstanceState.getInt(SELECTION);
    }

    @Override public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putInt(SELECTION, category);
    }

    @Override protected void addControls(LinearLayout controls) {
        Ui.button(controls, R.string.sale_new, R.drawable.ic_add, () -> edit(null));
        search = FormViews.input(controls, getString(R.string.search), InputType.TYPE_CLASS_TEXT, 1, 442);
        search.getEditText().addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { refreshRows(); }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.all_categories));
        labels.addAll(Labels.SALE_CATEGORIES.forContext(requireContext()));
        com.google.android.material.button.MaterialButton filter = Ui.button(controls, labels.get(category), R.drawable.ic_sales, () -> {});
        filter.setOnClickListener(view -> new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.category)
                .setSingleChoiceItems(labels.toArray(new String[0]), category, (dialog, index) -> {
                    category = index;
                    filter.setText(labels.get(index));
                    refreshRows();
                    dialog.dismiss();
                }).setNegativeButton(R.string.cancel, null).show());
    }

    @Override protected List<CommunityRecord> filter(List<CommunityRecord> records) {
        String query = InputValidation.searchKey(search == null ? "" : FormViews.raw(search));
        return records.stream().filter(record ->
                (category == 0 || Labels.SALE_CATEGORIES.at(category - 1).equals(record.text("category")))
                        && InputValidation.searchKey(record.text("title") + " " + record.text("description")).contains(query))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override protected RecordAdapter.Row row(CommunityRecord record) {
        String price = getString(R.string.price_value, NumberFormat.getIntegerInstance(InputValidation.CHILE).format(record.number("price")));
        String status = Labels.SALE_STATES.of(requireContext(), record.text("status"));
        String metadata = getString(R.string.date_meta, status, price);
        return new RecordAdapter.Row(record.id(), record.text("title"), Ui.preview(record.text("description")), metadata,
                () -> showProduct(record, metadata));
    }

    private void showProduct(CommunityRecord record, String metadata) {
        String details = record.text("description") + "\n\n" + metadata + "\n"
                + Labels.SALE_CATEGORIES.of(requireContext(), record.text("category")) + "\n"
                + author(record) + "\n" + Ui.date(record.number("createdAt"));
        detail(record.text("title"), details, actions -> {
            if (!session().uid().equals(record.text("owner"))) return;
            Ui.button(actions, R.string.edit, R.drawable.ic_tools, () -> edit(record));
            Ui.button(actions, R.string.delete, R.drawable.ic_close, () -> showForm(R.string.delete, R.string.confirm_delete,
                    R.string.delete, R.string.saved, "deleteSale", FormDialog.target("id", record.id())));
        });
    }

    private void edit(CommunityRecord record) {
        Bundle target = record == null ? null : FormDialog.target("id", record.id());
        showForm(record == null ? R.string.sale_new : R.string.sale_edit, 0, R.string.save, R.string.saved, "saveSale", target,
                Field.text("title", R.string.product, text(record, "title"), 100),
                Field.paragraph("description", R.string.description, text(record, "description"), 1, 1500),
                Field.price("price", R.string.price, record == null ? "" : Long.toString(record.number("price"))),
                Labels.SALE_CATEGORIES.field(requireContext(), "category", R.string.category, text(record, "category")),
                Labels.SALE_STATES.field(requireContext(), "status", R.string.status, text(record, "status")));
    }

    private static String text(CommunityRecord record, String field) {
        return record == null ? "" : record.text(field);
    }
}
