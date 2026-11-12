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

mkdir -p "$ROOT/libs" "$ROOT/jni"

curl -fL --retry 3 "$BASE_URL/tensorflow-lite/$VERSION/tensorflow-lite-$VERSION.aar" -o "$RUNTIME_AAR"
curl -fL --retry 3 "$BASE_URL/tensorflow-lite-api/$VERSION/tensorflow-lite-api-$VERSION.aar" -o "$API_AAR"

unzip -tq "$RUNTIME_AAR" >/dev/null
unzip -tq "$API_AAR" >/dev/null
unzip -p "$RUNTIME_AAR" classes.jar > "$ROOT/libs/tensorflow-lite.jar"
unzip -p "$API_AAR" classes.jar > "$ROOT/libs/tensorflow-lite-api.jar"

test -s "$ROOT/libs/tensorflow-lite.jar" || { echo "TensorFlow Lite runtime classes were not extracted." >&2; exit 1; }
test -s "$ROOT/libs/tensorflow-lite-api.jar" || { echo "TensorFlow Lite API classes were not extracted." >&2; exit 1; }

if ! unzip -l "$RUNTIME_AAR" | grep -q 'jni/arm64-v8a/libtensorflowlite_jni.so'; then
  echo "The runtime AAR does not contain the required arm64-v8a JNI library." >&2
  exit 1
fi

unzip -oq "$RUNTIME_AAR" 'jni/*' -d "$ROOT"
test -s "$ROOT/jni/arm64-v8a/libtensorflowlite_jni.so" || {
  echo "Could not extract the arm64-v8a JNI runtime." >&2
  exit 1
}

echo "TensorFlow Lite $VERSION AIDE dependencies installed:"
echo "  libs/tensorflow-lite.jar"
echo "  libs/tensorflow-lite-api.jar"
echo "  jni/arm64-v8a/libtensorflowlite_jni.so"
echo "Other ABIs included in the runtime AAR were extracted when present."
