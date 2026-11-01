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

    // Event
    const val STEPS = "Schritte"
    const val ADD_STEP = "＋ Schritt"
    const val STEP_LAST_HINT = "Auswahl, Probe und Szenario-Ende stehen immer am Schluss – danach geht es in einem eigenen Event weiter."
    const val MOVE_UP = "Nach oben"
    const val MOVE_DOWN = "Nach unten"
    const val QUESTION = "Text / Frage"
    const val OPTIONS = "Optionen"
    const val OPTION_LABEL = "Beschriftung"
    const val OPTION_EVENT = "führt zu Event"
    const val ADD_OPTION = "Option hinzufügen"
    const val ATTRIBUTE = "Attribut"
    const val SUCCESSES = "Benötigte Erfolge"
    const val ON_SUCCESS = "Bei Erfolg"
    const val ON_FAILURE = "Bei Misserfolg"
    const val AMOUNT = "Änderung (+/−)"
    const val CHAPTER = "Kapitel"
    const val TRIGGER = "Trigger (nur „manual“)"
    const val RESOLUTION = "Auflösung"
    val STEP_TYPES = listOf(
        "text" to "Text anzeigen",
        "choice" to "Auswahl",
        "test" to "Probe",
        "spawnEnemies" to "Gegner erscheinen",
        "changeThreat" to "Bedrohung ändern",
        "addToken" to "Token hinzufügen",
        "removeToken" to "Token entfernen",
        "unlockChapter" to "Kapitel freischalten",
        "setObjective" to "Ziel setzen",
        "fireTrigger" to "Trigger auslösen",
        "endScenario" to "Szenario beenden",
    )
    val ATTRIBUTES = listOf("MIGHT" to "Stärke", "AGILITY" to "Geschick", "WISDOM" to "Weisheit", "SPIRIT" to "Geist", "WIT" to "Verstand")

    // Map editor
    const val MAP_SELECT = "Auswählen"
    const val MAP_ADD_TILE = "Kachel hinzufügen"
    const val MAP_ADD_TOKEN = "Token setzen"
    const val MAP_ADD_ENEMY = "Gegner setzen"
    const val MAP_ROTATE = "Drehen (R)"
    const val MAP_FLIP = "Seite wechseln (F)"
    const val MAP_FIT = "Einpassen"
    const val MAP_HINT_SELECT = "Ziehen: Kachel/Token verschieben · leere Fläche ziehen: Ansicht verschieben · Mausrad: Zoom · R drehen · F Seite · Entf löschen"
    const val MAP_HINT_TILE = "Klicken zum Platzieren · R drehen · F Seite wechseln · Esc abbrechen"
    const val MAP_HINT_PLACE = "Auf ein Feld einer Kachel klicken · Esc abbrechen"
    const val MAP_OVERLAP = "Die Kachel würde andere Kacheln überlappen."
    const val MAP_NO_TILE = "An dieser Stelle liegt keine Kachel."
    const val MAP_EDITING = "Kapitel"
    const val NEW_TOKEN = "Neues Token"
    fun flipConflicts(ids: List<String>) = "Seite gewechselt. Diese Objekte liegen jetzt auf Feldern, die es auf der neuen Seite nicht gibt – bitte verschieben: ${ids.joinToString(", ")}"

    // Catalog editor
    const val MENU_CATALOG_EDIT = "Material-Katalog bearbeiten …"
    const val MENU_CATALOG_NEW = "Neuer Material-Katalog"
    const val CATALOG_WINDOW = "Material-Katalog"
    const val CATALOG_TILES = "Kacheln"
    const val CATALOG_ENEMIES = "Gegnertypen"
    const val CATALOG_EXPANSIONS = "Erweiterungen"
    const val CATALOG_NEW_TILE = "Neue Kachel (aufgedruckte Nummer)"
    const val CATALOG_NEW_ENEMY = "Neuer Gegnertyp"
    const val CATALOG_NEW_EXPANSION = "Neue Erweiterung"
    const val CATALOG_CELLS = "Felder"
    const val CATALOG_NOT_OWNED = "nicht vorhanden"
    const val CATALOG_EMPTY = "Links eine Kachel, einen Gegnertyp oder eine Erweiterung wählen oder mit ＋ anlegen."
    const val CATALOG_LABEL = "Aufgedruckte Nummer"
    const val CATALOG_EXPANSION = "Erweiterung"
    const val CATALOG_MIRROR = "Seite B = Spiegelung von A"
    const val CATALOG_SHAPE_HINT = "Felder anklicken, um sie hinzuzufügen oder zu entfernen. Das markierte Feld in der Mitte ist der Ankerpunkt (0,0) und gehört immer dazu. Die Form so eintragen, wie die Kachel von oben aussieht."
    const val CATALOG_OWNED = "Im Besitz"
    const val CATALOG_ORIENTATION = "Ausrichtung der Sechsecke (gilt für den ganzen Katalog)"
    const val CATALOG_POINTY = "Spitze oben"
    const val CATALOG_FLAT = "Flache Seite oben"
    const val CATALOG_VALID = "Katalog ist gültig."
    const val CATALOG_SAVE_INVALID = "Der Katalog enthält noch Fehler und kann so nicht gespeichert werden."

    // Playtest
    const val MENU_PLAYTEST = "Probespielen …"
    const val PLAYTEST_WINDOW = "Probespiel"
    const val PLAYTEST_HAS_ERRORS = "Die Kampagne enthält noch Fehler. Bitte zuerst die Fehler in der Prüfung beheben."
    const val PLAYTEST_START = "Probespiel starten"
    const val PLAYTEST_SCENARIO = "Szenario"
    const val PLAYTEST_HEROES = "Helden"
    const val PLAYTEST_BACK = "Schritt zurück"
    const val PLAYTEST_RESTART = "Neu starten"
    const val PLAYTEST_END_ROUND = "Runde beenden"
    const val PLAYTEST_DEFEATED = "Besiegt"
    const val PLAYTEST_TOKENS = "Token auf der Karte (anklicken = untersuchen)"
    const val PLAYTEST_LOG = "Verlauf"
    const val PLAYTEST_CAMPAIGN_OVER = "Kampagne beendet."
    const val CONTINUE = "Weiter"
    fun round(n: Int) = "Runde $n"
    fun threat(v: Int, max: Int) = "Bedrohung $v / $max"
    fun spawn(count: Int, name: String, tile: String) = "Stelle $count× $name auf Kachel $tile."
    fun placeToken(label: String, tile: String) = "Lege „$label“ auf Kachel $tile."
    fun removeToken(label: String) = "Entferne „$label“."
    fun threatChanged(delta: Int, now: Int) = "Bedrohung ${if (delta > 0) "+" else ""}$delta (jetzt $now)"
    fun roundEnded(round: Int, added: Int, now: Int) = "Runde $round beendet: Bedrohung +$added (jetzt $now)"
    fun setup(title: String) = "Aufbau: $title"
    fun tileLine(label: String, side: String) = "• Kachel $label, Seite $side"
    fun objectiveSet(title: String) = "Neues Ziel: $title"
    fun objectiveDone(title: String) = "Ziel erfüllt: $title"
    fun test(attribute: String, successes: Int) = "Probe: $attribute – benötigt $successes Erfolge"
    const val SUCCESSES_REACHED = "Erzielte Erfolge"
    fun resolution(outcome: String, title: String) = "$outcome: $title"

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
