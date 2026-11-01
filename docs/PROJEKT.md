# Projekt: Companion-App für eigene „Journeys in Middle-earth“-Kampagnen

## Ziel
Eine Android-App, mit der ich eigene Kampagnen für das Brettspiel „Der Herr der Ringe: Reise durch Mittelerde“ (Journeys in Middle-earth, Fantasy Flight Games) erstellen und am Tisch spielen kann – als Ersatz für die offizielle Companion-App, aber mit eigenen Inhalten.

Die App besteht langfristig aus drei Teilen:
1. **Player** – führt die Gruppe durch ein Szenario (Aufbau, Token-Interaktionen, Rundenablauf, Bedrohung, Gegner, Ziele, Spielstand speichern).
2. **Editor** – Szenarien und Kampagnen in der App erstellen und bearbeiten.
3. **KI-Generator** – Abenteuer auf Basis von Literatur/Notizen mit einer KI-API erzeugen lassen.

Alle drei hängen am selben **Kampagnenformat (JSON)**. Das Format ist das Fundament und wird zuerst entworfen.

## Rahmenbedingungen
- Rein private Nutzung.
- **Keine Inhalte von Fantasy Flight Games** in Code oder Repo: keine Grafiken, Logos, Kartentexte, Regeltexte. Kacheln werden **schematisch** als Sechsecke mit Kachelnummer dargestellt. Später sollen optional eigene Fotos/Scans meiner Kacheln hinterlegt werden können.
- Am Tisch liegen die echten Spielteile. Die App sagt, was wohin gelegt wird – sie muss das Spiel nicht grafisch nachbilden.
- Spielmaterial (welche Kacheln, Gegner, Erweiterungen ich besitze) wird in einer separaten **Material-Katalog-Datei** gepflegt, die ich selbst befülle – nicht im Code fest verdrahtet.
- Oberfläche auf **Deutsch**.

## Technik
- Kotlin, Jetpack Compose, Material 3
- Gradle mit Kotlin DSL, Version Catalog
- Architektur: MVVM, klare Trennung in Module/Pakete: `model` (Format), `validation`, `engine` (Spiellogik ohne Android-Abhängigkeit), `ui`
- `kotlinx.serialization` für JSON
- Spielstände lokal speichern (DataStore oder Dateien; Vorschlag erwünscht)
- minSdk 26
- Unit-Tests für Format, Validierung und Engine

## Kampagnenformat (Anforderungen)
Grobe Hierarchie: **Kampagne → Szenarien → Kapitel → Kachel-Platzierungen / Token → Events → Trigger → Ziele**

Muss abbilden können:
- **Kampagne**: Titel, Beschreibung, Szenarien, Verknüpfungen zwischen Szenarien (Verzweigungen je nach Ausgang)
- **Szenario**: Titel, Einleitungstext, ein oder mehrere Auflösungen (Enden), Bedrohungsleiste mit Schwellen, die Events auslösen, erstes Ziel
- **Kapitel**: Abschnitte des Szenarios, die nacheinander freigeschaltet werden und eigene Kacheln/Token mitbringen
- **Kachel-Definition** (im Material-Katalog): ID, Seite (A/B), Form als Liste von Sechseckfeldern in **axialen Hex-Koordinaten**
- **Kachel-Platzierung**: welche Kachel, Position und Drehung auf der Karte
- **Token**: Typ (z. B. Suche, Person, Bedrohung, Erkundung, eigener Typ), Kachel + Feld, Text beim Untersuchen, ausgelöstes Event
- **Events**: Text anzeigen, Auswahl mit Optionen, Probe (Attribut + benötigte Erfolge, Ergebnis je nach Erfolg/Misserfolg), Gegner erscheinen lassen, Bedrohung ändern, Token hinzufügen/entfernen, Kapitel freischalten, Ziel setzen, Szenario beenden
- **Trigger**: benannte Auslöser, die Events/Ziele verbinden (z. B. „Token X untersucht“, „Bedrohung erreicht 10“, „Gegner besiegt“, „Ziel erfüllt“)
- **Ziele**: Name, Erinnerungstext, Trigger, der sie erfüllt
- **Gegner-Gruppen**: Gegnertyp (Referenz auf Material-Katalog), Anzahl abhängig von Spielerzahl, Erscheinungsort
- Jedes Objekt hat eine eindeutige, sprechende **ID**; Verweise laufen nur über IDs.

Das Format muss so klar und streng sein, dass später eine KI es zuverlässig befüllen kann. Dazu gehört ein **JSON-Schema**.

## Validierung
Ein eigenes Modul prüft ein Szenario und liefert eine **strukturierte Fehlerliste** (Ort + Beschreibung), u. a.:
- Alle ID-Verweise existieren
- Verwendete Kacheln und Gegner sind im Material-Katalog vorhanden
- Jedes Ziel ist über irgendeinen Trigger-Pfad erreichbar
- Es gibt mindestens einen erreichbaren Weg zu einer Auflösung
- Keine Token auf nicht existierenden Feldern

Diese Fehlerliste wird später an die KI zurückgegeben, damit sie ihre Fehler selbst korrigiert.

## Phasen
**Phase 1 – Format + spielbarer Player (jetzt)**
- Datenklassen, JSON-Schema, Material-Katalog-Format
- Validator mit Tests
- Ein kleines Beispielszenario (2 Kapitel, ein paar Token, ein Gegner, zwei Enden) – mit frei erfundenen Texten
- Player: Kampagne aus Datei laden, Einleitung, Aufbau-Anweisungen mit schematischer Hex-Karte, Token antippen → Event, Rundenende mit Bedrohung, Ziele, Auflösung, Spielstand speichern/laden

**Phase 2 – Editor in der App**
- Szenarien anlegen und bearbeiten, Kacheln auf der Hex-Karte platzieren, Token setzen, Events/Trigger verknüpfen, Live-Validierung

**Phase 3 – KI-Generator**
- Eingabe: Vorlage (Zusammenfassung/Notizen oder gemeinfreier Text), Länge, Schwierigkeit, eigenes Material
- Schritt 1: KI erstellt Story-Entwurf → ich gebe frei oder ändere
- Schritt 2: KI erzeugt Szenario-JSON nach Schema → Validator → Fehler zurück an KI → Ergebnis im Editor
- KI-Anbieter über eine austauschbare Schnittstelle, API-Schlüssel lokal gespeichert
- Die KI soll eigene Texte schreiben und keine Passagen aus geschützten Büchern übernehmen

**Später**: Kampf mit Schadensberechnung, Helden/Inventar, Zufallselemente, eigene Kachelbilder

## Arbeitsweise
- Bevor du Code schreibst: Plan und Entwurf des Formats vorlegen und auf meine Freigabe warten.
- In kleinen Schritten arbeiten, nach jedem Schritt muss das Projekt bauen und die Tests laufen.
- Bei Entscheidungen mit mehreren sinnvollen Optionen kurz fragen.
- Code-Kommentare und Bezeichner auf Englisch, UI-Texte auf Deutsch (in `strings.xml`).
