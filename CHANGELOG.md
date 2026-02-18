# Changelog

## Unreleased

### Added
- JUnit 5 test suite for config persistence and SQL schema initialization.
- Database initializer idempotency coverage to guard SQL bootstrap regressions.

### Changed
- Gradle repositories updated to current PaperMC and CodeMC endpoints.
- README build instructions now match the Gradle build output path.

### Fixed
- Plugin startup now fails fast on invalid/missing config and invalid `database.url`.
- Invalid `log-level` config values now fall back to `INFO` instead of crashing startup.
- Config save now creates parent directories before writing.
