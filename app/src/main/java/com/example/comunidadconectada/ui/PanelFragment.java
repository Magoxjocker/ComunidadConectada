package com.example.comunidadconectada.ui;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import com.example.comunidadconectada.MainActivity;
import com.example.comunidadconectada.R;
import com.example.comunidadconectada.databinding.FragmentPanelBinding;
import com.example.comunidadconectada.viewmodel.SessionViewModel.Session;

import java.util.function.Consumer;

public abstract class PanelFragment extends Fragment {
    protected FragmentPanelBinding binding;

    protected PanelFragment() {
        super(R.layout.fragment_panel);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        binding = FragmentPanelBinding.bind(view);
        ViewCompat.setAccessibilityHeading(binding.heading, true);
    }

    protected MainActivity host() {
        return (MainActivity) requireActivity();
    }

    protected void showCommunityName(Session session, Consumer<String> render) {
        render.accept(session.community());
        FragmentPanelBinding currentBinding = binding;
        host().repository().community(session.community()).child("metadata").child("displayName").get()
                .addOnSuccessListener(snapshot -> {
                    if (binding != currentBinding || !host().active()) return;
                    Session current = host().currentSession();
                    if (!session.uid().equals(current.uid()) || !session.community().equals(current.community())) return;
                    Object name = snapshot.getValue();
                    if (name instanceof String displayName && !displayName.trim().isEmpty()) render.accept(displayName);
                });
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
