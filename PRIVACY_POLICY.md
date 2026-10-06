# Privacy Policy for InkReader

**Last Updated:** October 6, 2026

**InkReader** ("we", "our", or "us") is an offline-first Android e-reader application developed by Fiksr. We are deeply committed to respecting your privacy and protecting your personal data.

This Privacy Policy explains how InkReader handles your information. **In short: InkReader does not track you, does not collect analytics or personal telemetry, does not show ads, and does not operate any cloud servers that store your reading habits or private documents.**

---

## 1. Information We Do NOT Collect

- **No Personal Identifiers:** We do not collect names, email addresses, phone numbers, device IDs, or advertising IDs.
- **No Analytics or Telemetry:** There are no third-party tracking SDKs (no Firebase Analytics, no Facebook SDK, no crashlytics tracking).
- **No Reading Activity Tracking:** We do not log what books you read, how long you read, your reading speed, or your highlights.

---

## 2. Information Handled Locally on Your Device

All of your application data remains strictly on your local device:
- **E-books & Documents:** Imported EPUB files are stored in your device's private app storage.
- **Reading Progress & Statistics:** Bookmarks, reading streaks, words-per-minute calculations, and annotations are saved in your device's local database.
- **Custom Files:** Custom typography fonts (`.ttf`/`.otf`) and imported ambient audio files stay strictly on your local file system.

---

## 3. Network Connections & Third-Party AI Providers

InkReader only makes network requests when you explicitly trigger features that require an internet connection:

### A. AI Reading Companion (User-Initiated)
InkReader provides an optional AI Companion ("Catch Me Up" summaries, character lore queries, text explanations).
- **Direct HTTPS Connection:** When you submit an AI inquiry, requests are made directly from your Android device to the third-party AI provider you configure (such as Google Gemini, Groq, OpenAI, or DeepSeek/OpenRouter). There is no intermediary proxy server.
- **API Keys:** If you provide your own API key, it is stored securely on your device inside private Android `SharedPreferences`. It is never transmitted to us or any fourth party.
- **Transmitted Data:** Only the text required to fulfill your prompt (such as the current chapter title, progress percentage, and page excerpt) is sent to the selected AI provider.
- **Provider Policies:** Your interactions with third-party AI providers are subject to their respective privacy policies (e.g., [Google Privacy Policy](https://policies.google.com/privacy), [OpenAI Privacy Policy](https://openai.com/privacy)).

### B. On-Demand Neural TTS Voice Models
- When downloading on-device neural Text-to-Speech models (Supertonic, Kokoro, Piper), the app downloads compressed model weights directly from public release repositories (such as GitHub Releases or Hugging Face).
- Once downloaded, **speech synthesis runs 100% offline on your device CPU**. No audio or book text is streamed to cloud servers during reading.

### C. OPDS Catalogs & Cloud Sync (BookOrbit / KoSync)
- If you connect to an OPDS catalog (e.g., Standard Ebooks, Calibre Content Server) or a sync service (BookOrbit, KoSync), network traffic occurs solely between your device and the remote host address you configured.
- Sync credentials and tokens are stored locally on your device.

---

## 4. Android Permissions Used

| Permission | Purpose |
| :--- | :--- |
| `android.permission.INTERNET` | Required to fetch user-configured AI prompts, download offline TTS voice models, and connect to user-added OPDS / cloud sync servers. |
| `android.permission.ACCESS_NETWORK_STATE` | Used to detect network connectivity before initiating model downloads or sync requests. |

*InkReader does not request access to contacts, location, camera, microphone, or broad external storage (`MANAGE_EXTERNAL_STORAGE`). File imports are handled via the secure Android Storage Access Framework (SAF).*

---

## 5. Children’s Privacy

InkReader does not knowingly collect or solicit any personal information from children under the age of 13.

---

## 6. Security

Your locally stored configuration, API keys, and reading data are protected by the Android operating system’s application sandboxing architecture.

---

## 7. Changes to This Privacy Policy

If we make modifications to this policy, we will update the "Last Updated" date at the top of this document. Any updates will reflect our ongoing commitment to user privacy and offline-first design.

---

## 8. Contact Us

If you have questions, feedback, or concerns regarding this Privacy Policy, please reach out:
- **GitHub Issues:** [https://github.com/fiksr/inkreader](https://github.com/fiksr/inkreader)
- **Developer:** Fiksr
