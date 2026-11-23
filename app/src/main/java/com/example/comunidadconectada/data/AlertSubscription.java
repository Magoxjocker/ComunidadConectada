package com.example.comunidadconectada.data;

import android.os.Handler;
import android.os.Looper;

import com.example.comunidadconectada.R;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import javax.net.ssl.SSLSocketFactory;

public final class AlertSubscription {
    private static final int QOS = 1;
    private static final int MAX_RETRY_SECONDS = 60;
    private static final int MAX_PAYLOAD_BYTES = 512;

    private final CommunityRepository repository;
    private final android.content.Context context;
    private final IntConsumer connection;
    private final Consumer<String> incoming;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicInteger generation = new AtomicInteger();
    private MqttClient client;

    public AlertSubscription(android.content.Context context, CommunityRepository repository, IntConsumer connection, Consumer<String> incoming) {
        this.context = context.getApplicationContext();
        this.repository = repository;
        this.connection = connection;
        this.incoming = incoming;
    }

    public void start() {
        int current = generation.incrementAndGet();
        connect(current, 2);
    }

    public void stop() {
        generation.incrementAndGet();
        worker.execute(this::closeClient);
    }

    public void dispose() {
        stop();
        worker.shutdown();
    }

    private void connect(int current, int retryDelay) {
        if (generation.get() != current) return;
        status(current, R.string.mqtt_connecting);
        repository.mqttCredentials().addOnCompleteListener(result -> {
            if (generation.get() != current || worker.isShutdown()) return;
            worker.execute(() -> {
                if (generation.get() != current) return;
                if (!result.isSuccessful()) retry(current, retryDelay);
                else if (result.getResult().isEmpty()) status(current, R.string.mqtt_unconfigured);
                else establish(result.getResult(), current, retryDelay);
            });
        });
    }

    private void establish(Map<?, ?> credentials, int current, int retryDelay) {
        closeClient();
        if (!(credentials.get("host") instanceof String host) || !(credentials.get("topic") instanceof String topic)
                || !(credentials.get("username") instanceof String user) || !(credentials.get("token") instanceof String token)) {
            retry(current, retryDelay);
            return;
        }
        try {
            String clientId = "cc" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            client = new MqttClient("ssl://" + host, clientId, new MemoryPersistence());
            client.setTimeToWait(15_000);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
            options.setSocketFactory(createTlsSocketFactory());
            options.setHttpsHostnameVerificationEnabled(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            options.setUserName(user);
            options.setPassword(token.toCharArray());
            client.setCallback(callback(topic, current));
            client.connect(options);
            client.subscribe(topic, QOS);
            status(current, R.string.mqtt_online);
        } catch (Exception unavailable) {
            closeClient();
            retry(current, retryDelay);
        }
    }

    private MqttCallback callback(String subscription, int current) {
        return new MqttCallback() {
            @Override public void connectionLost(Throwable cause) {
                main.post(() -> {
                    if (generation.get() == current && !worker.isShutdown()) worker.execute(() -> retry(current, 2));
                });
            }

            @Override public void messageArrived(String topic, MqttMessage message) {
                if (!subscription.equals(topic) || message.getPayload().length > MAX_PAYLOAD_BYTES) return;
                String id = recordId(message.getPayload());
                if (id != null) main.post(() -> {
                    if (generation.get() == current) incoming.accept(id);
                });
            }

            @Override public void deliveryComplete(IMqttDeliveryToken token) {}
        };
    }

    private static String recordId(byte[] payload) {
        try {
            String id = new JSONObject(new String(payload, StandardCharsets.UTF_8)).getString("id");
            return id.matches("[A-Za-z0-9_-]{1,170}") ? id : null;
        } catch (JSONException invalidPayload) {
            return null;
        }
    }

    private void retry(int current, int delay) {
        if (generation.get() != current || worker.isShutdown()) return;
        status(current, R.string.mqtt_reconnecting);
        worker.schedule(() -> connect(current, Math.min(MAX_RETRY_SECONDS, delay * 2)), delay, TimeUnit.SECONDS);
    }

    private void status(int current, int message) {
        main.post(() -> {
            if (generation.get() == current) connection.accept(message);
        });
    }

    private void closeClient() {
        if (client == null) return;
        try {
            if (client.isConnected()) client.disconnectForcibly(0L, 500L);
            client.close(true);
        } catch (MqttException ignored) {
            // Closing an interrupted connection may race the transport shutdown.
        } finally {
            client = null;
        }
    }

    private SSLSocketFactory createTlsSocketFactory() throws Exception {
        java.security.cert.CertificateFactory factory =
                java.security.cert.CertificateFactory.getInstance("X.509");

        java.security.cert.Certificate certificate;
        try (java.io.InputStream input =
                     context.getResources().openRawResource(R.raw.mosquitto_ca)) {
            certificate = factory.generateCertificate(input);
        }

        java.security.KeyStore store =
                java.security.KeyStore.getInstance(
                        java.security.KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("mosquitto-ca", certificate);

        javax.net.ssl.TrustManagerFactory managers =
                javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        managers.init(store);

        javax.net.ssl.SSLContext ssl =
                javax.net.ssl.SSLContext.getInstance("TLS");
        ssl.init(null, managers.getTrustManagers(), null);

        return ssl.getSocketFactory();
    }
}


