# Model installation

This app uses the LiteRT Community EfficientDet-Lite0 COCO model (Apache-2.0). It detects common everyday object categories; it is not a specialised game-character model.

Run `sh scripts/download_model.sh` from the project root. The script downloads the 4.56 MB model and verifies SHA-256 before installing it as `assets/efficientdet_lite0_int8.tflite`.

Expected input: `[1, 320, 320, 3]`, RGB uint8.

Expected outputs (validated at runtime):
- output 0: boxes `[1, 25, 4]`, normalized `[ymin, xmin, ymax, xmax]`
- output 1: COCO class indices `[1, 25]`
- output 2: scores `[1, 25]`
- output 3: detection count `[1]`

Model source and licence: https://huggingface.co/litert-community/efficientdet
SHA-256: `33a3b622c7cac0762f96089353cd61495f3e993968d133af7871bfc2d5396704`

If the model is missing or incompatible, the app reports an error. It does not fabricate detections or fall back to pixel heuristics.