#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
VERSION="2.14.0"
URL="https://repo.maven.apache.org/maven2/org/tensorflow/tensorflow-lite/$VERSION/tensorflow-lite-$VERSION.aar"
TMP="$ROOT/tensorflow-lite-$VERSION.aar"
command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
command -v unzip >/dev/null 2>&1 || { echo "unzip is required" >&2; exit 1; }
mkdir -p "$ROOT/libs" "$ROOT/jni"
curl -fL --retry 3 "$URL" -o "$TMP"
unzip -p "$TMP" classes.jar > "$ROOT/libs/tensorflow-lite.jar"
if unzip -l "$TMP" | grep -q 'jni/'; then
  unzip -oq "$TMP" 'jni/*' -d "$ROOT"
fi
rm -f "$TMP"
echo "Installed TensorFlow Lite $VERSION Java classes and any packaged JNI libraries."
echo "Check that your AIDE build packages the target ABI's jni/*.so files before installing."
