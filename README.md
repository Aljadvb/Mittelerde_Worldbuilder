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

## Stand (Phase 1)

- Kampagnenformat + JSON-Schema (`schema/`), Beispielkatalog und Beispielkampagne (`examples/`, frei erfunden)
- Validator mit strukturierter Fehlerliste (Ort, Code, Text)
- Engine: Kapitel, Token, Events, Proben, Auswahl, Trigger, Bedrohung, Ziele, Gegnergruppen, Auflösungen, Kampagnenübergänge
- App: Kampagne/Katalog importieren (mit Prüfbericht), neues Spiel, schematische Hex-Karte, Aufbau-Anweisungen, Rundenablauf, Ereignisprotokoll; Spielstände werden nach jeder Aktion automatisch gespeichert

| Aufbau | Spiel | Probe |
|---|---|---|
| ![](docs/screenshots/04_setup_muehle.png) | ![](docs/screenshots/03_idle_dorf.png) | ![](docs/screenshots/05_test_tor.png) |

## Eigenen Material-Katalog anlegen

Vorlage: [`examples/beispiel-material.json`](examples/beispiel-material.json), Schema: [`schema/material.schema.json`](schema/material.schema.json).
Felder je Kachelseite in axialen Koordinaten (q, r), von oben gesehen, (0,0) = Ankerfeld. Import in der App über ⋮ → „Material-Katalog importieren“.

## Bauen & Testen

```bash
./gradlew test :app:testDebugUnitTest :app:assembleDebug
```

Die App-Tests rendern Screenshots nach `app/build/screenshots/`.

Voraussetzungen: JDK 17+, Android SDK mit Plattform 36 (`local.properties` mit `sdk.dir=…`).
