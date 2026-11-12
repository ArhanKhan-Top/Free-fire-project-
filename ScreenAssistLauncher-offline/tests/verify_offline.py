#!/usr/bin/env python3
"""Source-level checks for the offline annotation conversion."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "src/com/screenassist/launcher"
manifest = (ROOT / "AndroidManifest.xml").read_text()
main = (SRC / "MainActivity.java").read_text()
detector = (SRC / "Detector.java").read_text()
view = (SRC / "AnnotationView.java").read_text()
checks = {
    "document picker for local images": "ACTION_OPEN_DOCUMENT" in main,
    "user-selected image save flow": "ACTION_CREATE_DOCUMENT" in main,
    "background inference worker": "Executors.newSingleThreadExecutor" in main,
    "real TFLite interpreter": "new Interpreter(" in detector,
    "tensor output shape validation": "validateModelOutputs" in detector and "getOutputTensor(i).shape()" in detector,
    "EfficientDet boxes/classes/scores/count outputs": all(x in detector for x in ["outputs.put(0, boxes)", "outputs.put(1, classes)", "outputs.put(2, scores)", "outputs.put(3, count)"]),
    "aspect-ratio preserving preprocessing": "Math.min((float) INPUT_SIZE / width, (float) INPUT_SIZE / height)" in detector,
    "class-aware NMS": "candidate.label.equals(input.get(j).label)" in detector,
    "confidence labels and bounding boxes": "canvas.drawRect(rect, boxPaint)" in view and "result.confidence" in view,
    "missing model fails explicitly": "Model asset is missing" in detector,
    "no MediaProjection permission": "FOREGROUND_SERVICE_MEDIA_PROJECTION" not in manifest,
    "no overlay permission": "SYSTEM_ALERT_WINDOW" not in manifest,
    "no capture service declared": "CaptureService" not in manifest,
    "no game package coupling": "com.dts.freefiremax" not in "\n".join(p.read_text(errors="ignore") for p in SRC.glob("*.java")),
    "no screen-capture APIs in Java source": "MediaProjection" not in "\n".join(p.read_text(errors="ignore") for p in SRC.glob("*.java")),
    "model checksum documented": "33a3b622c7cac0762f96089353cd61495f3e993968d133af7871bfc2d5396704" in (ROOT / "assets/MODEL-SETUP.md").read_text(),
}
xml_files = list(ROOT.rglob("*.xml"))
for path in xml_files:
    ET.parse(path)
checks["all XML files parse"] = bool(xml_files)
for path in SRC.glob("*.java"):
    source = path.read_text()
    checks[f"balanced delimiters: {path.name}"] = source.count("{") == source.count("}") and source.count("(") == source.count(")")
for name, ok in checks.items():
    print(("PASS" if ok else "FAIL") + "  " + name)
print(f"\n{sum(checks.values())}/{len(checks)} checks passed")
raise SystemExit(0 if all(checks.values()) else 1)
