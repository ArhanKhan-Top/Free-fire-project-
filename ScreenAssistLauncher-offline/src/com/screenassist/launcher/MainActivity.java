package com.screenassist.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Offline image annotation. It never captures another app's screen. */
public class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 1101;
    private static final int REQUEST_SAVE = 1102;
    private static final int BG = Color.rgb(11, 18, 32);
    private static final int PANEL = Color.rgb(18, 28, 43);
    private static final int TEXT = Color.rgb(232, 237, 246);
    private static final int MUTED = Color.rgb(167, 178, 194);
    private static final int BLUE = Color.rgb(80, 121, 218);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AnnotationView preview;
    private TextView status;
    private Button chooseButton;
    private Button detectButton;
    private Button saveButton;
    private List<Detector.DetectionResult> currentDetections = java.util.Collections.emptyList();
    private Bitmap selectedBitmap;
    private Detector detector;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        buildScreen();
    }

    private void buildScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(BG);

        TextView title = new TextView(this);
        title.setText("SCREEN ASSIST");
        title.setTextColor(TEXT);
        title.setTextSize(18);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(38)));

        TextView subtitle = new TextView(this);
        subtitle.setText("OFFLINE IMAGE ANNOTATION");
        subtitle.setTextColor(MUTED);
        subtitle.setTextSize(10);
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(24)));

        preview = new AnnotationView(this);
        preview.setBackgroundColor(PANEL);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        previewParams.topMargin = dp(10);
        previewParams.bottomMargin = dp(12);
        root.addView(preview, previewParams);

        chooseButton = makeButton("CHOOSE IMAGE", true);
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openImagePicker(); }
        });
        root.addView(chooseButton, buttonParams());

        detectButton = makeButton("RUN DETECTION", true);
        detectButton.setEnabled(false);
        detectButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { runDetection(); }
        });
        LinearLayout.LayoutParams detectParams = buttonParams();
        detectParams.topMargin = dp(8);
        root.addView(detectButton, detectParams);

        saveButton = makeButton("SAVE ANNOTATED PNG", false);
        saveButton.setEnabled(false);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { createSaveDocument(); }
        });
        LinearLayout.LayoutParams saveParams = buttonParams();
        saveParams.topMargin = dp(8);
        root.addView(saveButton, saveParams);

        status = new TextView(this);
        status.setText("Choose an image stored on your device.");
        status.setTextColor(MUTED);
        status.setTextSize(12);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0, dp(10), 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(42)));
        setContentView(root);
    }

    private Button makeButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(TEXT);
        button.setBackgroundColor(primary ? BLUE : PANEL);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(-1, dp(48));
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == REQUEST_IMAGE) loadSelectedImage(data.getData());
        else if (requestCode == REQUEST_SAVE) saveAnnotatedImage(data.getData());
    }

    private void loadSelectedImage(final Uri uri) {
        setBusy(true, "Loading selected image…");
        detectButton.setEnabled(false);
        saveButton.setEnabled(false);
        worker.execute(new Runnable() {
            @Override public void run() {
                Bitmap decoded = null;
                try {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        BitmapFactory.decodeStream(in, null, bounds);
                    }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0)
                        throw new IllegalArgumentException("The selected file is not a supported image.");
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 2048);
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    try (InputStream in = getContentResolver().openInputStream(uri)) {
                        decoded = BitmapFactory.decodeStream(in, null, options);
                    }
                    if (decoded == null) throw new IllegalArgumentException("Could not decode this image.");
                    final Bitmap result = decoded;
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            releaseSelectedBitmap();
                            if (isFinishing() || isDestroyed()) {
                                result.recycle();
                                return;
                            }
                            selectedBitmap = result;
                            preview.setImage(result);
                            currentDetections = java.util.Collections.emptyList();
                            setBusy(false, "Image ready: " + result.getWidth() + " × " + result.getHeight());
                            detectButton.setEnabled(true);
                            saveButton.setEnabled(false);
                        }
                    });
                } catch (final Exception error) {
                    if (decoded != null && !decoded.isRecycled()) decoded.recycle();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            setBusy(false, "");
                            detectButton.setEnabled(selectedBitmap != null);
                            showError(error);
                        }
                    });
                }
            }
        });
    }

    private void runDetection() {
        if (selectedBitmap == null || selectedBitmap.isRecycled()) return;
        setBusy(true, "Loading model and analysing image…");
        detectButton.setEnabled(false);
        saveButton.setEnabled(false);
        final Bitmap inputImage = selectedBitmap;
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (detector == null) detector = new Detector(MainActivity.this);
                    final List<Detector.DetectionResult> results = detector.analyze(inputImage);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            currentDetections = results;
                            preview.setDetections(results);
                            setBusy(false, "Finished: " + results.size() + " objects detected (COCO model).");
                            detectButton.setEnabled(true);
                            saveButton.setEnabled(true);
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            setBusy(false, "");
                            detectButton.setEnabled(selectedBitmap != null);
                            showError(error);
                        }
                    });
                }
            }
        });
    }

    private void createSaveDocument() {
        if (selectedBitmap == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_TITLE, "screen-assist-annotated.png");
        startActivityForResult(intent, REQUEST_SAVE);
    }

    private void saveAnnotatedImage(final Uri uri) {
        final Bitmap sourceImage = selectedBitmap;
        final List<Detector.DetectionResult> savedDetections = new java.util.ArrayList<>(currentDetections);
        setBusy(true, "Saving annotated PNG…");
        detectButton.setEnabled(false);
        saveButton.setEnabled(false);
        worker.execute(new Runnable() {
            @Override public void run() {
                try {
                    Bitmap output = Bitmap.createBitmap(sourceImage.getWidth(),
                            sourceImage.getHeight(), Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas canvas = new android.graphics.Canvas(output);
                    canvas.drawBitmap(sourceImage, 0, 0, null);
                    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    paint.setColor(Color.rgb(110, 145, 242));
                    paint.setStyle(android.graphics.Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(3f, sourceImage.getWidth() / 400f));
                    android.graphics.Paint textPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(Math.max(18f, sourceImage.getWidth() / 35f));
                    for (Detector.DetectionResult item : savedDetections) {
                        canvas.drawRect(item.box, paint);
                        canvas.drawText(item.label + " " + Math.round(item.confidence * 100f) + "%",
                                item.box.left, Math.max(textPaint.getTextSize(), item.box.top - 5), textPaint);
                    }
                    try {
                        try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                            if (out == null || !output.compress(Bitmap.CompressFormat.PNG, 100, out))
                                throw new IllegalStateException("Could not write the PNG file.");
                        }
                    } finally {
                        output.recycle();
                    }
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            setBusy(false, "Saved annotated PNG.");
                            detectButton.setEnabled(selectedBitmap != null);
                            saveButton.setEnabled(true);
                        }
                    });
                } catch (final Exception error) {
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (isFinishing() || isDestroyed()) return;
                            setBusy(false, "");
                            detectButton.setEnabled(selectedBitmap != null);
                            saveButton.setEnabled(true);
                            showError(error);
                        }
                    });
                }
            }
        });
    }

    private void setBusy(boolean busy, String message) {
        chooseButton.setEnabled(!busy);
        if (message != null && !message.isEmpty()) status.setText(message);
    }

    private void showError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) message = error.getClass().getSimpleName();
        status.setText("Error: " + message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static int sampleSize(int width, int height, int maximumSide) {
        int sample = 1;
        while (width / sample > maximumSide || height / sample > maximumSide)
            sample *= 2;
        return sample;
    }

    private void releaseSelectedBitmap() {
        if (selectedBitmap != null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
        selectedBitmap = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        worker.execute(new Runnable() {
            @Override public void run() {
                if (detector != null) detector.close();
                if (selectedBitmap != null && !selectedBitmap.isRecycled()) selectedBitmap.recycle();
            }
        });
        worker.shutdown();
        super.onDestroy();
    }
}
