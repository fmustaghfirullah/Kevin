package com.ghfir.whatsapp.kevin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements ImageAnalysis.Analyzer {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private PreviewView viewFinder;
    private View overlay;
    private MaterialCardView resultCard;
    private TextView resultTextClass;
    private TextView resultTextScore;
    private ObjectDetector objectDetector;
    private ExecutorService cameraExecutor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewFinder = findViewById(R.id.view_finder);
        overlay = findViewById(R.id.overlay);
        resultCard = findViewById(R.id.result_card);
        resultTextClass = findViewById(R.id.result_text_class);
        resultTextScore = findViewById(R.id.result_text_score);

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            ObjectDetector.ObjectDetectorOptions options = ObjectDetector.ObjectDetectorOptions.builder()
                    .setMaxResults(1)
                    .setScoreThreshold(0.5f)
                    .build();
            objectDetector = ObjectDetector.createFromFileAndOptions(this, "model.tflite", options);
        } catch (IOException e) {
            Log.e(TAG, "Error initializing object detector.", e);
            runOnUiThread(() -> {
                Toast.makeText(this, "Model tidak ditemukan. Pastikan model.tflite ada di folder assets.", Toast.LENGTH_LONG).show();
            });
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override
    public void analyze(@NonNull ImageProxy image) {
        if (objectDetector == null) {
            image.close();
            return;
        }

        Bitmap bitmap = image.toBitmap();
        if (bitmap == null) {
            image.close();
            return;
        }

        TensorImage tensorImage = TensorImage.fromBitmap(bitmap);

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        tensorImage = imageProcessor.process(tensorImage);

        List<Detection> results = objectDetector.detect(tensorImage);

        runOnUiThread(() -> {
            if (results != null && !results.isEmpty()) {
                Detection firstResult = results.get(0);
                RectF boundingBox = firstResult.getBoundingBox();

                float scaleX = (float) overlay.getWidth() / 300;
                float scaleY = (float) overlay.getHeight() / 300;

                Bitmap overlayBitmap = Bitmap.createBitmap(overlay.getWidth(), overlay.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(overlayBitmap);
                Paint paint = new Paint();

                // Draw bounding box
                paint.setColor(Color.RED);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(8.0f);
                float left = boundingBox.left * scaleX;
                float top = boundingBox.top * scaleY;
                float right = boundingBox.right * scaleX;
                float bottom = boundingBox.bottom * scaleY;
                canvas.drawRect(left, top, right, bottom, paint);

                // Draw label
                String label = firstResult.getCategories().get(0).getLabel() + ": " + String.format("%.2f%%", firstResult.getCategories().get(0).getScore() * 100);
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(60.0f);
                canvas.drawText(label, left, top - 10, paint);

                overlay.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), overlayBitmap));

                // Update result card
                resultCard.setVisibility(View.VISIBLE);
                resultTextClass.setText("Kelas: " + firstResult.getCategories().get(0).getLabel());
                resultTextScore.setText("Kepercayaan: " + String.format("%.2f%%", firstResult.getCategories().get(0).getScore() * 100));

            } else {
                overlay.setBackground(null);
                resultCard.setVisibility(View.GONE);
            }
        });

        image.close();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                // Handle permission denial
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (objectDetector != null) {
            objectDetector.close();
        }
    }
}
