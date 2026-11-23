package com.example.comunidadconectada.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

public final class OperationViewModel extends ViewModel {
    public record Key(String uid, String community, String action, String target) {}
    public record State(boolean pending, boolean confirmed, Throwable failure, Map<String, String> draft) {}
    public record Notice(Key key, int message, Throwable failure) {}

    private final Map<Key, Operation> operations = new LinkedHashMap<>();
    private final MutableLiveData<List<Notice>> notices = new MutableLiveData<>(List.of());

    public LiveData<State> state(Key key) { return operation(key).state; }

    public LiveData<List<Notice>> notices() { return notices; }

    public void acknowledge(List<Notice> delivered) {
        List<Notice> remaining = new ArrayList<>(notices.getValue());
        if (remaining.removeAll(delivered)) notices.setValue(List.copyOf(remaining));
    }

    public void setVisible(Key key, boolean visible) {
        Operation operation = operations.get(key);
        if (operation != null) operation.visible = visible;
    }

    public void forgetDraft(Key key) {
        Operation operation = operations.get(key);
        if (operation != null && !operation.state.getValue().pending()) operations.remove(key);
    }

    public void submit(Key key, Map<String, String> draft, int feedback, Function<String, Task<?>> write) {
        Operation operation = operation(key);
        if (operation.state.getValue().pending()) return;
        Map<String, String> savedDraft = Map.copyOf(draft);
        operation.state.setValue(new State(true, false, null, savedDraft));
        try {
            write.apply(operation.requestId).addOnCompleteListener(result -> {
                if (result.isSuccessful()) {
                    operations.remove(key, operation);
                    operation.state.setValue(new State(false, true, null, Map.of()));
                    publish(new Notice(key, feedback, null));
                } else {
                    Throwable failure = result.isCanceled() ? new CancellationException() : result.getException();
                    fail(key, operation, savedDraft, failure);
                }
            });
        } catch (RuntimeException error) {
            fail(key, operation, savedDraft, error);
        }
    }

    private void fail(Key key, Operation operation, Map<String, String> draft, Throwable failure) {
        operation.state.setValue(new State(false, false, failure, draft));
        if (!operation.visible) publish(new Notice(key, 0, failure));
    }

    private Operation operation(Key key) {
        return operations.computeIfAbsent(key, ignored -> new Operation());
    }

    private void publish(Notice notice) {
        List<Notice> pending = new ArrayList<>(notices.getValue());
        pending.add(notice);
        notices.setValue(List.copyOf(pending));
    }

    private static final class Operation {
        private final String requestId = UUID.randomUUID().toString();
        private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, false, null, Map.of()));
        private boolean visible;
    }
}
