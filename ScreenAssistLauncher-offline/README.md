# Screen Assist — Offline Image Annotator

A native Android Java/XML app for selecting a local image, running TensorFlow Lite object detection, viewing confidence-labelled boxes, and saving an annotated PNG.

## Features

- Opens a local image through Android's system document picker.
- Runs generic EfficientDet-Lite0 COCO inference on a single background worker.
- Uses aspect-ratio-preserving letterbox preprocessing, output tensor-shape validation, confidence filtering, class-aware NMS, and an annotation preview.
- Saves the annotated result through Android's document picker.
- Processes only the image the user explicitly selects. It does not capture the display, run a foreground capture service, create floating windows, or connect to another app.

## Setup

1. Install the model: `sh scripts/download_model.sh` (Apache-2.0; checksum verified).
2. Build with Android Gradle Plugin 7.4.2, Gradle 7.5, JDK 11, Android SDK Platform 32, and TensorFlow Lite 2.14.0.
3. For AIDE, run `sh scripts/setup_aide_tflite.sh` and verify that AIDE packages the correct JNI library for the target ABI. If it cannot, build with Android Studio/Gradle.

The model is generic COCO object detection. It is not trained for game characters or any specialised domain, and no accuracy claim is made for game screenshots.