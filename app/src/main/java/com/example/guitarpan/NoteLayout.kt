package com.example.guitarpan

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

// Note IDs are mapped directly to C++ array indices
// Left Drum: C#3 to F#4
// Right Drum: G3 to C#4 (and the duplicate F4/G4/C#4)
class NoteLayout {
    // Helper to create a Note using the MusicalNote lookup
    private fun createNote(
        name: String, // Can be "C#3", "Eb4", etc.
        type: NoteType,
        angle: Double,
        sizeFactor: Float,
        centerRatio: Float = 0.75f,
        xOffset: Float = 0f,
        yOffset: Float = 0f
    ): Note {
        val musicalNote = MusicalNote.get(name) // Use the new lookup
        return Note(
            id = musicalNote.id,
            name = name,
            type = type,
            angle = angle,
            sizeFactor = sizeFactor,
            centerRatio = centerRatio,
            xOffset = xOffset,
            yOffset = yOffset
        )
    }
    val leftDrumNotes = listOf(
        createNote(name = "C4", type = NoteType.OUTER, angle = 308.571, sizeFactor = 1.0f),
        createNote(name = "E4", type = NoteType.OUTER, angle = 257.143, sizeFactor = 1.0f),
        createNote(name = "E3", type = NoteType.OUTER, angle = 205.714, sizeFactor = 1.0f),
        createNote(name = "G#3", type = NoteType.OUTER, angle = 154.286, sizeFactor = 1.0f),
        createNote(name = "D3", type = NoteType.OUTER, angle = 102.857, sizeFactor = 1.0f),
        createNote(name = "Bb3", type = NoteType.OUTER, angle = 51.429, sizeFactor = 1.0f),
        createNote(name = "F#3", type = NoteType.OUTER, angle = 0.0, sizeFactor = 1.0f), // Using flat name

        // Inner notes
        createNote(name = "D4", type = NoteType.INNER, angle = -1.0, sizeFactor = 0.35f, centerRatio = 0.35f, xOffset = -0.3f, yOffset = -0.1f),
        createNote(name = "F#4", type = NoteType.INNER, angle = -1.0, sizeFactor = 0.3f, centerRatio = 0.25f, xOffset = 0.3f, yOffset = -0.1f),
        createNote(name = "G#4", type = NoteType.INNER, angle = -1.0, sizeFactor = 0.3f, centerRatio = 0.25f, xOffset = 0.0f, yOffset = -0.6f) // Restored G#4
    )

    val rightDrumNotes = listOf(
        createNote(name = "B3", type = NoteType.OUTER, angle = 308.571, sizeFactor = 1.0f),
        createNote(name = "Eb4", type = NoteType.OUTER, angle = 257.143, sizeFactor = 1.0f),
        createNote(name = "Eb3", type = NoteType.OUTER, angle = 205.714, sizeFactor = 1.0f), // Using flat name
        createNote(name = "G3", type = NoteType.OUTER, angle = 154.286, sizeFactor = 1.0f), // Using flat name
        createNote(name = "C#3", type = NoteType.OUTER, angle = 102.857, sizeFactor = 1.0f),
        createNote(name = "A3", type = NoteType.OUTER, angle = 51.429, sizeFactor = 1.0f),
        createNote(name = "F3", type = NoteType.OUTER, angle = 0.0, sizeFactor = 1.0f),

        // Inner notes
        createNote(name = "C#4", type = NoteType.INNER, angle = -1.0, sizeFactor = 0.35f, centerRatio = 0.35f, xOffset = -0.3f, yOffset = -0.1f),
        createNote(name = "F4", type = NoteType.INNER, angle = -1.0, sizeFactor = 0.3f, centerRatio = 0.25f, xOffset = 0.3f, yOffset = -0.1f),
        createNote(name = "G4", type = NoteType.INNER, angle = -1.0, sizeFactor = 0.3f, centerRatio = 0.25f, xOffset = 0.0f, yOffset = -0.6f)
    )
}

enum class NoteType { OUTER, INNER }

data class Note(
    val id: Int,
    val name: String,
    val type: NoteType,
    val angle: Double, // degrees, for OUTER notes
    val sizeFactor: Float,
    val centerRatio: Float = 0.75f, // How far from center (0.0) to edge (1.0)
    val xOffset: Float = 0f, // For INNER notes
    val yOffset: Float = 0f  // For INNER notes
) {
    private val path = Path()

    // Function to calculate the bounding rectangle for the note
    // This should be callable outside of draw()
    private fun calculateBoundingRect(drumCenter: Offset, drumRadius: Float): Rect {
        val noteCenter = getNoteCenter(drumCenter, drumRadius)
        val noteWidth = drumRadius * 0.4f * sizeFactor
        val noteHeight = drumRadius * 0.3f * sizeFactor
        return Rect(
            left = noteCenter.x - noteWidth / 2f,
            top = noteCenter.y - noteHeight / 2f,
            right = noteCenter.x + noteWidth / 2f,
            bottom = noteCenter.y + noteHeight / 2f
        )
    }

    private fun getNoteCenter(drumCenter: Offset, drumRadius: Float): Offset {
        return if (type == NoteType.OUTER) {
            val rad = Math.toRadians(angle)
            Offset(
                drumCenter.x + (drumRadius * centerRatio * cos(rad)).toFloat(),
                drumCenter.y + (drumRadius * centerRatio * sin(rad)).toFloat()
            )
        } else {
            Offset(
                drumCenter.x + drumRadius * xOffset,
                drumCenter.y + drumRadius * yOffset
            )
        }
    }

    fun isPointInside(point: Offset, drumCenter: Offset, drumRadius: Float): Boolean {
        val currentRect = calculateBoundingRect(drumCenter, drumRadius)
        return currentRect.contains(point)
    }

    fun draw(drawScope: DrawScope, drumCenter: Offset, drumRadius: Float) {
        val noteCenter = getNoteCenter(drumCenter, drumRadius)
        val noteWidth = drumRadius * 0.4f * sizeFactor
        val noteHeight = drumRadius * 0.3f * sizeFactor

        val displayRect = Rect(left = noteCenter.x - noteWidth / 2f,
            top = noteCenter.y - noteHeight / 2f,
            right = noteCenter.x + noteWidth / 2f,
            bottom = noteCenter.y + noteHeight / 2f)

        path.reset()
        path.addOval(displayRect)

        drawScope.apply {
            drawPath(path, Color.White)
            drawPath(path, Color.Black, style = Stroke(width = 2.dp.toPx()))

            val textPaint = Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 40f
                textAlign = Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                name,
                noteCenter.x,
                noteCenter.y - ((textPaint.descent() + textPaint.ascent()) / 2),
                textPaint
            )
        }
    }
}