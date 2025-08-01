package com.example.guitarpan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import android.graphics.Paint // Keep for text
// import androidx.compose.ui.test.topLeft
// import androidx.compose.ui.unit.size
import kotlin.math.*

// MusicalNote and TOTAL_UNIQUE_NOTES_COUNT remain the same

enum class NoteType { OUTER, INNER }

data class Note(
    val id: Int,
    val name: String, // Display name
    val type: NoteType,
    // For OUTER notes, 'angle' could be used for ordering if needed, but slices are contiguous.
    // We'll primarily use the note's index in the list of outer notes.
    val angleDegrees: Double = 0.0, // Can be used for specific placement of INNER notes or initial orientation.
    val sizeFactor: Float = 1.0f, // For INNER notes: relative size. For OUTER: could influence ring thickness.
    // For OUTER notes: defines the ratio of the inner radius of the outer ring.
    // For INNER notes: defines distance from center.
    val centerRatio: Float = 0.75f,
    val xOffset: Float = 0f, // For INNER notes: relative X position from center of inner area
    val yOffset: Float = 0f  // For INNER notes: relative Y position from center of inner area
) {

    // --- HIT DETECTION ---

    fun isPointInside(
        tapOffset: Offset,
        drumCenter: Offset,
        drumRadius: Float,
        outerNotesCount: Int, // Total number of outer notes for this drum
        noteIndexInOuterRing: Int, // This note's index if it's an OUTER note
        innerRadiusRatioForOuterRing: Float // e.g., 0.6f meaning outer notes are between 0.6*R and R
    ): Boolean {
        val dx = tapOffset.x - drumCenter.x
        val dy = tapOffset.y - drumCenter.y
        val distanceFromCenter = sqrt(dx * dx + dy * dy)

        if (type == NoteType.OUTER) {
            if (outerNotesCount == 0) return false
            val noteRingOuterRadius = drumRadius
            val noteRingInnerRadius = drumRadius * innerRadiusRatioForOuterRing

            if (distanceFromCenter < noteRingInnerRadius || distanceFromCenter > noteRingOuterRadius) {
                return false // Tap is outside the radial band of the outer notes
            }

            // Calculate angle of the tap
            var angle = atan2(dy, dx) // Radians, -PI to PI
            if (angle < 0) angle += 2 * PI.toFloat() // Normalize to 0 to 2*PI

            val sweepAnglePerNote = (2 * PI / outerNotesCount).toFloat()
            val startAngleForNote = noteIndexInOuterRing * sweepAnglePerNote

            // Check if tap angle is within this note's segment
            // Handle wrap-around for the last segment potentially crossing 0 radians
            val endAngleForNote = startAngleForNote + sweepAnglePerNote
            if (startAngleForNote <= endAngleForNote) { // Normal case
                return angle >= startAngleForNote && angle < endAngleForNote
            } else { // Wrap around case (shouldn't happen with normalized startAngleForNote)
                return angle >= startAngleForNote || angle < endAngleForNote
            }

        } else { // INNER
            // Inner notes are ovals. Their positions are relative to the center of the
            // area *inside* the outer ring.
            val innerAreaRadius = drumRadius * innerRadiusRatioForOuterRing

            // Calculate the specific center of this inner note
            // xOffset and yOffset are relative to the center of the *inner area*
            val innerNoteCenterX = drumCenter.x + (xOffset * innerAreaRadius)
            val innerNoteCenterY = drumCenter.y + (yOffset * innerAreaRadius)
            // val innerNoteCenter = Offset(innerNoteCenterX, innerNoteCenterY)

            // Size of inner ovals. sizeFactor is relative to a fraction of innerAreaRadius
            val baseOvalSize = innerAreaRadius * 0.3f // Base size for an inner oval if sizeFactor is 1
            val noteWidth = baseOvalSize * sizeFactor
            val noteHeight = baseOvalSize * sizeFactor * 0.8f // Make them slightly elliptical by default

            val ovalRect = Rect(
                left = innerNoteCenterX - noteWidth / 2f,
                top = innerNoteCenterY - noteHeight / 2f,
                right = innerNoteCenterX + noteWidth / 2f,
                bottom = innerNoteCenterY + noteHeight / 2f
            )
            return ovalRect.contains(tapOffset)
        }
    }

    // --- DRAWING ---

    fun draw(
        drawScope: DrawScope,
        drumCenter: Offset,
        drumRadius: Float,
        outerNotesCount: Int,
        noteIndexInOuterRing: Int, // Only relevant for OUTER notes
        innerRadiusRatioForOuterRing: Float,
        debugLines: Boolean = false // For visualizing hit areas
    ) {
        drawScope.apply {
            if (type == NoteType.OUTER) {
                if (outerNotesCount == 0) return

                val sweepAngleDegrees = 360f / outerNotesCount
                // Start angle offset by -90 degrees to make the first segment start at the top (12 o'clock)
                val startAngleDegrees = (noteIndexInOuterRing * sweepAngleDegrees) - 90f

                val noteRingOuterRadius = drumRadius
                val noteRingInnerRadius = drumRadius * innerRadiusRatioForOuterRing

                // Path for the outer segment (pie slice)
                val segmentPath = Path().apply {
                    // Move to the inner-start point of the arc
                    moveTo(
                        drumCenter.x + innerRadiusRatioForOuterRing * drumRadius * cos(Math.toRadians(startAngleDegrees.toDouble())).toFloat(),
                        drumCenter.y + innerRadiusRatioForOuterRing * drumRadius * sin(Math.toRadians(startAngleDegrees.toDouble())).toFloat()
                    )
                    // Line to the outer-start point of the arc
                    lineTo(
                        drumCenter.x + drumRadius * cos(Math.toRadians(startAngleDegrees.toDouble())).toFloat(),
                        drumCenter.y + drumRadius * sin(Math.toRadians(startAngleDegrees.toDouble())).toFloat()
                    )
                    // Arc along the outer edge
                    arcTo(
                        rect = Rect(center = drumCenter, radius = drumRadius),
                        startAngleDegrees = startAngleDegrees,
                        sweepAngleDegrees = sweepAngleDegrees,
                        forceMoveTo = false
                    )
                    // Line to the inner-end point of the arc
                    lineTo(
                        drumCenter.x + innerRadiusRatioForOuterRing * drumRadius * cos(Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())).toFloat(),
                        drumCenter.y + innerRadiusRatioForOuterRing * drumRadius * sin(Math.toRadians((startAngleDegrees + sweepAngleDegrees).toDouble())).toFloat()
                    )
                    // Arc along the inner edge (drawn in reverse to close the path)
                    arcTo(
                        rect = Rect(center = drumCenter, radius = innerRadiusRatioForOuterRing * drumRadius),
                        startAngleDegrees = startAngleDegrees + sweepAngleDegrees,
                        sweepAngleDegrees = -sweepAngleDegrees, // Draw inner arc in reverse
                        forceMoveTo = false
                    )
                    close()
                }
                drawPath(segmentPath, Color.White) // Fill the segment
                drawPath(segmentPath, Color.Black, style = Stroke(width = 1.dp.toPx())) // Outline

                // Text in the middle of the segment
                val textAngleDegrees = startAngleDegrees + sweepAngleDegrees / 2f
                val textRadius = (noteRingInnerRadius + noteRingOuterRadius) / 2f
                val textX = drumCenter.x + textRadius * cos(Math.toRadians(textAngleDegrees.toDouble())).toFloat()
                val textY = drumCenter.y + textRadius * sin(Math.toRadians(textAngleDegrees.toDouble())).toFloat()

                val textPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = drumRadius * 0.1f // Scale text with drum size
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    name,
                    textX,
                    textY - ((textPaint.descent() + textPaint.ascent()) / 2f), // Center vertically
                    textPaint
                )

            } else { // INNER
                val innerAreaRadius = drumRadius * innerRadiusRatioForOuterRing
                val innerNoteCenterX = drumCenter.x + (xOffset * innerAreaRadius)
                val innerNoteCenterY = drumCenter.y + (yOffset * innerAreaRadius)
                val innerNoteCenter = Offset(innerNoteCenterX, innerNoteCenterY) // For text placement

                val baseOvalSize = innerAreaRadius * 0.4f // Adjust base size
                val noteWidth = baseOvalSize * sizeFactor
                val noteHeight = baseOvalSize * sizeFactor * 0.8f // Elliptical

                // CORRECTED Rect creation for drawing the oval:
                // We need the top-left and the size for drawOval
                val ovalTopLeft = Offset(
                    x = innerNoteCenterX - noteWidth / 2f,
                    y = innerNoteCenterY - noteHeight / 2f
                )
                val ovalSize = Size(noteWidth, noteHeight)

                drawOval(color = Color.White, topLeft = ovalTopLeft, size = ovalSize)
                drawOval(color = Color.Black, topLeft = ovalTopLeft, size = ovalSize, style = Stroke(width = 1.dp.toPx()))

                val textPaint = Paint().apply {
                    color = android.graphics.Color.BLACK
                    textSize = noteHeight * 0.4f // Scale text with oval height
                    textAlign = Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(
                    name,
                    innerNoteCenter.x, // Text is centered on the logical center
                    innerNoteCenter.y - ((textPaint.descent() + textPaint.ascent()) / 2f),
                    textPaint
                )
            }
        }
    }
}

class NoteLayout {
    // Helper to create a Note
    private fun createNote(
        name: String,
        type: NoteType,
        angleDegrees: Double = 0.0, // Used for inner note initial hints or outer note ordering
        sizeFactor: Float = 1.0f,
        centerRatio: Float = if (type == NoteType.OUTER) 0.6f else 0.5f, // Default inner radius for outer ring, or center dist for inner
        xOffset: Float = 0f, // For INNER notes
        yOffset: Float = 0f  // For INNER notes
    ): Note {
        val musicalNote = MusicalNote.get(name)
        return Note(
            id = musicalNote.id,
            name = name, // Use input name for display
            type = type,
            angleDegrees = angleDegrees,
            sizeFactor = sizeFactor,
            centerRatio = centerRatio,
            xOffset = xOffset,
            yOffset = yOffset
        )
    }

    // Example Layout (adjust xOffset, yOffset, sizeFactor for inner notes for good spacing)
    // The order of OUTER notes in this list determines their segment order around the drum.
    val leftDrumNotes = listOf(
        // Outer - centerRatio defines inner boundary of the ring, e.g. 0.6 means ring is from 0.6*R to R
        createNote(name = "C4", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "E4", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "G#3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "E3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "D3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "Bb3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "F#3", type = NoteType.OUTER, centerRatio = 0.6f),

        // Inner - xOffset/yOffset are relative to center of *inner area* (-1 to 1 range typical)
        // centerRatio for inner notes defines their radial distance factor if angleDegrees is used for polar placement.
        // Or, use xOffset/yOffset for cartesian placement within the inner circle.
        // sizeFactor is crucial for inner note sizes.
        createNote(name = "D4", type = NoteType.INNER, xOffset = 0f, yOffset = -0.5f, sizeFactor = 0.8f),
        createNote(name = "F#4", type = NoteType.INNER, xOffset = -0.45f, yOffset = 0.25f, sizeFactor = 0.7f),
        createNote(name = "G#4", type = NoteType.INNER, xOffset = 0.45f, yOffset = 0.25f, sizeFactor = 0.7f)
    )

    val rightDrumNotes = listOf(
        createNote(name = "B3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "Eb4", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "G3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "Eb3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "C#3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "A3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "F3", type = NoteType.OUTER, centerRatio = 0.6f),

        createNote(name = "C#4", type = NoteType.INNER, xOffset = 0f, yOffset = -0.5f, sizeFactor = 0.8f),
        createNote(name = "F4", type = NoteType.INNER, xOffset = -0.45f, yOffset = 0.25f, sizeFactor = 0.7f),
        createNote(name = "G4", type = NoteType.INNER, xOffset = 0.45f, yOffset = 0.25f, sizeFactor = 0.7f)
    )
}
