package com.example.comunidadconectada.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.AndroidViewModel;

import com.example.comunidadconectada.R;
import com.example.comunidadconectada.data.AlertSubscription;
import com.example.comunidadconectada.data.CommunityRepository;

import java.util.LinkedHashSet;
import java.util.Set;

public final class AlertChannelViewModel extends AndroidViewModel {
    public AlertChannelViewModel(android.app.Application application) {
        super(application);
    }
    private static final int MAX_RECEIPTS = 300;
    private final MutableLiveData<Integer> status = new MutableLiveData<>(R.string.mqtt_connecting);
    private final MutableLiveData<Integer> received = new MutableLiveData<>(0);
    private final Set<String> receipts = new LinkedHashSet<>();
    private AlertSubscription subscription;
    private String scope = "";
    private boolean running;

    public LiveData<Integer> status() { return status; }
    public LiveData<Integer> receipts() { return received; }
    public boolean received(String id) { return receipts.contains(id); }

    public void start(CommunityRepository repository, String scope) {
        if (!this.scope.equals(scope)) {
            stop();
            receipts.clear();
            this.scope = scope;
        }
        if (running) return;
        if (subscription == null) subscription = new AlertSubscription(getApplication(), repository, status::setValue, this::receive);
        status.setValue(R.string.mqtt_connecting);
        running = true;
        subscription.start();
    }

    public void stop() {
        if (running) subscription.stop();
        running = false;
    }

    private void receive(String id) {
        if (!receipts.add(id)) return;
        if (receipts.size() > MAX_RECEIPTS) receipts.remove(receipts.iterator().next());
        received.setValue(received.getValue() + 1);
    }

    @Override protected void onCleared() {
        if (subscription != null) subscription.dispose();
    }
}
