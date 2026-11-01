package io.github.aljadvb.worldbuilder.model

import java.io.File

/** Access to schema and example files at the repository root (set by Gradle). */
object RepoFiles {
    private val root = File(System.getProperty("repoRoot") ?: "..")

    fun read(path: String): String = File(root, path).readText()

    val campaignSchema get() = read("schema/campaign.schema.json")
    val materialSchema get() = read("schema/material.schema.json")
    val exampleCampaign get() = read("examples/beispiel-kampagne.json")
    val exampleMaterial get() = read("examples/beispiel-material.json")
}
