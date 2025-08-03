package com.example.guitarpan

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
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
    val color: Color = Color.White,
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
        } else if (type == NoteType.INNER) {
            val innerAreaMaxRadius =
                drumRadius * innerRadiusRatioForOuterRing // Boundary of the clear inner space
            val baseInnerNoteRadius =
                innerAreaMaxRadius * 0.30f // e.g., a note is 30% of the inner area radius by default
            val finalInnerNoteRadius = baseInnerNoteRadius * this.sizeFactor

            if (finalInnerNoteRadius <= 0) return false

            val dx =
                tapOffset.x - (drumCenter.x + (xOffset * innerAreaMaxRadius)) // Account for xOffset
            val dy =
                tapOffset.y - (drumCenter.y + (yOffset * innerAreaMaxRadius)) // Account for yOffset
            val distanceFromNoteCenter = sqrt(dx * dx + dy * dy)

            return distanceFromNoteCenter <= finalInnerNoteRadius
        }
        return false
    }

    // --- DRAWING ---
// Make sure this is inside your data class Note { ... }
// or if it's an extension function: fun Note.draw(...)

    fun draw( // If it's a member function in Note class
// fun Note.draw( // If you prefer it as an extension function
        drawScope: DrawScope,
        drumCenter: Offset,
        drumRadius: Float,
        outerNotesCount: Int,
        noteIndexInOuterRing: Int, // Only relevant for OUTER notes
        innerRadiusRatioForOuterRing: Float // For OUTER notes, this is note.centerRatio.
        // For INNER notes, this is the drum's overall inner area boundary ratio.
    ) {
        drawScope.apply {
            if (type == NoteType.OUTER) {
                // --- Outer Note Drawing (Convex Inner Edge) ---
                val angleStep = 360f / outerNotesCount
                // Adjusted startAngle to better center the segment visually
                val baseStartAngle = (noteIndexInOuterRing * angleStep) - 90f
                val segmentStartAngle = baseStartAngle - (angleStep / 2f)
                val segmentSweepAngle = angleStep

                // 'centerRatio' of an OUTER note defines the inner radius of its ring segment
                val outerNoteInnerRadius = drumRadius * centerRatio

                val path = Path()

                //region Calculate corner points
                val outerArcStartPoint = Offset(
                    drumCenter.x + drumRadius * cos(Math.toRadians(segmentStartAngle.toDouble())).toFloat(),
                    drumCenter.y + drumRadius * sin(Math.toRadians(segmentStartAngle.toDouble())).toFloat()
                )
//                val outerArcEndPoint = Offset( // Though not directly used by variable name, its coordinates are where arcTo ends
//                    drumCenter.x + drumRadius * cos(Math.toRadians((segmentStartAngle + segmentSweepAngle).toDouble())).toFloat(),
//                    drumCenter.y + drumRadius * sin(Math.toRadians((segmentStartAngle + segmentSweepAngle).toDouble())).toFloat()
//                )
                val innerArcStartPoint = Offset(
                    drumCenter.x + outerNoteInnerRadius * cos(Math.toRadians(segmentStartAngle.toDouble())).toFloat(),
                    drumCenter.y + outerNoteInnerRadius * sin(Math.toRadians(segmentStartAngle.toDouble())).toFloat()
                )
                val innerArcEndPoint = Offset(
                    drumCenter.x + outerNoteInnerRadius * cos(Math.toRadians((segmentStartAngle + segmentSweepAngle).toDouble())).toFloat(),
                    drumCenter.y + outerNoteInnerRadius * sin(Math.toRadians((segmentStartAngle + segmentSweepAngle).toDouble())).toFloat()
                )
                //endregion

                // Start from one end of the desired inner curved edge
                path.moveTo(innerArcStartPoint.x, innerArcStartPoint.y)
                // Line to the corresponding point on the outer radius
                path.lineTo(outerArcStartPoint.x, outerArcStartPoint.y)
                // Draw the outer arc
                path.arcTo(
                    rect = Rect(center = drumCenter, radius = drumRadius),
                    startAngleDegrees = segmentStartAngle,
                    sweepAngleDegrees = segmentSweepAngle,
                    forceMoveTo = false
                )
                // Line from the end of the outer arc to the other end of the desired inner curved edge
                path.lineTo(innerArcEndPoint.x, innerArcEndPoint.y)

                // --- Corrected Inner Edge Curve Logic ---
                val curveDepthAmount = drumRadius * 0.08f // Adjust for more/less inward curve depth

                val midAngleRad = Math.toRadians(segmentStartAngle + segmentSweepAngle / 2.0).toFloat()

                // Control point is INWARD from the straight line connecting innerArcStartPoint and innerArcEndPoint
                // It lies on the bisector of the segment, at a radius smaller than outerNoteInnerRadius.
                val controlPointRadius = outerNoteInnerRadius - curveDepthAmount // Key change: SUBTRACT

                val controlPoint = Offset(
                    drumCenter.x + controlPointRadius * cos(midAngleRad),
                    drumCenter.y + controlPointRadius * sin(midAngleRad)
                )

                // The path's current point is innerArcEndPoint.
                // We want to curve from innerArcEndPoint BACK to innerArcStartPoint.
                path.quadraticTo(
                    controlPoint.x, controlPoint.y,
                    innerArcStartPoint.x, innerArcStartPoint.y
                )

                path.close()
                drawPath(path = path, color = color) // Use the Note's color property

                // Optional: Draw a border for the outer note segment
                drawPath(path = path, color = Color.Red, style = Stroke(width = 1.dp.toPx()))

                // --- Text for Outer Note (Optional) ---
                // Calculate a position for the text, e.g., in the middle of the segment's radial depth
                val textRadius = (drumRadius + outerNoteInnerRadius) / 2f
                val textAngleRad = Math.toRadians(segmentStartAngle + segmentSweepAngle / 2.0).toFloat()
                val textCenterX = drumCenter.x + textRadius * cos(textAngleRad)
                val textCenterY = drumCenter.y + textRadius * sin(textAngleRad)

                val textPaintOuter = android.graphics.Paint().apply {
                    color = Color.Blue.toArgb() // Or a contrasting color
                    textSize = (drumRadius - outerNoteInnerRadius) * 0.3f // Adjust size based on segment height
                    textAlign = android.graphics.Paint.Align.CENTER
                    // Consider rotating text if segments are narrow, or using curved text (more complex)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    name, // Use Note's name property
                    textCenterX,
                    textCenterY - ((textPaintOuter.descent() + textPaintOuter.ascent()) / 2f),
                    textPaintOuter
                )

            } else if (type == NoteType.INNER) {
                // --- Inner Note Drawing (Circular) ---
                // innerRadiusRatioForOuterRing here is the ratio defining the *entire* central clear area of the drum
                val innerDrumAreaRadius = drumRadius * innerRadiusRatioForOuterRing

                // Calculate the actual center of this inner note using its xOffset and yOffset
                // These offsets are relative to the center of the innerDrumAreaRadius
                val noteActualCenterX = drumCenter.x + (xOffset * innerDrumAreaRadius)
                val noteActualCenterY = drumCenter.y + (yOffset * innerDrumAreaRadius)
                val noteActualVisualCenter = Offset(noteActualCenterX, noteActualCenterY)

                // Determine the radius of this inner note
                // Let's define a base proportion of the innerDrumAreaRadius that a note takes up by default,
                // and then scale it by the note's individual sizeFactor.
                val baseProportionOfInnerArea = 0.35f // e.g., a note's radius is 35% of the inner drum area radius by default
                val finalNoteRadius = (innerDrumAreaRadius * baseProportionOfInnerArea) * sizeFactor // Use Note's sizeFactor

                if (finalNoteRadius > 0) {
                    // Draw the inner note circle
                    drawCircle(
                        color = Color.Green, // Use the Note's color property
                        radius = finalNoteRadius,
                        center = noteActualVisualCenter
                    )
                    // Draw a border for the inner note
                    drawCircle(
                        color = Color.Yellow,
                        radius = finalNoteRadius,
                        center = noteActualVisualCenter,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // Draw the text (name) for the inner note
                    val textPaintInner = android.graphics.Paint().apply {
                        color = Color.Cyan.toArgb() // Or a contrasting text color
                        textSize = finalNoteRadius * 0.8f // Text size relative to the note's radius
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        name, // Use Note's name property
                        noteActualVisualCenter.x,
                        noteActualVisualCenter.y - ((textPaintInner.descent() + textPaintInner.ascent()) / 2f),
                        textPaintInner
                    )
                }
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
        createNote(name = "F#3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "Bb3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "D3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "G#3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "E3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "E4", type = NoteType.OUTER, centerRatio = 0.6f),

        // Inner - xOffset/yOffset are relative to center of *inner area* (-1 to 1 range typical)
        // centerRatio for inner notes defines their radial distance factor if angleDegrees is used for polar placement.
        // Or, use xOffset/yOffset for cartesian placement within the inner circle.
        // sizeFactor is crucial for inner note sizes.
        createNote(name = "D4", type = NoteType.INNER, xOffset = 0f, yOffset = 0.5f, sizeFactor = 0.8f),
        createNote(name = "G#4", type = NoteType.INNER, xOffset = -0.45f, yOffset = -0.25f, sizeFactor = 0.7f),
        createNote(name = "F#4", type = NoteType.INNER, xOffset = 0.45f, yOffset = -0.25f, sizeFactor = 0.7f)
    )

    val rightDrumNotes = listOf(
        createNote(name = "B3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "F3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "A3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "C#3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "G3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "Eb3", type = NoteType.OUTER, centerRatio = 0.6f),
        createNote(name = "Eb4", type = NoteType.OUTER, centerRatio = 0.6f),

        createNote(name = "C#4", type = NoteType.INNER, xOffset = 0f, yOffset = 0.5f, sizeFactor = 0.8f),
        createNote(name = "G4", type = NoteType.INNER, xOffset = -0.45f, yOffset = -0.25f, sizeFactor = 0.7f),
        createNote(name = "F4", type = NoteType.INNER, xOffset = 0.45f, yOffset = -0.25f, sizeFactor = 0.7f)
    )
}
