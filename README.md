# InkReader 📖

**InkReader** is an e-reader designed for Android and E-Ink devices (BOOX, Bigme, Meebook) as well as modern smartphones. It pairs typography with offline next-generation neural text-to-speech, ambient reading soundscapes, OPDS catalog integration, and a spoiler-free AI literary companion.

---

## ✨ Features

### 📚 Reading Experience
- **E-Ink & OLED Optimized:** High-contrast pure black/white rendering with zero ghosting animations and customizable refresh modes.
- **EPUB Engine:** Fast pagination, customizable typography (line height, margins, letter spacing), and custom font support.
- **Reading Progress & Stats:** Daily reading streak, reading velocity, and book time remaining.
- **Cross-Device Sync:** Built-in sync support for **BookOrbit** and **KoSync** (compatible with KOReader).

### 🎙️ Offline Neural TTS (Read Aloud)
High-quality, completely offline neural voices that run directly on your device CPU:
- **Supertonic 3:** Ultra-expressive, lightweight multi-step neural TTS powered by ONNX.
- **Kokoro:** SOTA offline voice synthesis with natural intonation.
- **Piper:** Fast VITS models with minimal memory footprint.
- **Gapless Pre-Buffering:** Background next-page synthesis ensures instant, seamless page turns with zero audio pauses.
- **Real-time Word/Sentence Tracking:** Visual highlight follows along as the audio plays.

### 🎧 Ambient Soundscapes
- Built-in calming background sounds (Rain, Fireplace, Forest Wind, Ocean Waves, Brown Noise).
- Custom audio import support (import your own MP3/WAV ambient tracks).
- Configurable sleep timer and independent ambient volume controls.

### 🤖 AI Reading Companion (Spoiler-Free)
- **Catch Me Up:** Resuming a book after days or weeks? Get a concise narrative story recap up to your exact current chapter without spoiling anything ahead.
- **Character & Lore Lookup:** Ask who a character is or what a faction means in context without future plot spoilers.
- **Multi-Provider Support:** Supports Google Gemini, Groq, OpenAI, and OpenRouter.

### 🌐 OPDS & Cloud Catalogs
- Connect directly to your **Calibre Content Server**, **BookOrbit**, Standard Ebooks, and custom OPDS feeds.
- Search, browse, and download EPUBs straight into your library.

---

## 📲 Installation

1. Download the latest `InkReader.apk` from the [Releases](https://github.com/fiksr/inkreader/releases) page.
2. Transfer and install on your Android phone, tablet, or E-Ink device (Android 8.0+).
3. Open the app, import your EPUB files or connect your OPDS catalog, and start reading!

---

## 🛠️ Building from Source

```bash
git clone https://github.com/fiksr/inkreader.git
cd inkreader
./gradlew assembleDebug
```
The compiled APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`.

---

## 📄 License
This project is licensed under the Apache 2.0 License.
