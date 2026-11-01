package io.github.aljadvb.worldbuilder.validation

enum class Severity { ERROR, WARNING }

/** Stable machine-readable issue codes (also used as feedback for AI generation). */
enum class IssueCode {
    // General / IDs
    UNSUPPORTED_FORMAT_VERSION,
    INVALID_ID,
    ID_PREFIX,
    DUPLICATE_ID,
    BLANK_TEXT,

    // References
    UNKNOWN_REFERENCE,
    WRONG_REFERENCE_TYPE,
    REFERENCE_TO_OTHER_SCENARIO,

    // Material catalog
    UNKNOWN_TILE,
    UNKNOWN_TILE_SIDE,
    UNKNOWN_ENEMY_TYPE,
    UNKNOWN_EXPANSION,
    EXPANSION_NOT_OWNED,
    TILE_SHAPE_WITHOUT_ANCHOR,
    TILE_SHAPE_DUPLICATE_CELL,

    // Map
    INVALID_ROTATION,
    CELL_NOT_ON_TILE,
    TILES_OVERLAP,
    TILE_USED_TWICE,
    LOCATION_ON_LATER_CHAPTER,

    // Content rules
    CUSTOM_TYPE_MISSING,
    CUSTOM_TYPE_NOT_ALLOWED,
    ENEMY_COUNT_INCOMPLETE,
    INVALID_NUMBER,
    EMPTY_EVENT,
    TERMINAL_STEP_NOT_LAST,
    CHOICE_TOO_FEW_OPTIONS,
    FIRE_NON_MANUAL_TRIGGER,
    THREAT_THRESHOLD_ABOVE_MAX,
    DUPLICATE_TRANSITION,

    // Reachability
    NO_THREAT_MAX_TRIGGER,
    OBJECTIVE_NOT_COMPLETABLE,
    NO_RESOLUTION_REACHABLE,
    NO_VICTORY_REACHABLE,
    UNREACHABLE,
}

/**
 * One finding of the validator.
 *
 * @param path location in the campaign file, e.g. `scenarios[0].chapters[1].tokens[2].location.cell`
 * @param objectId ID of the closest enclosing object, if any
 * @param message human-readable description (German)
 */
data class ValidationIssue(
    val severity: Severity,
    val code: IssueCode,
    val path: String,
    val objectId: String?,
    val message: String,
) {
    override fun toString() = "[$severity] $code at $path${objectId?.let { " ($it)" } ?: ""}: $message"
}

data class ValidationReport(val issues: List<ValidationIssue>) {
    val errors get() = issues.filter { it.severity == Severity.ERROR }
    val warnings get() = issues.filter { it.severity == Severity.WARNING }
    val isValid get() = errors.isEmpty()

    /** Plain-text listing, one issue per line (suitable for feeding back to an AI). */
    fun format(): String = issues.joinToString("\n") { it.toString() }
}

/** Collects issues while walking a document. */
internal class IssueCollector {
    val issues = mutableListOf<ValidationIssue>()

    fun error(code: IssueCode, path: String, objectId: String?, message: String) {
        issues += ValidationIssue(Severity.ERROR, code, path, objectId, message)
    }

    fun warn(code: IssueCode, path: String, objectId: String?, message: String) {
        issues += ValidationIssue(Severity.WARNING, code, path, objectId, message)
    }
}
