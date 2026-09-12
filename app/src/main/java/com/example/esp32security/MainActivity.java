package com.example.esp32security;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final String CHANNEL_ID = "security_alerts_channel";
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int PERMISSION_REQUEST_CODE = 101;
    private static final String ALERT_HEADER = "ALERT:HUMAN";

    // UI Elements
    private TextView tvStatus;
    private View statusDot;
    private MaterialButton btnConnect;
    private MaterialButton btnOpenLiveFeed;
    private LinearLayout feedContainer;
    private View llEmptyState;
    private ImageView ivShieldIcon;
    private ImageView ivMiniPreview;
    private View llCameraOff;
    private View livePulseDot;
    private MaterialCardView connectionCard;
    private MaterialCardView liveFeedCard;

    // Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private static BluetoothSocket bluetoothSocket;
    private static InputStream inputStream;
    private static volatile boolean isConnected = false;
    private Thread workerThread;

    // Alerts
    private TextToSpeech tts;
    private Vibrator vibrator;

    // Animation
    private Handler animHandler = new Handler(Looper.getMainLooper());
    private int feedCardCount = 0;

    // Last frame for live preview sharing
    private static Bitmap lastFrame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initBluetooth();
        createNotificationChannel();
        checkAndRequestPermissions();
        startEntranceAnimations();

        btnConnect.setOnClickListener(v -> {
            animateButtonPress(v);
            if (!isConnected) {
                findAndConnectESP32();
            } else {
                disconnectBT();
            }
        });

        btnOpenLiveFeed.setOnClickListener(v -> {
            animateButtonPress(v);
            if (isConnected) {
                Intent intent = new Intent(this, LiveFeedActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            } else {
                Toast.makeText(this, "Connect to ESP32 first", Toast.LENGTH_SHORT).show();
                // Shake the connection card to guide user
                shakeView(connectionCard);
            }
        });
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        statusDot = findViewById(R.id.statusDot);
        btnConnect = findViewById(R.id.btnConnect);
        btnOpenLiveFeed = findViewById(R.id.btnOpenLiveFeed);
        feedContainer = findViewById(R.id.feedContainer);
        llEmptyState = findViewById(R.id.llEmptyState);
        ivShieldIcon = findViewById(R.id.ivShieldIcon);
        ivMiniPreview = findViewById(R.id.ivMiniPreview);
        llCameraOff = findViewById(R.id.llCameraOff);
        livePulseDot = findViewById(R.id.livePulseDot);
        connectionCard = findViewById(R.id.connectionCard);
        liveFeedCard = findViewById(R.id.liveFeedCard);
    }

    private void initBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        tts = new TextToSpeech(this, this);
    }

    // ==================== 3D ENTRANCE ANIMATIONS ====================

    private void startEntranceAnimations() {
        View headerBar = findViewById(R.id.headerBar);

        // Shield icon 3D spin
        ivShieldIcon.setAlpha(0f);
        ivShieldIcon.setRotationY(-180f);
        ivShieldIcon.animate()
                .alpha(1f)
                .rotationY(0f)
                .setDuration(1000)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .setStartDelay(200)
                .start();

        // Header slide from top with 3D tilt
        headerBar.setAlpha(0f);
        headerBar.setTranslationY(-80f);
        headerBar.setRotationX(15f);
        headerBar.animate()
                .alpha(1f)
                .translationY(0f)
                .rotationX(0f)
                .setDuration(700)
                .setInterpolator(new DecelerateInterpolator(2f))
                .setStartDelay(100)
                .start();

        // Connection card 3D entrance: scale + rotateX
        connectionCard.setAlpha(0f);
        connectionCard.setScaleX(0.85f);
        connectionCard.setScaleY(0.85f);
        connectionCard.setRotationX(20f);
        connectionCard.setTranslationY(60f);
        connectionCard.setCameraDistance(connectionCard.getWidth() > 0 ? connectionCard.getWidth() * 12f : 8000f);
        connectionCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotationX(0f)
                .translationY(0f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .setStartDelay(350)
                .start();

        // Live feed card 3D entrance
        liveFeedCard.setAlpha(0f);
        liveFeedCard.setScaleX(0.85f);
        liveFeedCard.setScaleY(0.85f);
        liveFeedCard.setRotationX(25f);
        liveFeedCard.setTranslationY(80f);
        liveFeedCard.setCameraDistance(8000f);
        liveFeedCard.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotationX(0f)
                .translationY(0f)
                .setDuration(800)
                .setInterpolator(new OvershootInterpolator(0.8f))
                .setStartDelay(500)
                .start();

        // Empty state fade in
        llEmptyState.setAlpha(0f);
        llEmptyState.animate()
                .alpha(1f)
                .setDuration(600)
                .setStartDelay(800)
                .start();
    }

    // ==================== MICRO-ANIMATIONS ====================

    private void animateButtonPress(View view) {
        view.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(80)
                .withEndAction(() -> view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new OvershootInterpolator(3f))
                        .start())
                .start();
    }

    private void shakeView(View view) {
        ObjectAnimator shakeX = ObjectAnimator.ofFloat(view, "translationX",
                0f, -12f, 12f, -10f, 10f, -6f, 6f, 0f);
        shakeX.setDuration(500);
        shakeX.setInterpolator(new DecelerateInterpolator());
        shakeX.start();
    }

    private void animateStatusDotPulse() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(statusDot, "scaleX", 1f, 1.6f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(statusDot, "scaleY", 1f, 1.6f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(statusDot, "alpha", 1f, 0.5f, 1f);

        AnimatorSet pulseSet = new AnimatorSet();
        pulseSet.playTogether(scaleX, scaleY, alpha);
        pulseSet.setDuration(1500);

        ValueAnimator looper = ValueAnimator.ofFloat(0, 1);
        looper.setDuration(2000);
        looper.setRepeatCount(ValueAnimator.INFINITE);
        looper.addUpdateListener(a -> {});
        looper.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                if (isConnected) {
                    pulseSet.start();
                }
            }
        });
        looper.start();
    }

    private void startLiveDotPulse() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(livePulseDot, "scaleX", 1f, 1.5f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(livePulseDot, "scaleY", 1f, 1.5f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(livePulseDot, "alpha", 1f, 0.3f, 1f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY, alpha);
        set.setDuration(1200);
        set.setInterpolator(new DecelerateInterpolator());

        ValueAnimator repeater = ValueAnimator.ofFloat(0, 1);
        repeater.setDuration(1500);
        repeater.setRepeatCount(ValueAnimator.INFINITE);
        repeater.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                set.start();
            }
        });
        repeater.start();
    }

    private void animate3DCardEntrance(View cardView, int delayMs) {
        cardView.setAlpha(0f);
        cardView.setTranslationY(100f);
        cardView.setRotationX(30f);
        cardView.setScaleX(0.9f);
        cardView.setScaleY(0.9f);
        cardView.setCameraDistance(12000f);

        cardView.animate()
                .alpha(1f)
                .translationY(0f)
                .rotationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700)
                .setStartDelay(delayMs)
                .setInterpolator(new OvershootInterpolator(0.6f))
                .start();
    }

    private void animateConnectionSuccess() {
        // 3D flip the connection card
        connectionCard.setCameraDistance(12000f);
        ObjectAnimator flipOut = ObjectAnimator.ofFloat(connectionCard, "rotationY", 0f, 90f);
        flipOut.setDuration(200);
        flipOut.setInterpolator(new DecelerateInterpolator());

        ObjectAnimator flipIn = ObjectAnimator.ofFloat(connectionCard, "rotationY", -90f, 0f);
        flipIn.setDuration(300);
        flipIn.setInterpolator(new OvershootInterpolator(1.5f));

        AnimatorSet flipSet = new AnimatorSet();
        flipSet.playSequentially(flipOut, flipIn);
        flipSet.start();

        // Animate shield icon glow
        ObjectAnimator shieldPulse = ObjectAnimator.ofFloat(ivShieldIcon, "scaleX", 1f, 1.3f, 1f);
        ObjectAnimator shieldPulseY = ObjectAnimator.ofFloat(ivShieldIcon, "scaleY", 1f, 1.3f, 1f);
        AnimatorSet shieldSet = new AnimatorSet();
        shieldSet.playTogether(shieldPulse, shieldPulseY);
        shieldSet.setDuration(600);
        shieldSet.setStartDelay(300);
        shieldSet.start();
    }

    // ==================== PERMISSIONS ====================

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale.US);
        }
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    // ==================== BLUETOOTH ====================

    private void findAndConnectESP32() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Please enable Bluetooth first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestPermissions();
            return;
        }

        updateStatusUI("Scanning...", R.drawable.status_dot_disconnected, R.color.status_scanning_text, "Scanning...");

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        BluetoothDevice espDevice = null;

        if (pairedDevices != null) {
            for (BluetoothDevice device : pairedDevices) {
                String name = device.getName();
                if (name != null && (name.contains("ESP32") || name.contains("DoorSecurity") || name.contains("Security"))) {
                    espDevice = device;
                    break;
                }
            }
            if (espDevice == null && !pairedDevices.isEmpty()) {
                espDevice = pairedDevices.iterator().next();
            }
        }

        if (espDevice != null) {
            connectToDevice(espDevice);
        } else {
            updateStatusUI("Not Paired", R.drawable.status_dot_disconnected, R.color.status_disconnected_text, "Connect");
            Toast.makeText(this, "No paired ESP32 found. Pair in Android Bluetooth settings.", Toast.LENGTH_LONG).show();
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                bluetoothSocket.connect();
                inputStream = bluetoothSocket.getInputStream();

                isConnected = true;
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateStatusUI("Active & Streaming", R.drawable.status_dot_connected, R.color.status_connected_text, "Disconnect");
                    animateConnectionSuccess();
                    animateStatusDotPulse();
                    startLiveDotPulse();

                    // Show live feed card camera
                    llCameraOff.setVisibility(View.GONE);
                    TextView liveFeedStatus = findViewById(R.id.tvLiveFeedStatus);
                    if (liveFeedStatus != null) liveFeedStatus.setText("STREAMING");
                });

                startDataListener();

            } catch (IOException e) {
                isConnected = false;
                new Handler(Looper.getMainLooper()).post(() -> {
                    updateStatusUI("Connection Failed", R.drawable.status_dot_disconnected, R.color.status_disconnected_text, "Connect");
                    shakeView(connectionCard);
                });
            }
        }).start();
    }

    // ==================== DATA STREAM LISTENER ====================

    private void startDataListener() {
        workerThread = new Thread(() -> {
            byte[] headerBuffer = new byte[1024];

            while (!Thread.currentThread().isInterrupted() && isConnected) {
                try {
                    int available = inputStream.available();
                    if (available > 0) {
                        int bytesRead = inputStream.read(headerBuffer);
                        if (bytesRead > 0) {
                            String incomingChunk = new String(headerBuffer, 0, bytesRead);
                            if (incomingChunk.contains(ALERT_HEADER)) {
                                triggerAlertActions();

                                byte[] sizeBytes = readExactBytes(4);
                                if (sizeBytes != null) {
                                    int imageSize = (sizeBytes[0] & 0xFF) |
                                                    ((sizeBytes[1] & 0xFF) << 8) |
                                                    ((sizeBytes[2] & 0xFF) << 16) |
                                                    ((sizeBytes[3] & 0xFF) << 24);

                                    if (imageSize > 0 && imageSize < 5_000_000) {
                                        byte[] jpegBytes = readExactBytes(imageSize);
                                        if (jpegBytes != null && jpegBytes.length == imageSize) {
                                            Bitmap frame = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
                                            if (frame != null) {
                                                lastFrame = frame;
                                                new Handler(Looper.getMainLooper()).post(() -> {
                                                    processIncomingSnapshot(jpegBytes);
                                                    updateMiniPreview(frame);
                                                });
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Continuous live frames: try reading as a live stream frame
                                // Format: 4-byte length followed by JPEG bytes
                                // This handles non-alert continuous frames for live preview
                                tryReadLiveFrame();
                            }
                        }
                    } else {
                        Thread.sleep(20);
                    }
                } catch (Exception e) {
                    isConnected = false;
                    new Handler(Looper.getMainLooper()).post(() ->
                        updateStatusUI("Disconnected", R.drawable.status_dot_disconnected, R.color.status_disconnected_text, "Connect"));
                    break;
                }
            }
        });
        workerThread.start();
    }

    private void tryReadLiveFrame() {
        try {
            if (inputStream.available() >= 4) {
                byte[] sizeBytes = new byte[4];
                int read = inputStream.read(sizeBytes);
                if (read == 4) {
                    int frameSize = (sizeBytes[0] & 0xFF) |
                                    ((sizeBytes[1] & 0xFF) << 8) |
                                    ((sizeBytes[2] & 0xFF) << 16) |
                                    ((sizeBytes[3] & 0xFF) << 24);

                    if (frameSize > 0 && frameSize < 2_000_000) {
                        byte[] frameBytes = readExactBytes(frameSize);
                        if (frameBytes != null) {
                            Bitmap frame = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.length);
                            if (frame != null) {
                                lastFrame = frame;
                                new Handler(Looper.getMainLooper()).post(() -> updateMiniPreview(frame));
                            }
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private void updateMiniPreview(Bitmap bitmap) {
        if (ivMiniPreview != null) {
            ivMiniPreview.setImageBitmap(bitmap);
            if (llCameraOff != null && llCameraOff.getVisibility() == View.VISIBLE) {
                llCameraOff.setVisibility(View.GONE);
            }
        }
    }

    private byte[] readExactBytes(int totalBytesToRead) throws IOException {
        byte[] buffer = new byte[totalBytesToRead];
        int bytesReadTotal = 0;
        long startTime = System.currentTimeMillis();

        while (bytesReadTotal < totalBytesToRead && isConnected) {
            if (inputStream.available() > 0) {
                int read = inputStream.read(buffer, bytesReadTotal, totalBytesToRead - bytesReadTotal);
                if (read > 0) bytesReadTotal += read;
            } else {
                if (System.currentTimeMillis() - startTime > 5000) return null;
                try { Thread.sleep(10); } catch (InterruptedException e) { return null; }
            }
        }
        return buffer;
    }

    // ==================== ALERT PIPELINE ====================

    private void triggerAlertActions() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (tts != null) {
                tts.speak("Someone is on the door, please check", TextToSpeech.QUEUE_FLUSH, null, null);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(1000);
                }
            }
        });
    }

    private void processIncomingSnapshot(byte[] jpegBytes) {
        Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        if (bitmap == null) return;

        saveImageToGallery(bitmap);
        sendSystemNotification(bitmap);
        addMessageToFeed(bitmap);
    }

    private void addMessageToFeed(Bitmap bitmap) {
        if (llEmptyState.getVisibility() == View.VISIBLE) {
            llEmptyState.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> llEmptyState.setVisibility(View.GONE))
                    .start();
        }

        String timestamp = new SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(new Date());

        View cardView = LayoutInflater.from(this).inflate(R.layout.item_alert_card, feedContainer, false);

        TextView tvTimestamp = cardView.findViewById(R.id.tvCardTimestamp);
        ImageView ivSnapshot = cardView.findViewById(R.id.ivCardSnapshot);
        TextView tvDescription = cardView.findViewById(R.id.tvCardDescription);

        tvTimestamp.setText(timestamp);
        ivSnapshot.setImageBitmap(bitmap);
        tvDescription.setText("Human detected at front door. Image archived to Gallery under Pictures/DoorbellSnapshots.");

        feedContainer.addView(cardView, 0);
        feedCardCount++;

        // 3D entrance animation for the new card
        animate3DCardEntrance(cardView, 50);
    }

    private void sendSystemNotification(Bitmap bitmap) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("\uD83D\uDEA8 Doorbell Alert!")
                .setContentText("Someone is on the door, please check!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(new NotificationCompat.BigPictureStyle().bigPicture(bitmap))
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void saveImageToGallery(Bitmap bitmap) {
        String filename = "Doorbell_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DoorbellSnapshots");

            Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            try {
                if (imageUri != null) fos = getContentResolver().openOutputStream(imageUri);
            } catch (IOException e) { e.printStackTrace(); }
        }

        if (fos != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();
                Toast.makeText(this, "Snapshot saved to Gallery!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Security Alerts", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Live image alerts from ESP32 security camera");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    // ==================== STATUS UI ====================

    private void updateStatusUI(final String statusText, final int dotDrawable, final int textColorRes, final String buttonText) {
        new Handler(Looper.getMainLooper()).post(() -> {
            tvStatus.setText(statusText);
            tvStatus.setTextColor(ContextCompat.getColor(this, textColorRes));
            statusDot.setBackgroundResource(dotDrawable);
            btnConnect.setText(buttonText);

            if (isConnected) {
                btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.accent_red));
            } else {
                btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.primary));
            }
        });
    }

    private void disconnectBT() {
        isConnected = false;
        if (workerThread != null) workerThread.interrupt();
        try {
            if (inputStream != null) inputStream.close();
            if (bluetoothSocket != null) bluetoothSocket.close();
        } catch (IOException ignored) {}

        updateStatusUI("Offline", R.drawable.status_dot_disconnected, R.color.status_disconnected_text, "Connect");

        // Show camera off state
        if (llCameraOff != null) llCameraOff.setVisibility(View.VISIBLE);
        if (ivMiniPreview != null) ivMiniPreview.setImageBitmap(null);

        // Connection card disconnect animation
        connectionCard.setCameraDistance(12000f);
        ObjectAnimator flip = ObjectAnimator.ofFloat(connectionCard, "rotationY", 0f, 360f);
        flip.setDuration(600);
        flip.setInterpolator(new DecelerateInterpolator());
        flip.start();
    }

    // ==================== STATIC ACCESSORS FOR LiveFeedActivity ====================

    static BluetoothSocket getBluetoothSocket() { return bluetoothSocket; }
    static InputStream getInputStream() { return inputStream; }
    static boolean isDeviceConnected() { return isConnected; }
    static Bitmap getLastFrame() { return lastFrame; }

    @Override
    protected void onDestroy() {
        disconnectBT();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}