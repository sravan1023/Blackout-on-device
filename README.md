# Blackout

**On-device document redaction. Zero leaks.**

No cloud. No `INTERNET` permission. All inference runs locally via LiteRT-LM on the Snapdragon 8 Elite NPU.

---

## Features

- **7 domains** — Medical, Financial, Legal, Tactical, Journalism, Field Service, General
- **4-layer pipeline** — Classify → Detect → Redact → Validate, each a fresh LLM conversation
- **Relational detection** — catches `"the patient's daughter Lisa"` where regex sees nothing
- **Indexed image redaction** — OCR elements indexed by number, not string; lossless bounding-box mapping
- **Validator retry loop** — up to 3 rounds; independent auditor with no memory of prior layers
- **Tap-to-reveal** — redacted spans are interactive; toggle individual items or full document
- **Backend cascade** — NPU → GPU → CPU, auto-selected at init
- **Document vault** — local Room database with categories, versions, and saved text snippets
- **Camera + gallery + paste** — any input format, ML Kit OCR with full bounding-box metadata
- **100% offline** — works in airplane mode; no telemetry

### Performance (Galaxy S25 Ultra)

| Metric | NPU | GPU |
|---|---|---|
| First token | **92ms** | 366ms |
| Decode | **41.7 tok/s** | 24.5 tok/s |
| 229-char note, end-to-end | **2.78s** | 5.65s |

---

## Build & install

### Prerequisites

- Android Studio Meerkat or later
- Android SDK 36, JDK 21
- Device: Samsung Galaxy S25 Ultra (or any arm64 Android 12+ with 8 GB RAM)

### Push model files

```bash
# NPU variant (S25 Ultra / Snapdragon 8 Elite)
adb push gemma4_npu.litertlm /sdcard/Android/data/com.example.blackout/files/gemma4_npu.litertlm

# CPU/GPU variant
adb push gemma4.litertlm /sdcard/Android/data/com.example.blackout/files/gemma4.litertlm
```

Model: `litert-community/gemma-4-E2B-it-litert-lm` on HuggingFace (Apache 2.0).

| File | Size | Backend |
|---|---|---|
| `gemma-4-E2B-it.litertlm` | ~2.4 GB | CPU / GPU |
| `gemma-4-E2B-it_qualcomm_sm8750.litertlm` | ~2.8 GB | Snapdragon 8 Elite NPU |

### Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew installDebug
```

### Launch

```bash
adb shell am start -n com.example.blackout/com.example.blackout.MainActivity
```

---
