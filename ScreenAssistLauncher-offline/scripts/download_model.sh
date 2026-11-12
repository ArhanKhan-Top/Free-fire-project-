#!/usr/bin/env sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
DEST="$ROOT/assets/efficientdet_lite0_int8.tflite"
URL="https://huggingface.co/litert-community/efficientdet/resolve/main/efficientdet_lite0_detection.tflite?download=true"
EXPECTED="33a3b622c7cac0762f96089353cd61495f3e993968d133af7871bfc2d5396704"
mkdir -p "$ROOT/assets"
command -v curl >/dev/null 2>&1 || { echo "curl is required" >&2; exit 1; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 1; }
TMP="$DEST.part"
rm -f "$TMP"
curl -fL --retry 3 "$URL" -o "$TMP"
ACTUAL="$(sha256sum "$TMP" | awk '{print $1}')"
if [ "$ACTUAL" != "$EXPECTED" ]; then
  rm -f "$TMP"
  echo "Model checksum mismatch. Expected $EXPECTED, got $ACTUAL" >&2
  exit 1
fi
mv "$TMP" "$DEST"
echo "Verified model installed at $DEST"
