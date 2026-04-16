# Changelog

All notable changes to this project will be documented in this file.

The format is inspired by Keep a Changelog and uses semantic versioning where practical.

## [0.1.1] - 2026-04-16

### Added

- protocol-based AI provider adapters for OpenAI-compatible, Anthropic, and Gemini APIs
- jpackage-ready Maven output layout under `target/jpackage-input/`

### Changed

- externalized writable configuration so `config.properties` is created and updated beside the app
- reused a singleton `OkHttpClient` with connection pooling and global timeouts
- refreshed release docs to match the current packaging and configuration flow

### Removed

- accidental manifest residue and module metadata that were not needed for the current classpath packaging flow

## [0.1.0] - 2026-04-15

### Added

- Maven build entry for a minimal open-source release
- public configuration example
- root-level README and release documentation
- CI workflow for repository build validation
- contribution guide and MIT license

### Changed

- sanitized default configuration for public sharing
- updated helper scripts to use Maven instead of local JavaFX path variables
- expanded `.gitignore` to exclude private runtime data and generated artifacts
