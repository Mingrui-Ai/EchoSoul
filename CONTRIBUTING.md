# Contributing

Thanks for contributing to EchoSoul.

## Basic workflow

1. Fork or branch from the latest default branch.
2. Keep changes scoped and easy to review.
3. Do not commit private credentials, local history, or generated audio files.
4. Update documentation when behavior or setup changes.
5. Open a pull request with a short summary of what changed and how it was checked.

## Development notes

- Use JDK 21+ and Maven 3.9+.
- Run `mvn -B -DskipTests clean package` before opening a release-oriented PR when possible.
- Keep local configuration in the root `config.properties`, not in `resources/config.properties`.

## What to avoid

- Do not commit API keys or machine-specific absolute paths.
- Do not commit `.idea/`, `target/`, `history/`, `temp/`, or generated media files.
