# Screen Assist — Offline Image Annotator

A native Android Java/XML app for selecting a local image, running TensorFlow Lite object detection, viewing confidence-labelled boxes, and saving an annotated PNG.

## Features

- Opens a local image through Android's system document picker.
- Runs generic object detection on a single background worker.
- Uses aspect-ratio-preserving preprocessing, output tensor-shape validation, confidence filtering, class-aware NMS, and an annotation preview.
- Saves the annotated result through Android's document picker.
- Processes only the image the user explicitly selects. It does not capture the display, run a foreground capture service, create floating windows, or connect to another app.

## TensorFlow Lite setup

- Gradle: the app uses TensorFlow Lite 2.14.0 from Maven Central; Gradle resolves the matching API dependency.
- AIDE: run `sh scripts/setup_aide_tflite.sh`. It installs both Java JAR dependencies and extracts the ARM64 JNI runtime required by the Galaxy Note10+.
- Model installation is separate from the TensorFlow Lite runtime. Do not treat the runtime setup as proof that a model is installed or compatible.

## Build requirements

Android Gradle Plugin 7.4.2, Gradle 7.5, JDK 11, and Android SDK Platform 32.

The app is an offline local-image annotator. It is not a live game-screen analysis tool.