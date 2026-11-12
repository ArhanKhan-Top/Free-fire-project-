#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
VERSION="2.14.0"
BASE_URL="https://repo.maven.apache.org/maven2/org/tensorflow"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/tflite-aide.XXXXXX")"
RUNTIME_AAR="$TMP_DIR/tensorflow-lite-$VERSION.aar"
API_AAR="$TMP_DIR/tensorflow-lite-api-$VERSION.aar"

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT HUP INT TERM

command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }

mkdir -p "$ROOT/libs/arm64-v8a" "$ROOT/app/src/main/jniLibs/arm64-v8a"

curl -fL --retry 3 "$BASE_URL/tensorflow-lite/$VERSION/tensorflow-lite-$VERSION.aar" -o "$RUNTIME_AAR"
curl -fL --retry 3 "$BASE_URL/tensorflow-lite-api/$VERSION/tensorflow-lite-api-$VERSION.aar" -o "$API_AAR"

unzip -tq "$RUNTIME_AAR" >/dev/null
unzip -tq "$API_AAR" >/dev/null
unzip -p "$RUNTIME_AAR" classes.jar > "$ROOT/libs/tensorflow-lite.jar"
unzip -p "$API_AAR" classes.jar > "$ROOT/libs/tensorflow-lite-api.jar"

test -s "$ROOT/libs/tensorflow-lite.jar" || { echo "TensorFlow Lite runtime classes were not extracted." >&2; exit 1; }
test -s "$ROOT/libs/tensorflow-lite-api.jar" || { echo "TensorFlow Lite API classes were not extracted." >&2; exit 1; }

unzip -p "$RUNTIME_AAR" jni/arm64-v8a/libtensorflowlite_jni.so > "$ROOT/libs/arm64-v8a/libtensorflowlite_jni.so"
test -s "$ROOT/libs/arm64-v8a/libtensorflowlite_jni.so" || {
  echo "The runtime AAR does not contain the required arm64-v8a JNI library." >&2
  exit 1
}
cp "$ROOT/libs/arm64-v8a/libtensorflowlite_jni.so" "$ROOT/app/src/main/jniLibs/arm64-v8a/libtensorflowlite_jni.so"

echo "TensorFlow Lite $VERSION AIDE dependencies installed:"
echo "  libs/tensorflow-lite.jar"
echo "  libs/tensorflow-lite-api.jar"
echo "  libs/arm64-v8a/libtensorflowlite_jni.so"
echo "  app/src/main/jniLibs/arm64-v8a/libtensorflowlite_jni.so"
