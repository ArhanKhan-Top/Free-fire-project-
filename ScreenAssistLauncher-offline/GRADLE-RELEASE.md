# Android build

This project is an offline image annotator, not a screen-capture service.

## Requirements

- JDK 11
- Gradle 7.5
- Android Gradle Plugin 7.4.2
- Android SDK Platform 32 and Build Tools 32.x
- Network access for the Maven dependency on first Gradle sync
- The EfficientDet-Lite0 model in `assets/` (run `sh scripts/download_model.sh`)

Build a debug APK from the project root:

```bash
gradle :app:assembleDebug
```

Output:

`app/build/outputs/apk/debug/app-debug.apk`

## AIDE

The project retains its Java/XML Eclipse structure and targets Android SDK 32.
Run `sh scripts/setup_aide_tflite.sh` to prepare the TFLite Java class and native runtime files. AIDE must package the matching JNI library for the device ABI. If it cannot, use the Gradle build path.

No Android build is claimed until Gradle/AIDE has completed a real build. No signing keys or credentials belong in this repository.