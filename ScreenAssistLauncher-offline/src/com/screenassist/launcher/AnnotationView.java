package com.screenassist.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.Collections;
import java.util.List;

/** Displays a selected local image and model-produced boxes. */
public final class AnnotationView extends View {
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private android.graphics.Bitmap bitmap;
    private List<Detector.DetectionResult> detections = Collections.emptyList();

    public AnnotationView(Context context) {
        super(context);
        boxPaint.setColor(Color.rgb(110, 145, 242));
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
        labelBgPaint.setColor(Color.rgb(18, 28, 43));
        labelBgPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextSize(12f * getResources().getDisplayMetrics().density);
        labelPaint.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void setImage(android.graphics.Bitmap image) {
        bitmap = image;
        detections = Collections.emptyList();
        invalidate();
    }

    public void setDetections(List<Detector.DetectionResult> results) {
        detections = results == null ? Collections.<Detector.DetectionResult>emptyList() : results;
        invalidate();
    }

    public android.graphics.Bitmap getImage() { return bitmap; }
    public List<Detector.DetectionResult> getDetections() { return detections; }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(11, 18, 32));
        if (bitmap == null || bitmap.isRecycled()) return;

        float scale = Math.min((float) getWidth() / bitmap.getWidth(),
                (float) getHeight() / bitmap.getHeight());
        float drawnWidth = bitmap.getWidth() * scale;
        float drawnHeight = bitmap.getHeight() * scale;
        float offsetX = (getWidth() - drawnWidth) / 2f;
        float offsetY = (getHeight() - drawnHeight) / 2f;
        RectF imageRect = new RectF(offsetX, offsetY, offsetX + drawnWidth, offsetY + drawnHeight);
        canvas.drawBitmap(bitmap, null, imageRect, imagePaint);

        float density = getResources().getDisplayMetrics().density;
        for (Detector.DetectionResult result : detections) {
            RectF rect = new RectF(offsetX + result.box.left * scale,
                    offsetY + result.box.top * scale,
                    offsetX + result.box.right * scale,
                    offsetY + result.box.bottom * scale);
            canvas.drawRect(rect, boxPaint);
            String label = result.label + " " + Math.round(result.confidence * 100f) + "%";
            float textWidth = labelPaint.measureText(label);
            float labelHeight = 20f * density;
            float labelTop = Math.max(offsetY, rect.top - labelHeight);
            canvas.drawRect(rect.left, labelTop, rect.left + textWidth + 12f * density,
                    labelTop + labelHeight, labelBgPaint);
            canvas.drawText(label, rect.left + 6f * density,
                    labelTop + 14f * density, labelPaint);
        }
    }
}