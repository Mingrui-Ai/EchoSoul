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

Run locally:

```powershell
mvn javafx:run
```

## Suggested Git release flow

1. Update `CHANGELOG.md`.
2. Commit the release preparation changes.
3. Create a tag such as `v0.1.0`.
4. Push the branch and tag.
5. Create a GitHub release from that tag.
6. If you want a binary attachment later, build on the target operating system and upload the generated archive or installer.

## Notes

- This repository currently targets a source release first. Installer packaging is intentionally left as a later step.
- Optional speech features require user-supplied DeepSeek, Baidu, or Vosk setup.
