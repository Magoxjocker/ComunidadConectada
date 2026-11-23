package com.example.comunidadconectada.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.comunidadconectada.databinding.RowRecordBinding;

import java.util.Objects;

final class RecordAdapter extends ListAdapter<RecordAdapter.Row, RecordAdapter.Holder> {
    record Row(String id, CharSequence title, CharSequence body, CharSequence metadata, int icon, int accent, Runnable open) {
        Row(String id, CharSequence title, CharSequence body, CharSequence metadata, Runnable open) {
            this(id, title, body, metadata, 0, com.example.comunidadconectada.R.color.petroleum, open);
        }
    }

    private static final DiffUtil.ItemCallback<Row> DIFF = new DiffUtil.ItemCallback<>() {
        @Override public boolean areItemsTheSame(@NonNull Row previous, @NonNull Row current) {
            return previous.id().equals(current.id());
        }

        @Override public boolean areContentsTheSame(@NonNull Row previous, @NonNull Row current) {
            return Objects.equals(previous.title(), current.title()) && Objects.equals(previous.body(), current.body())
                    && Objects.equals(previous.metadata(), current.metadata())
                    && previous.icon() == current.icon() && previous.accent() == current.accent();
        }
    };

    RecordAdapter() { super(DIFF); }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(RowRecordBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = getItem(position);
        holder.binding.title.setText(row.title());
        Ui.status(holder.binding.body, row.body());
        Ui.status(holder.binding.metadata, row.metadata());
        int color = holder.binding.metadata.getContext().getColor(row.accent());
        holder.binding.metadata.setTextColor(color);
        holder.binding.metadata.setCompoundDrawablesRelativeWithIntrinsicBounds(row.icon(), 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(holder.binding.metadata, android.content.res.ColorStateList.valueOf(color));
        holder.binding.metadata.setCompoundDrawablePadding(Ui.dp(holder.binding.metadata.getContext(), 6));
        holder.binding.getRoot().setOnClickListener(view -> {
            int current = holder.getBindingAdapterPosition();
            if (current != RecyclerView.NO_POSITION) getItem(current).open().run();
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final RowRecordBinding binding;

        Holder(RowRecordBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
