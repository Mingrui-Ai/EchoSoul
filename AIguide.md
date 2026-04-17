
# [Context Restoration] EchoSoul Project Session Context

> **TO THE AI ASSISTANT RECEIVING THIS FILE:** > This is a context-restoration document. Please read the following system state, architecture rules, and pending tasks carefully. Acknowledge receipt of this context briefly, and wait for the user's prompt to begin the next phase of development.

## 📌 Project Metadata
* **Project Name**: EchoSoul (AI Desktop Chat Assistant)
* **Developer**: Mingrui
* **Current Version**: v0.1.1 (Maven synchronized)
* **Last Session Date**: 2026-04-17
* **Current Priority**: Core source optimization, with the in-memory inverted-index history search now taking precedence. Packaging slimming is temporarily deprioritized.

---

## 🏗️ Core Architecture & Logic Constraints (CRITICAL)

### 1. Security & Configuration (`ConfigManager`)
* **Constraint**: API Keys and sensitive data MUST NOT be hardcoded or packaged within the JAR.
* **Logic**: The application uses `System.getProperty("user.dir")` to locate `config.properties` in the external execution directory.
* **Fallback**: If `config.properties` is missing externally, `ConfigManager` automatically extracts an example template from the `resources` folder to the `user.dir` to guide the user.

### 2. Network Layer (`ChatService`)
* **Client**: `OkHttp` (v4.12.0) implemented as a **Singleton**.
* **Performance Tuning**: Uses a Connection Pool (Max idle connections: 10, Keep-alive: 5 minutes) to prevent frequent TCP handshakes during high-frequency chats.
* **Timeouts**: Configured strictly to prevent JavaFX UI freezing (Connect: 15s, Read: 60s, Write: 60s).

---

## 📦 Build & Packaging Environment

* **Build Tool**: Maven (`mvn clean package` outputs to `target/jpackage-input/` and now also generates `target/runtime/echosoul-jre/` via `jlink`).
* **Packager**: JDK `jpackage` utilizing WiX Toolset v3.14 (Environment variable path is confirmed working).
* **Runtime Slimming**: `src/module-info.java` was added, and `pom.xml` now invokes `jlink` with `--strip-debug`, `--compress=2`, `--no-header-files`, and `--no-man-pages` to build a trimmed custom runtime image.
* **Delivery Command**: When generating the final Windows installer, use the exact following parameters to ensure the custom icon and user-selectable installation path work:

```powershell
jpackage --type exe --dest target/installer --input target/jpackage-input `
--name EchoSoul --app-version 0.1.1 --icon "EchoSoul.ico" `
--win-dir-chooser --win-shortcut --main-class app.Main `
--main-jar echosoul-0.1.1.jar --runtime-image target/runtime/echosoul-jre
```

---

## 🚀 Next Stage Tasks (Roadmap)
Core source optimization is now the active milestone. The following roadmap items are ordered by implementation priority:

1. **Inverted Index Search Optimization**: 
   * Current issue: Linear search is too slow for 5000+ messages.
   * Status: In progress and now partially implemented.
   * Goal: Implement a memory-based inverted index (using `HashMap`) for fast keyword retrieval of chat history.
2. **Offline Voice Recognition Integration**: 
   * Tool: Vosk offline engine.
   * Model: `vosk-model-small-cn-0.15` (Chinese).
   * Goal: Enable accurate speech-to-text without a network connection.
3. **Local LLM Backend**: 
   * Concept: Research `Llama-cpp-java` bindings to integrate a quantized Llama 2-7B model directly locally, replacing basic keyword matching when offline.

## 2026-04-17 Phase 0 Progress
* Added `src/module-info.java` and limited the app module to the JavaFX, logging, desktop, Gson, OkHttp, Baidu SDK, and `org.json` modules currently used by the codebase.
* Updated `pom.xml` so Maven creates a custom `jlink` runtime image at `target/runtime/echosoul-jre` before packaging. The build currently stays on `--compress=2` because the installed `jlink` rejects the string form `--compress=zip`.
* Added `scripts/package.ps1` to encode the new PowerShell packaging flow with `--runtime-image target/runtime/echosoul-jre` and the icon at `resources/images/EchoSoul.ico`.
* Removed the unused `javafx-fxml` dependency from `pom.xml`; the current codebase does not use `FXMLLoader`, `@FXML`, or any `.fxml` resources.
* Cleaned runtime dependencies in `pom.xml`: excluded `slf4j-simple`, `guava`, `error_prone_annotations`, and `org.jetbrains:annotations` from the packaged app because they are not required by the current runtime path.
* Trimmed bundled image resources for packaging: only the default avatars and app icons are copied into the jar, while large gallery/demo images remain external assets instead of inflating the installer.
* Hardened the JavaFX window icon loading path so the app can fall back to bundled avatar assets when `app_icon.png` is absent from the working tree.
* Verified outputs on 2026-04-17: the custom runtime directory is about 44.57 MB, the `app-image` is about 58.79 MB, and the final Windows installer `target/final-installer/EchoSoul-0.1.1.exe` is about 44.89 MB.
* `jpackage` should now consume the custom runtime through `--runtime-image` instead of bundling the default full JRE.

## GraalVM Native Image Assessment
* Reaching roughly 20MB-30MB with near-instant startup is realistic only if the app moves from the current `jpackage + custom JRE` model to true ahead-of-time native compilation.
* For a JavaFX desktop app, the practical path is GraalVM JDK 21 plus a JavaFX-aware native build plugin such as GluonFX / Gluon Substrate. A plain `native-maven-plugin` setup is usually not enough for JavaFX media, desktop packaging, and native resource handling.
* Required build changes would include: a dedicated native profile in `pom.xml`, Windows-native toolchain setup, explicit resource inclusion for `resources/help`, `resources/database`, bundled images, and `config.example.properties`, plus native packaging rules for icon/output layout.
* Reflection and dynamic loading are a major migration risk. `VoskSpeechService` currently uses `Class.forName(...)` and reflective constructor/method calls, so native image would need reflection metadata or a refactor to direct typed bindings.
* Resource lookup would need explicit configuration because the app currently loads bundled text/image assets with classpath resource streams while also reading writable files from `user.dir`.
* The riskiest runtime areas are JavaFX media playback, `javax.sound.sampled`, Baidu SDK networking/TLS, and any future Vosk native library loading. These usually require extra JNI / reachability metadata and platform-specific testing.
* Native builds will shrink startup time dramatically, but they also make builds slower, debugging harder, and third-party library compatibility much stricter than the current JVM distribution model.

## 2026-04-17 History Search Progress
* Added stable message IDs to `src/model/Message.java`. New messages now get a generated UUID, and legacy history JSON without `id` is still readable because `fromJson(...)` backfills missing IDs automatically.
* Added `src/service/ChatSearchEngine.java`, which maintains an in-memory `Map<String, Set<String>>` inverted index and uses a lightweight tokenizer: ASCII words are grouped as lowercase terms, while Chinese text is tokenized into single characters plus overlapping bi-grams.
* Added asynchronous history indexing/search entry points in `src/service/ChatService.java`: `createRefreshSessionsTask()` now loads persona-scoped sessions and builds the inverted index on a JavaFX background `Task`, while `createSearchSessionsTask(String keyword)` maps matched message IDs back to session filenames without blocking the UI thread.
* Updated `src/app/ChatbotUI.java` so the history sidebar now includes a dedicated search box, hides/shows that box correctly when the sidebar is collapsed, refreshes history via background tasks, and performs keyword searches asynchronously with stale-task protection using version counters.
* Re-applied the existing window-icon fallback in `src/app/ChatbotUI.java` so the app still tolerates a missing `/images/app_icon.png` by falling back to bundled avatar assets.
* Repaired the `API 设置` dialog regression in `src/app/ChatbotUI.java`: it now exposes the generic AI provider preset, protocol display, API key, base URL, and model fields again instead of only showing a single DeepSeek key field, while still preserving the Baidu speech credentials section.
* Updated the window icon loader in `src/app/ChatbotUI.java` to prioritize `resources/images/EchoSoul.ico` and added a Swing-based `.ico` fallback path so the JavaFX window can display the project icon even when only the `.ico` asset is present.
* Verified on 2026-04-17 with `mvn -B -DskipTests compile`: the project compiles successfully after the inverted-index and async sidebar search changes.

---

PS D:\06_Projects\EchoSoul> Get-ChildItem -Recurse -Depth 1


    目录: D:\06_Projects\EchoSoul


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/15     22:39                .github
d-----         2026/4/16     20:11                .idea
d-----         2026/4/15     22:44                docs
d-----         2026/4/15     22:15                history
d-----         2026/4/16     11:49                resources
d-----         2026/4/15     22:15                scripts
d-----         2026/4/16     19:01                src
d-----         2026/4/16     20:17                target
-a----         2026/4/15     22:39            168 .gitattributes
-a----         2026/4/16     18:56            640 .gitignore
-a----         2026/4/17     14:57           2890 AIguide.md
-a----         2026/4/16     18:56           1238 CHANGELOG.md
-a----         2026/4/16     18:56            632 config.example.properties
-a----         2026/4/16     18:59            486 config.properties
-a----         2026/4/15     22:39            793 CONTRIBUTING.md
-a----         2026/4/15     22:39           1078 LICENSE
-a----         2026/4/16     18:56           6473 pom.xml
-a----         2026/4/16     19:19           5499 README.md


    目录: D:\06_Projects\EchoSoul\.github


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/15     22:39                workflows


    目录: D:\06_Projects\EchoSoul\.idea


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/15     22:15                libraries
-ar---        2025/10/10     20:11            190 .gitignore
-a----        2025/11/23     11:17              9 .name
-a----         2026/4/16     10:01            541 compiler.xml
-ar---         2025/11/3     18:45            195 copilot.data.migration.agent.xml
-ar---         2025/11/3     21:46            193 copilot.data.migration.ask.xml
-ar---         2025/11/5     10:07            199 copilot.data.migration.ask2agent.xml
-ar---         2025/11/3     21:46            194 copilot.data.migration.edit.xml
-ar---        2025/11/22     16:59          27102 copilotDiffState.xml
-a----         2026/4/16     10:01            248 encodings.xml
-a----         2026/4/16     10:01            864 jarRepositories.xml
-a----         2026/4/16     10:01            539 misc.xml
-a----         2026/4/15     23:12            185 vcs.xml
-a----         2026/4/16     20:11          18535 workspace.xml


    目录: D:\06_Projects\EchoSoul\docs


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a----         2026/4/15     22:44            339 README.md
-a----         2026/4/16     18:58           1627 RELEASE.md


    目录: D:\06_Projects\EchoSoul\history


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/15     22:15                contacts
d-----         2026/4/16     14:06                message


    目录: D:\06_Projects\EchoSoul\resources


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/15     22:15                database
d-----         2026/4/15     22:15                help
d-----         2026/4/15     22:15                images
-a----         2026/4/16     18:56            673 config.example.properties


    目录: D:\06_Projects\EchoSoul\scripts


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
-a----         2026/4/15     22:44            356 compile.bat
-a----         2026/4/15     22:44            323 run.bat


    目录: D:\06_Projects\EchoSoul\src


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/15     22:15                app
d-----         2026/4/15     22:15                local
d-----         2026/4/15     22:15                model
d-----         2026/4/16     12:30                service
d-----         2026/4/15     22:15                speech


    目录: D:\06_Projects\EchoSoul\target


Mode                 LastWriteTime         Length Name
----                 -------------         ------ ----
d-----         2026/4/16     19:04                app-image
d-----         2026/4/16     20:01                bootstrapper-src
d-----         2026/4/16     19:03                classes
d-----         2026/4/16     20:02                final-installer
d-----         2026/4/16     19:03                generated-sources
d-----         2026/4/16     19:48                iexpress-stage
d-----         2026/4/16     19:03                installer
d-----         2026/4/16     19:33                installer-exe
d-----         2026/4/16     19:44                installer-j21-debug
d-----         2026/4/16     19:03                jpackage-input
d-----         2026/4/16     19:45                jpackage-temp-debug
d-----         2026/4/16     19:03                maven-archiver
d-----         2026/4/16     19:03                maven-status
-a----         2026/4/16     20:17       37851451 EchoSoul-v0.1.1-Portable.zip.zip


PS D:\06_Projects\EchoSoul>

---

**[END OF CONTEXT]**
