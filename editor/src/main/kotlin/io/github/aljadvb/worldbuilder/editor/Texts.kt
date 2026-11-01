package io.github.aljadvb.worldbuilder.editor

/** German UI texts of the desktop editor, kept in one place. */
object Texts {
    const val APP_TITLE = "Mittelerde Worldbuilder – Editor"
    const val UNTITLED = "Unbenannt"

    const val MENU_FILE = "Datei"
    const val MENU_NEW = "Neue Kampagne"
    const val MENU_OPEN = "Öffnen …"
    const val MENU_OPEN_EXAMPLE = "Beispielkampagne öffnen"
    const val MENU_RECENT = "Zuletzt geöffnet"
    const val MENU_SAVE = "Speichern"
    const val MENU_SAVE_AS = "Speichern unter …"
    const val MENU_CATALOG = "Material-Katalog wählen …"
    const val MENU_CATALOG_EXAMPLE = "Beispielkatalog verwenden"
    const val MENU_CLOSE = "Schließen"
    const val MENU_EXIT = "Beenden"
    const val MENU_EDIT = "Bearbeiten"
    const val MENU_UNDO = "Rückgängig"
    const val MENU_REDO = "Wiederholen"

    const val STRUCTURE = "Struktur"
    const val CHECK = "Prüfung"
    const val NO_ISSUES = "Keine Befunde – die Kampagne kann in der App importiert werden."
    const val ERROR = "Fehler"
    const val WARNING = "Hinweis"
    const val USED_BY = "Benutzt von"
    const val NOT_USED = "Wird nirgends verwendet."
    const val NOTHING_OPEN = "Keine Kampagne geöffnet."
    const val NOTHING_OPEN_HINT = "Lege über „Datei“ eine neue Kampagne an oder öffne eine vorhandene Datei."

    const val GROUP_SCENARIOS = "Szenarien"
    const val GROUP_CHAPTERS = "Kapitel"
    const val GROUP_EVENTS = "Events"
    const val GROUP_TRIGGERS = "Trigger"
    const val GROUP_OBJECTIVES = "Ziele"
    const val GROUP_ENEMIES = "Gegnergruppen"
    const val GROUP_RESOLUTIONS = "Auflösungen"
    const val GROUP_TILES = "Kacheln"
    const val GROUP_TOKENS = "Token"

    const val UNSAVED_TITLE = "Ungespeicherte Änderungen"
    const val UNSAVED_TEXT = "Die Kampagne wurde geändert. Änderungen speichern?"
    const val SAVE = "Speichern"
    const val DISCARD = "Verwerfen"
    const val CANCEL = "Abbrechen"
    const val OK = "OK"

    const val NONE = "– keine –"
    const val CREATE_NEW = "＋ Neu anlegen …"
    const val NAME = "Name"
    const val TITLE = "Titel"
    const val DESCRIPTION = "Beschreibung"
    const val ID = "ID"
    const val RENAME = "ID ändern …"
    const val RENAME_TITLE = "ID ändern"
    const val RENAME_HINT = "Alle Verweise werden automatisch angepasst."
    const val DELETE = "Löschen"
    const val DELETE_TITLE = "Löschen?"
    const val DELETE_UNUSED = "Das Objekt wird nirgends verwendet."
    const val DELETE_USED = "Achtung, das Objekt wird noch verwendet. Diese Verweise werden danach als Fehler angezeigt:"
    const val ADD = "Hinzufügen"

    // Campaign
    const val START_SCENARIO = "Startszenario"
    const val TRANSITIONS = "Übergänge"
    const val TRANSITIONS_HINT = "Endet ein Szenario mit einer Auflösung, folgt das gewählte Szenario. Auflösungen ohne Übergang beenden die Kampagne."
    const val FROM_SCENARIO = "Wenn Szenario"
    const val WITH_RESOLUTION = "endet mit"
    const val TO_SCENARIO = "dann weiter mit"
    const val ADD_TRANSITION = "Übergang hinzufügen"
    const val ADD_SCENARIO = "Szenario hinzufügen"
    const val NEW_SCENARIO = "Neues Szenario"

    // Scenario
    const val INTRO = "Einleitungstext"
    const val THREAT = "Bedrohung"
    const val THREAT_MAX = "Maximum"
    const val THREAT_BASE = "pro Runde"
    const val THREAT_PER_HERO = "pro Held"
    const val THREAT_HINT = "Am Rundenende steigt die Bedrohung um „pro Runde“ + „pro Held“ × Heldenzahl. Schwellen legst du als Trigger „Bedrohung erreicht“ an."
    const val START_CHAPTER = "Startkapitel"
    const val START_OBJECTIVE = "Startziel"
    const val CONTENT = "Inhalt anlegen"
    const val NEW_CHAPTER = "Neues Kapitel"
    const val NEW_EVENT = "Neues Event"
    const val NEW_TRIGGER = "Neuer Trigger"
    const val NEW_OBJECTIVE = "Neues Ziel"
    const val NEW_RESOLUTION = "Neue Auflösung"
    const val NEW_ENEMY_GROUP = "Neue Gegnergruppe"

    // Chapter
    const val SETUP_TEXT = "Aufbautext"
    const val ON_UNLOCK = "Event nach dem Aufbau"
    const val MAP_HINT = "Kacheln und Token platzierst du im Karteneditor."

    // Objective / resolution
    const val REMINDER = "Erinnerungstext"
    const val COMPLETED_BY = "Erfüllt durch Trigger"
    const val TEXT = "Text"
    const val OUTCOME = "Ausgang"
    const val OUTCOME_VICTORY = "Sieg"
    const val OUTCOME_DEFEAT = "Niederlage"
    const val OUTCOME_NEUTRAL = "Neutral"

    // Enemy group
    const val ENEMY_TYPE = "Gegnertyp"
    const val COUNT_BY_HEROES = "Anzahl je Heldenzahl"
    fun heroes(n: Int) = "$n Held" + if (n == 1) "" else "en"
    const val LOCATION = "Ort"
    fun location(tile: String, cell: String) = "Kachel $tile, Feld $cell"

    // Token
    const val LABEL = "Bezeichnung"
    const val TOKEN_TYPE = "Token-Typ"
    const val CUSTOM_TYPE = "Eigener Typ"
    const val INSPECT_TEXT = "Text beim Untersuchen"
    const val ON_INTERACT = "Event beim Antippen"
    const val INITIALLY_ACTIVE = "Liegt ab Kapitelbeginn aus"
    const val REMOVE_AFTER = "Nach dem Antippen entfernen"
    val TOKEN_TYPES = listOf("search" to "Suche", "person" to "Person", "threat" to "Bedrohung", "explore" to "Erkundung", "custom" to "Eigener Typ")

    // Trigger
    const val CONDITION = "Auslöser"
    const val TRIGGER_EVENTS = "Startet Events"
    const val REPEATABLE = "Mehrfach auslösbar"
    const val ADD_EVENT = "Event hinzufügen"
    const val VALUE = "Wert"
    const val ROUND = "Runde"
    const val TOKEN = "Token"
    const val ENEMY_GROUP = "Gegnergruppe"
    const val OBJECTIVE = "Ziel"
    val CONDITIONS = listOf(
        "tokenInteracted" to "Token untersucht",
        "threatReached" to "Bedrohung erreicht Wert",
        "threatMax" to "Bedrohung erreicht Maximum",
        "enemyGroupDefeated" to "Gegnergruppe besiegt",
        "objectiveCompleted" to "Ziel erfüllt",
        "roundEnded" to "Runde beendet",
        "manual" to "Nur durch Event-Schritt „Trigger auslösen“",
    )

    // Placement
    const val TILE = "Kachel"
    const val SIDE = "Seite"
    const val ROTATION = "Drehung"
    const val POSITION = "Position"

    fun catalogLabel(file: String?) = if (file == null) "Katalog: Beispielkatalog (erfunden)" else "Katalog: $file"
    fun issueSummary(errors: Int, warnings: Int) = "$errors Fehler · $warnings Hinweise"
    fun saved(name: String) = "Gespeichert: $name"
    fun savedWithErrors(name: String, errors: Int) = "Gespeichert: $name – enthält noch $errors Fehler; die App importiert die Datei erst ohne Fehler."
    fun saveFailed(reason: String) = "Speichern fehlgeschlagen: $reason"
    fun openFailed(name: String, reason: String) = "„$name“ konnte nicht gelesen werden:\n$reason"
    fun catalogInvalid(reason: String) = "Der Material-Katalog enthält Fehler: $reason"
}
