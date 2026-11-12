package com.screenassist.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Offline EfficientDet-Lite0 COCO detector. This class accepts only caller-supplied bitmaps. */
public final class Detector implements AutoCloseable {
    public static final class DetectionResult {
        public final Rect box;
        public final float confidence;
        public final String label;

        DetectionResult(Rect box, float confidence, String label) {
            this.box = box;
            this.confidence = confidence;
            this.label = label;
        }
    }

    private static final String TAG = "ScreenAssistDetector";
    private static final String DEFAULT_MODEL = "efficientdet_lite0_int8.tflite";
    private static final int INPUT_SIZE = 320;
    private static final int MAX_DETECTIONS = 25;
    private static final float CONFIDENCE_THRESHOLD = 0.40f;
    private static final float NMS_IOU_THRESHOLD = 0.50f;

    private static final String[] COCO_LABELS = {
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
        "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
        "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
        "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
        "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair",
        "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
        "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink", "refrigerator",
        "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    };

    private final Interpreter interpreter;
    private final String[] labels;
    private final ByteBuffer input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
            .order(ByteOrder.nativeOrder());
    private final float[][][] boxes = new float[1][MAX_DETECTIONS][4];
    private final float[][] scores = new float[1][MAX_DETECTIONS];
    private final float[][] classes = new float[1][MAX_DETECTIONS];
    private final float[] count = new float[1];
    private final int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
    private final Bitmap inputBitmap;
    private final Canvas inputCanvas;
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private float resizeScale;
    private float padX;
    private float padY;

    public Detector(Context context) {
        this(context, DEFAULT_MODEL, COCO_LABELS);
    }

    public Detector(Context context, String modelAsset, String[] labels) {
        this.labels = labels == null ? COCO_LABELS : labels;
        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(2);
            interpreter = new Interpreter(loadModel(context, modelAsset), options);
            validateModelOutputs();
        } catch (IOException e) {
            throw new ModelNotReadyException("Model asset is missing: " + modelAsset
                    + ". Follow MODEL-SETUP.md to install it.", e);
        } catch (RuntimeException e) {
            throw new ModelNotReadyException("Could not initialize the TFLite model: "
                    + e.getMessage(), e);
        }
        inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        inputCanvas = new Canvas(inputBitmap);
    }

    public synchronized List<DetectionResult> analyze(Bitmap source) {
        if (source == null || source.isRecycled()) {
            throw new IllegalArgumentException("Choose a valid image first.");
        }
        prepareInput(source);
        Object[] inputs = {input};
        java.util.Map<Integer, Object> outputs = new java.util.HashMap<>();
        outputs.put(0, boxes);
        outputs.put(1, classes);
        outputs.put(2, scores);
        outputs.put(3, count);
        interpreter.runForMultipleInputsOutputs(inputs, outputs);

        int resultCount = Math.max(0, Math.min(MAX_DETECTIONS, Math.round(count[0])));
        List<DetectionResult> candidates = new ArrayList<>();
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        for (int i = 0; i < resultCount; i++) {
            float confidence = scores[0][i];
            if (Float.isNaN(confidence) || confidence < CONFIDENCE_THRESHOLD) continue;

            float top = (boxes[0][i][0] * INPUT_SIZE - padY) / resizeScale;
            float left = (boxes[0][i][1] * INPUT_SIZE - padX) / resizeScale;
            float bottom = (boxes[0][i][2] * INPUT_SIZE - padY) / resizeScale;
            float right = (boxes[0][i][3] * INPUT_SIZE - padX) / resizeScale;
            int x0 = clamp(Math.round(left), 0, sourceWidth - 1);
            int y0 = clamp(Math.round(top), 0, sourceHeight - 1);
            int x1 = clamp(Math.round(right), x0 + 1, sourceWidth);
            int y1 = clamp(Math.round(bottom), y0 + 1, sourceHeight);
            if (x1 <= x0 || y1 <= y0) continue;

            int classIndex = Math.round(classes[0][i]);
            String label = classIndex >= 0 && classIndex < labels.length
                    ? labels[classIndex] : "class " + classIndex;
            if ("knife".equalsIgnoreCase(label)) continue;
            candidates.add(new DetectionResult(new Rect(x0, y0, x1, y1), confidence, label));
        }
        return applyNms(candidates);
    }

    @Override
    public synchronized void close() {
        interpreter.close();
        inputBitmap.recycle();
    }

    private void validateModelOutputs() {
        int[] inputShape = interpreter.getInputTensor(0).shape();
        if (!java.util.Arrays.equals(inputShape, new int[]{1, INPUT_SIZE, INPUT_SIZE, 3})) {
            throw new IllegalArgumentException("Unexpected input tensor shape: "
                    + java.util.Arrays.toString(inputShape));
        }
        if (interpreter.getInputTensor(0).dataType() != DataType.UINT8) {
            throw new IllegalArgumentException("Model input must be uint8 RGB.");
        }
        int[][] expected = {{1, MAX_DETECTIONS, 4}, {1, MAX_DETECTIONS},
                {1, MAX_DETECTIONS}, {1}};
        if (interpreter.getOutputTensorCount() != expected.length) {
            throw new IllegalArgumentException("Expected 4 model outputs; found "
                    + interpreter.getOutputTensorCount());
        }
        for (int i = 0; i < expected.length; i++) {
            int[] actual = interpreter.getOutputTensor(i).shape();
            if (!java.util.Arrays.equals(actual, expected[i])) {
                throw new IllegalArgumentException("Unexpected output tensor " + i
                        + " shape: " + java.util.Arrays.toString(actual));
            }
        }
    }

    private void prepareInput(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        resizeScale = Math.min((float) INPUT_SIZE / width, (float) INPUT_SIZE / height);
        float scaledWidth = width * resizeScale;
        float scaledHeight = height * resizeScale;
        padX = (INPUT_SIZE - scaledWidth) / 2f;
        padY = (INPUT_SIZE - scaledHeight) / 2f;

        inputCanvas.drawColor(Color.rgb(114, 114, 114));
        android.graphics.RectF destination = new android.graphics.RectF(
                padX, padY, padX + scaledWidth, padY + scaledHeight);
        inputCanvas.drawBitmap(source, null, destination, bitmapPaint);
        inputBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        input.clear();
        for (int pixel : pixels) {
            input.put((byte) Color.red(pixel));
            input.put((byte) Color.green(pixel));
            input.put((byte) Color.blue(pixel));
        }
        input.rewind();
    }

    private static List<DetectionResult> applyNms(List<DetectionResult> input) {
        Collections.sort(input, new Comparator<DetectionResult>() {
            @Override public int compare(DetectionResult a, DetectionResult b) {
                return Float.compare(b.confidence, a.confidence);
            }
        });
        List<DetectionResult> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[input.size()];
        for (int i = 0; i < input.size(); i++) {
            if (suppressed[i]) continue;
            DetectionResult candidate = input.get(i);
            kept.add(candidate);
            for (int j = i + 1; j < input.size(); j++) {
                if (!suppressed[j] && candidate.label.equals(input.get(j).label)
                        && intersectionOverUnion(candidate.box, input.get(j).box) > NMS_IOU_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    private static float intersectionOverUnion(Rect a, Rect b) {
        int left = Math.max(a.left, b.left);
        int top = Math.max(a.top, b.top);
        int right = Math.min(a.right, b.right);
        int bottom = Math.min(a.bottom, b.bottom);
        int width = Math.max(0, right - left);
        int height = Math.max(0, bottom - top);
        float intersection = width * height;
        float union = (float) a.width() * a.height() + (float) b.width() * b.height() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static MappedByteBuffer loadModel(Context context, String assetName) throws IOException {
        File modelFile = new File(context.getFilesDir(), assetName);
        try (InputStream asset = context.getAssets().open(assetName)) {
            long expectedLength = asset.available();
            if (!modelFile.exists() || modelFile.length() != expectedLength) {
                File temporary = new File(context.getFilesDir(), assetName + ".tmp");
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = asset.read(buffer)) != -1) output.write(buffer, 0, read);
                    output.flush();
                }
                if (modelFile.exists() && !modelFile.delete())
                    throw new IOException("Could not replace the cached model file.");
                if (!temporary.renameTo(modelFile)) {
                    temporary.delete();
                    throw new IOException("Could not install the model in app storage.");
                }
            }
        }
        try (FileInputStream stream = new FileInputStream(modelFile)) {
            FileChannel channel = stream.getChannel();
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }
    }
}
