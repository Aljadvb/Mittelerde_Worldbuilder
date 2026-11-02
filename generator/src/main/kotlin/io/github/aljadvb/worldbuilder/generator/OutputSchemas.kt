package io.github.aljadvb.worldbuilder.generator

import io.github.aljadvb.worldbuilder.model.Terrain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * JSON schemas for the model's structured output, derived from the repository's
 * campaign schema so both can never drift apart.
 *
 * Structured outputs accept only part of JSON Schema: no length/number limits, no
 * conditionals, `anyOf` instead of `oneOf`, and `additionalProperties: false` on every
 * object. The validator checks everything removed here after generation.
 */
object OutputSchemas {
    private val json = Json { prettyPrint = false }

    private val UNSUPPORTED = setOf(
        "\$schema", "\$id", "minLength", "maxLength", "minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum",
        "minItems", "maxItems", "uniqueItems", "pattern", "if", "then", "else", "not", "default", "minProperties", "multipleOf",
    )

    private fun campaignSchema(): JsonObject =
        json.parseToJsonElement(OutputSchemas::class.java.getResource("/campaign.schema.json")!!.readText()).jsonObject

    /** Removes unsupported keywords and turns `oneOf` into `anyOf`, recursively. */
    fun sanitize(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(
            e.filterKeys { it !in UNSUPPORTED }.map { (k, v) -> (if (k == "oneOf") "anyOf" else k) to sanitize(v) }.toMap(),
        )
        is JsonArray -> JsonArray(e.map(::sanitize))
        else -> e
    }

    /**
     * Schema of one scenario as the model writes it. Tile placements carry no position
     * or rotation: the layout is computed afterwards ([AutoLayout]).
     */
    val scenario: String by lazy {
        val defs = campaignSchema().getValue("\$defs").jsonObject.toMutableMap()
        val placement = defs.getValue("tilePlacement").jsonObject
        val props = placement.getValue("properties").jsonObject.filterKeys { it != "position" && it != "rotation" }
        defs["tilePlacement"] = JsonObject(
            placement + mapOf(
                "properties" to JsonObject(props),
                "required" to JsonArray(listOf("id", "tile", "side").map(::JsonPrimitive)),
            ),
        )
        val scenarioDef = defs.getValue("scenario").jsonObject
        // The place on the campaign map is chosen in the editor.
        val scenario = JsonObject(scenarioDef + ("properties" to JsonObject(scenarioDef.getValue("properties").jsonObject - "place")))
        val root = JsonObject(scenario + mapOf("\$defs" to JsonObject(defs - "scenario")))
        json.encodeToString(JsonElement.serializer(), sanitize(root))
    }

    /** Schema of [StoryDraft]. */
    val story: String by lazy {
        fun obj(required: List<String>, block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) = buildJsonObject {
            put("type", "object"); put("additionalProperties", false)
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            putJsonObject("properties", block)
        }
        fun str(desc: String) = buildJsonObject { put("type", "string"); put("description", desc) }
        fun arr(items: JsonElement) = buildJsonObject { put("type", "array"); put("items", items) }
        val root = obj(listOf("title", "intro", "chapters", "objectives", "enemies", "endings", "mapReason")) {
            put("title", str("Titel des Szenarios"))
            put("intro", str("Einleitungstext, 3–6 Sätze, eigene Worte"))
            put("chapters", arr(obj(listOf("title", "summary", "locations", "terrain", "battle")) {
                put("title", str("Kapiteltitel"))
                put("summary", str("Was in diesem Kapitel geschieht, 2–4 Sätze"))
                put("locations", arr(str("Schauplatz, z. B. „Turmruine am Waldrand“")))
                put("terrain", arr(buildJsonObject { putJsonArray("enum") { Terrain.TAGS.forEach { add(JsonPrimitive(it.id)) } } }))
                put("battle", buildJsonObject { put("type", "boolean"); put("description", "true = Kapitel spielt auf Schlachtplänen statt auf Reise-Kacheln") })
            }))
            put("mapReason", str("Kurze Begründung, warum die Kapitel Reise- oder Schlachtkarten nutzen"))
            put("objectives", arr(obj(listOf("title", "howToComplete")) {
                put("title", str("Ziel in Kurzform"))
                put("howToComplete", str("Wie die Gruppe das Ziel erfüllt"))
            }))
            put("enemies", arr(obj(listOf("enemyType", "where")) {
                put("enemyType", str("Gegnertyp-ID aus dem Material"))
                put("where", str("Wo und wann die Gegner auftauchen"))
            }))
            put("endings", arr(obj(listOf("title", "outcome", "condition")) {
                put("title", str("Titel des Endes"))
                put("outcome", buildJsonObject { putJsonArray("enum") { listOf("victory", "defeat", "neutral").forEach { add(JsonPrimitive(it)) } } })
                put("condition", str("Wodurch dieses Ende eintritt"))
            }))
        }
        json.encodeToString(JsonElement.serializer(), root)
    }

    /** All object nodes of a schema (for tests and sanity checks). */
    internal fun objects(e: JsonElement): List<JsonObject> = when (e) {
        is JsonObject -> listOf(e) + e.values.flatMap(::objects)
        is JsonArray -> e.flatMap(::objects)
        else -> emptyList()
    }

    internal fun parse(s: String) = json.parseToJsonElement(s)

}
