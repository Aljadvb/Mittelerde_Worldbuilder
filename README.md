# Mittelerde Worldbuilder

Private Companion-App (Android) zum Erstellen und Spielen eigener Kampagnen für
„Der Herr der Ringe: Reise durch Mittelerde“. Enthält **keine** Inhalte von
Fantasy Flight Games – Kacheln werden schematisch dargestellt, Material wird in
einer eigenen Katalog-Datei gepflegt.

- Projektbeschreibung: [`docs/PROJEKT.md`](docs/PROJEKT.md)
- Format-Entwurf: [`docs/ENTWURF.md`](docs/ENTWURF.md)

## Module

| Modul         | Inhalt                                             |
|---------------|----------------------------------------------------|
| `:model`      | Kampagnenformat, Material-Katalog, Hex-Mathematik  |
| `:validation` | Validator mit strukturierter Fehlerliste           |
| `:engine`     | Spiellogik ohne Android-Abhängigkeit               |
| `:app`        | Android-App (Jetpack Compose, Material 3)          |

## Bauen & Testen

```bash
./gradlew test :app:assembleDebug
```

Voraussetzungen: JDK 17+, Android SDK mit Plattform 36 (`local.properties` mit `sdk.dir=…`).
