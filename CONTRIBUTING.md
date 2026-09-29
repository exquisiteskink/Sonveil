# Contributing to Sonveil

Thanks for helping improve Sonveil.

## Before you start

- Search [existing issues](https://github.com/exquisiteskink/Sonveil/issues) before opening a new one.
- For bugs: include Sonveil version, Android version / device, server type (e.g. Navidrome version), and steps to reproduce.
- For features: describe the problem and how you use your self-hosted library today.

## Development

1. Fork and clone the repo.
2. Use JDK 17 and Android SDK 35.
3. Run unit tests before opening a PR:

```bash
./gradlew :app:testDebugUnitTest
```

4. Open a PR against `main` with a short summary of **what** changed and **why**. Prefer small, focused PRs.

## Naming

- User-facing product name is **Sonveil** (`app.sonveil.music`).
- Do not put “Auralis” in README, store listings, F-Droid metadata, or UI strings meant for users. Some internal identifiers may still use that name; leave them unless a dedicated rename task says otherwise.

## Scope tips

- Playback / DVC / AudioSink changes need careful review — prefer discussing in an issue first.
- Docs, metadata, translations, UI polish, and test coverage are great first contributions.

## License

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
