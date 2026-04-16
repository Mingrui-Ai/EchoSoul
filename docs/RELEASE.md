# Release Guide

This repository is prepared for a minimal source-based open-source release.

## Before tagging a release

1. Verify the root `README.md`, `LICENSE`, `CHANGELOG.md`, and `config.example.properties` are up to date.
2. Keep private runtime files out of version control:
   - `config.properties`
   - `history/`
   - `temp/`
   - `temp_audio.wav`
   - `response_audio.mp3`
   - IDE metadata such as `.idea/`
3. Confirm external service credentials are not committed.
4. Review bundled images before wider redistribution and replace any assets whose license or attribution is unclear.

## Build steps

Requirements:

- JDK 21 or newer
- Maven 3.9 or newer

Commands:

```powershell
mvn -B -DskipTests clean package
```

This generates the jpackage input layout:

- `target/jpackage-input/echosoul-0.1.1.jar`
- `target/jpackage-input/libs/`

Run locally:

```powershell
mvn javafx:run
```

Optional local packaging check:

```powershell
jpackage --type app-image --dest target/installer --input target/jpackage-input --name EchoSoul --main-class app.Main --main-jar echosoul-0.1.1.jar
```

## Suggested Git release flow

1. Update `CHANGELOG.md`.
2. Commit the release preparation changes.
3. Create a tag such as `vX.Y.Z`.
4. Push the branch and tag.
5. Create a GitHub release from that tag.
6. If you want a binary attachment later, build on the target operating system and upload the generated archive or installer.

## Notes

- This repository currently targets a source release first, with optional local installer packaging.
- Optional speech features require user-supplied AI provider, Baidu, or Vosk setup.
