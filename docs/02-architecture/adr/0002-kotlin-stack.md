# ADR-0002: Kotlin für Backend und Android

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Android-App ist gesetzt. Backend soll ressourcenschonend auf dem Pi laufen und gut von Agenten wartbar sein.

## Entscheidung

- **Android:** Kotlin, Jetpack Compose, Clean Architecture, Coroutines/Flow.
- **Backend:** Kotlin, Ktor, Exposed (DB-Zugriff), kotlinx.serialization, Koin (DI).
- **Tests:** JUnit 5 / Kotest, MockK, Ktor `testApplication`, Kover für Coverage.
- **Qualität:** ktlint + Detekt im Build.

## Alternativen

- **Python/FastAPI:** etwas weniger RAM, aber zweite Sprache und keine geteilten Modelle mit der App.
- **Go:** sehr schlank, aber zweite Sprache.

## Konsequenzen

- Eine Sprache im ganzen Projekt; ECC-Regeln und Reviewer (`kotlin-reviewer`, `kotlin-build-resolver`) passen überall.
- JVM braucht mehr RAM als Go/Python (~150–300 MB). Auf dem Pi sind ~5 GB frei; der Heap wird per `-Xmx` begrenzt (siehe ADR-0005).
