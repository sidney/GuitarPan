package com.example.guitarpan

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.guitarpan.ui.theme.GuitarPanTheme
import kotlin.math.min

class MainActivity : ComponentActivity() {

    override fun onDestroy() {
        stopAudioEngine()
        super.onDestroy()
    }

    // JNI function declarations
    private external fun startAudioEngineNative(): Boolean
    private external fun stopAudioEngine()
    private external fun playNote(noteId: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAudioEngineNative() // Call the wrapper
        setContent {
            GuitarPanTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PanUIRoot(onPlayNote = ::playNote)
                }
            }
        }
    }

    companion object {
        init {
            System.loadLibrary("guitarpan")
        }
    }
}

@Composable
fun PanUIRoot(onPlayNote: (noteId: Int) -> Unit) { // Accepts the action
    val noteLayout = remember { NoteLayout() }

    LaunchedEffect(Unit) {
        // MainActivity handles lifecycle, this is a placeholder if needed
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { // `this` is BoxWithConstraintsScope
        val maxDiameterConstrainedByHeight = maxHeight // Each drum can be as tall as the screen
        val maxDiameterConstrainedBySharedWidth = maxWidth / 2 // Each drum gets half the width
        // The actual diameter is limited by the smaller of these constraints
        val optimalDiameterForEachDrum = androidx.compose.ui.unit.min(maxDiameterConstrainedByHeight, maxDiameterConstrainedBySharedWidth)
        // Calculate drumSize within the scope where maxWidth and maxHeight are available
        val drumDisplayDiameterDp = optimalDiameterForEachDrum * 0.9f // Use 90% of the calculated optimal space
        val drumSizeForPanDrum = drumDisplayDiameterDp.value

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanDrum(drumSizeForPanDrum, noteLayout.leftDrumNotes, onNoteTapped = onPlayNote)
            PanDrum(drumSizeForPanDrum, noteLayout.rightDrumNotes, onNoteTapped = onPlayNote)
        }
    }
}

@Composable
fun PanDrum(diameterDp: Float, notes: List<Note>, onNoteTapped: (Int) -> Unit) {
    val density = LocalDensity.current
    val diameterPx = with(density) { diameterDp.dp.toPx() }
    val drumRadiusPx = diameterPx / 2f

    // Determine innerRadiusRatio from the first outer note, or use a default
    // This ratio is crucial for calculating the boundary between outer and inner note areas.
    val innerRadiusRatioForOuterRing = remember(notes) {
        notes.firstOrNull { it.type == NoteType.OUTER }?.centerRatio ?: 0.6f
    }

    val outerNotes = remember(notes) { notes.filter { it.type == NoteType.OUTER } }
    val innerNotes = remember(notes) { notes.filter { it.type == NoteType.INNER } }

    Canvas(modifier = Modifier
        .size(diameterDp.dp)
        .pointerInput(Unit) { // Key can be Unit if notes list doesn't change, or `notes` if it does
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()

                    // Iterate through all changes in this event (can be multiple pointers)
                    event.changes.forEach { pointerInputChange: PointerInputChange ->
                        // Check if this specific pointer just went down
                        if (pointerInputChange.changedToDown()) {
                            val pressPosition = pointerInputChange.position
                            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
                            var notePlayedForThisPointer = false

                            // Check outer notes
                            outerNotes.forEachIndexed { index, note ->
                                if (note.isPointInside(
                                        pressPosition,
                                        canvasCenter,
                                        drumRadiusPx,
                                        outerNotes.size,
                                        index,
                                        note.centerRatio // Outer note uses its own centerRatio
                                    )
                                ) {
                                    Log.d(
                                        "TapDebug",
                                        "Outer Note Tapped! Name: ${note.name}, ID: ${note.id}, IndexInOuterRing: $index, OuterNotesCount: ${outerNotes.size}"
                                    )
                                    onNoteTapped(note.id)
                                    pointerInputChange.consume() // Consume the down event
                                    notePlayedForThisPointer = true
                                    return@forEachIndexed // Exit outerNotes.forEachIndexed
                                }
                            }

                            if (notePlayedForThisPointer) return@forEach // Next pointerInputChange

                            // Check inner notes if no outer note was hit by this pointer
                            innerNotes.forEach { note ->
                                // For inner notes, isPointInside needs the overall drum's innerRadiusRatio
                                if (note.isPointInside(
                                        pressPosition,
                                        canvasCenter,
                                        drumRadiusPx,
                                        0, // Still relevant for inner area calculation
                                        -1, // noteIndexInOuterRing not applicable
                                        innerRadiusRatioForOuterRing // Use the drum's overall inner ring boundary
                                    )
                                ) {
                                    Log.d(
                                        "TapDebug",
                                        "Inner Note Tapped! Name: ${note.name}, ID: ${note.id}"
                                    )
                                    onNoteTapped(note.id)
                                    pointerInputChange.consume() // Consume the down event
                                    //notePlayedForThisPointer = true
                                    return@forEach // Exit innerNotes.forEach
                                }
                            }
                            // If a note was played and event consumed, go to next pointerInputChange
                            // Otherwise, the event remains unconsumed for this pointer
                        }
                        // You can also handle pointerInputChange.changedToUp() here if you need
                        // to trigger something on note release (e.g., stop a sustained note)
                    }
                }
            }
        }) {
        val drawingCenter = Offset(size.width / 2f, size.height / 2f)
        val drawingRadius = min(size.width, size.height) / 2f // Consistent radius

        // 1. Draw Drum Background
        drawCircle(color = Color.DarkGray, radius = drawingRadius, center = drawingCenter) // Example color
        drawCircle(color = Color.Black, radius = drawingRadius, center = drawingCenter, style = Stroke(width = 2.dp.toPx()))


        // 2. Draw Outer Notes
        outerNotes.forEachIndexed { index, note ->
            note.draw(
                drawScope = this,
                drumCenter = drawingCenter,
                drumRadius = drawingRadius,
                outerNotesCount = outerNotes.size,
                noteIndexInOuterRing = index,
                innerRadiusRatioForOuterRing = note.centerRatio
            )
        }

        // 3. Draw Inner Notes
        innerNotes.forEach { note ->
            note.draw(
                drawScope = this,
                drumCenter = drawingCenter,
                drumRadius = drawingRadius,
                outerNotesCount = outerNotes.size,  // Can be useful context if inner layout depends on outer
                noteIndexInOuterRing = -1,
                innerRadiusRatioForOuterRing = innerRadiusRatioForOuterRing // For inner area sizing
            )
        }
    }
}
