package com.example.esp32security;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class LiveFeedActivity extends AppCompatActivity {

    private ImageView ivLiveFeed;
    private LinearLayout llNoSignal;
    private View liveDotIndicator;
    private TextView tvLiveStatus;
    private TextView tvFpsCounter;
    private TextView tvFrameCount;
    private MaterialButton btnCaptureSnapshot;
    private MaterialButton btnStopFeed;
    private ImageView btnBack;
    private ImageView btnFullscreen;

    private Thread feedThread;
    private volatile boolean isFeeding = false;
    private boolean isFullscreen = false;

    private int frameCount = 0;
    private long lastFpsTime = 0;
    private int fpsFrameCount = 0;

    private Bitmap currentFrame;

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live_feed);

        // Keep screen on during live feed
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initViews();
        startEntranceAnimations();
        startLiveDotPulse();

        btnBack.setOnClickListener(v -> {
            animatePress(v);
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        btnFullscreen.setOnClickListener(v -> {
            animatePress(v);
            toggleFullscreen();
        });

        btnCaptureSnapshot.setOnClickListener(v -> {
            animatePress(v);
            captureCurrentFrame();
        });

        btnStopFeed.setOnClickListener(v -> {
            animatePress(v);
            stopFeed();
        });

        if (MainActivity.isDeviceConnected()) {
            startLiveFeed();
        } else {
            llNoSignal.setVisibility(View.VISIBLE);
        }
    }

    private void initViews() {
        ivLiveFeed = findViewById(R.id.ivLiveFeed);
        llNoSignal = findViewById(R.id.llNoSignal);
        liveDotIndicator = findViewById(R.id.liveDotIndicator);
        tvLiveStatus = findViewById(R.id.tvLiveStatus);
        tvFpsCounter = findViewById(R.id.tvFpsCounter);
        tvFrameCount = findViewById(R.id.tvFrameCount);
        btnCaptureSnapshot = findViewById(R.id.btnCaptureSnapshot);
        btnStopFeed = findViewById(R.id.btnStopFeed);
        btnBack = findViewById(R.id.btnBack);
        btnFullscreen = findViewById(R.id.btnFullscreen);
    }

    private void startEntranceAnimations() {
        // Live feed frame 3D entrance
        ivLiveFeed.setAlpha(0f);
        ivLiveFeed.setScaleX(0.9f);
        ivLiveFeed.setScaleY(0.9f);
        ivLiveFeed.setRotationX(15f);
        ivLiveFeed.setCameraDistance(10000f);
        ivLiveFeed.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .rotationX(0f)
                .setDuration(600)
                .setInterpolator(new OvershootInterpolator(0.6f))
                .setStartDelay(200)
                .start();

        // Buttons slide up
        btnCaptureSnapshot.setAlpha(0f);
        btnCaptureSnapshot.setTranslationY(40f);
        btnCaptureSnapshot.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(400)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();

        btnStopFeed.setAlpha(0f);
        btnStopFeed.setTranslationY(40f);
        btnStopFeed.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(500)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }

    private void startLiveDotPulse() {
        ValueAnimator looper = ValueAnimator.ofFloat(0, 1);
        looper.setDuration(1500);
        looper.setRepeatCount(ValueAnimator.INFINITE);
        looper.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(android.animation.Animator animation) {
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(liveDotIndicator, "scaleX", 1f, 1.8f, 1f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(liveDotIndicator, "scaleY", 1f, 1.8f, 1f);
                ObjectAnimator alpha = ObjectAnimator.ofFloat(liveDotIndicator, "alpha", 1f, 0.3f, 1f);
                android.animation.AnimatorSet set = new android.animation.AnimatorSet();
                set.playTogether(scaleX, scaleY, alpha);
                set.setDuration(1000);
                set.start();
            }
        });
        looper.start();
    }

    private void animatePress(View v) {
        v.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(80)
                .withEndAction(() -> v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .setInterpolator(new OvershootInterpolator(3f))
                        .start())
                .start();
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ==================== LIVE FEED STREAM ====================

    private void startLiveFeed() {
        isFeeding = true;
        lastFpsTime = System.currentTimeMillis();

        // Show the last known frame immediately if available
        Bitmap lastFrame = MainActivity.getLastFrame();
        if (lastFrame != null) {
            ivLiveFeed.setImageBitmap(lastFrame);
            llNoSignal.setVisibility(View.GONE);
        }

        feedThread = new Thread(() -> {
            InputStream stream = MainActivity.getInputStream();
            if (stream == null) {
                uiHandler.post(() -> {
                    llNoSignal.setVisibility(View.VISIBLE);
                    tvLiveStatus.setText("NO SIGNAL");
                });
                return;
            }

            uiHandler.post(() -> {
                llNoSignal.setVisibility(View.GONE);
                tvLiveStatus.setText("LIVE");
            });

            while (isFeeding && MainActivity.isDeviceConnected()) {
                try {
                    // Read 4-byte length header
                    byte[] sizeBytes = new byte[4];
                    int totalRead = 0;
                    long startTime = System.currentTimeMillis();

                    while (totalRead < 4 && isFeeding) {
                        if (stream.available() > 0) {
                            int read = stream.read(sizeBytes, totalRead, 4 - totalRead);
                            if (read > 0) totalRead += read;
                        } else {
                            if (System.currentTimeMillis() - startTime > 8000) break;
                            Thread.sleep(15);
                        }
                    }

                    if (totalRead < 4) continue;

                    // Check if this is an ALERT header instead of a frame
                    String check = new String(sizeBytes);
                    if (check.startsWith("ALER")) {
                        // Skip the rest of the alert header, it'll be handled by MainActivity's listener
                        continue;
                    }

                    int frameSize = (sizeBytes[0] & 0xFF) |
                                    ((sizeBytes[1] & 0xFF) << 8) |
                                    ((sizeBytes[2] & 0xFF) << 16) |
                                    ((sizeBytes[3] & 0xFF) << 24);

                    if (frameSize <= 0 || frameSize > 2_000_000) continue;

                    // Read JPEG frame bytes
                    byte[] frameBytes = new byte[frameSize];
                    int bytesRead = 0;
                    startTime = System.currentTimeMillis();

                    while (bytesRead < frameSize && isFeeding) {
                        if (stream.available() > 0) {
                            int read = stream.read(frameBytes, bytesRead, frameSize - bytesRead);
                            if (read > 0) bytesRead += read;
                        } else {
                            if (System.currentTimeMillis() - startTime > 8000) break;
                            Thread.sleep(5);
                        }
                    }

                    if (bytesRead < frameSize) continue;

                    // Decode & display
                    Bitmap frame = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.length);
                    if (frame != null) {
                        currentFrame = frame;
                        frameCount++;
                        fpsFrameCount++;

                        uiHandler.post(() -> {
                            ivLiveFeed.setImageBitmap(frame);
                            tvFrameCount.setText("Frames: " + frameCount);

                            // Calculate FPS every second
                            long now = System.currentTimeMillis();
                            if (now - lastFpsTime >= 1000) {
                                tvFpsCounter.setText(fpsFrameCount + " FPS");
                                fpsFrameCount = 0;
                                lastFpsTime = now;
                            }
                        });
                    }
                } catch (Exception e) {
                    uiHandler.post(() -> {
                        tvLiveStatus.setText("SIGNAL LOST");
                        tvLiveStatus.setTextColor(0xFFF59E0B);
                    });
                    break;
                }
            }

            if (!MainActivity.isDeviceConnected()) {
                uiHandler.post(() -> {
                    llNoSignal.setVisibility(View.VISIBLE);
                    tvLiveStatus.setText("DISCONNECTED");
                    tvLiveStatus.setTextColor(0xFFEF4444);
                });
            }
        });
        feedThread.start();
    }

    private void stopFeed() {
        isFeeding = false;
        if (feedThread != null) feedThread.interrupt();
        tvLiveStatus.setText("PAUSED");
        tvLiveStatus.setTextColor(0xFFF59E0B);

        // 3D rotation stop animation on the feed view
        ivLiveFeed.setCameraDistance(10000f);
        ObjectAnimator rotateOut = ObjectAnimator.ofFloat(ivLiveFeed, "rotationX", 0f, 8f);
        rotateOut.setDuration(300);
        rotateOut.setInterpolator(new DecelerateInterpolator());
        rotateOut.start();

        btnStopFeed.setText("▶ Resume");
        btnStopFeed.setOnClickListener(v -> {
            animatePress(v);
            resumeFeed();
        });
    }

    private void resumeFeed() {
        // 3D rotation resume animation
        ObjectAnimator rotateIn = ObjectAnimator.ofFloat(ivLiveFeed, "rotationX", 8f, 0f);
        rotateIn.setDuration(400);
        rotateIn.setInterpolator(new OvershootInterpolator(1f));
        rotateIn.start();

        tvLiveStatus.setText("LIVE");
        tvLiveStatus.setTextColor(0xFFEF4444);

        btnStopFeed.setText("⏹ Stop Feed");
        btnStopFeed.setOnClickListener(v -> {
            animatePress(v);
            stopFeed();
        });

        if (MainActivity.isDeviceConnected()) {
            startLiveFeed();
        }
    }

    private void captureCurrentFrame() {
        if (currentFrame == null) {
            Toast.makeText(this, "No frame to capture", Toast.LENGTH_SHORT).show();
            return;
        }

        // Flash animation on capture
        View flashOverlay = new View(this);
        flashOverlay.setBackgroundColor(0x88FFFFFF);
        flashOverlay.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));

        android.widget.FrameLayout root = (android.widget.FrameLayout) ivLiveFeed.getParent();
        if (root != null) {
            root.addView(flashOverlay);
            flashOverlay.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> root.removeView(flashOverlay))
                    .start();
        }

        // Save to gallery
        saveFrameToGallery(currentFrame);
    }

    private void saveFrameToGallery(Bitmap bitmap) {
        String filename = "LiveCapture_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            contentValues.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DoorbellSnapshots");

            Uri imageUri = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            try {
                if (imageUri != null) fos = getContentResolver().openOutputStream(imageUri);
            } catch (IOException e) { e.printStackTrace(); }
        }

        if (fos != null) {
            try {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos);
                fos.close();
                Toast.makeText(this, "📸 Snapshot captured to Gallery!", Toast.LENGTH_SHORT).show();
            } catch (IOException e) { e.printStackTrace(); }
        }
    }

    @Override
    protected void onDestroy() {
        isFeeding = false;
        if (feedThread != null) feedThread.interrupt();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
