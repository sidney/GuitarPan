package com.example.guitarpan

// MusicalNoteIds.kt

// Defines a unique ID for each note in the C#3 to G#4 range
// The order here will define the order in mNoteFrequencies in C++
enum class MusicalNote(
    val canonicalName: String, // The primary name used for display or reference
    val frequency: Double,
    private vararg val alternativeNames: String // For lookup by flat names etc.
) {
    CS3("C#3", 138.59),
    D3("D3", 146.83),
    DS3("D#3", 155.56, "Eb3"), // D#3 / Eb3
    E3("E3", 164.81),
    F3("F3", 174.61),
    FS3("F#3", 185.00),
    G3("G3", 196.00),
    GS3("G#3", 207.65, "Ab3"), // G#3 / Ab3
    A3("A3", 220.00),
    AS3("A#3", 233.08, "Bb3"), // A#3 / Bb3
    B3("B3", 246.94),
    C4("C4", 261.63),
    CS4("C#4", 277.18),
    D4("D4", 293.66),
    DS4("D#4", 311.13, "Eb4"), // D#4 / Eb4
    E4("E4", 329.63),
    F4("F4", 349.23),
    FS4("F#4", 369.99),
    G4("G4", 392.00),
    GS4("G#4", 415.30, "Ab4"); // G#4 / Ab4  -- ADDED G#4

    // Ordinal is the ID
    val id: Int get() = this.ordinal

    companion object {
        private val mapByName = values().associateBy { it.canonicalName }
        private val mapByAlternativeName = values().flatMap { note ->
            note.alternativeNames.map { altName -> altName to note }
        }.toMap()
        private val mapByAnyName = mapByName + mapByAlternativeName

        fun byName(name: String): MusicalNote? = mapByAnyName[name]

        // Convenience if you are certain the name exists
        fun get(name: String): MusicalNote = byName(name)
            ?: throw IllegalArgumentException("No MusicalNote found for name: $name")

        // To keep the previous idOf style if preferred for clarity in NoteLayout
        fun idOf(note: MusicalNote): Int = note.id
        fun idOf(name: String): Int = get(name).id

        val count: Int = values().size
    }
}

// Update this constant if you change the enum
const val TOTAL_UNIQUE_NOTES_COUNT = 20 // C#3 to G#4 inclusive = 20 notes