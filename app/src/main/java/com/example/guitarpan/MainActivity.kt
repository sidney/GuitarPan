package com.example.guitarpan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
        // Calculate drumSize within the scope where maxWidth and maxHeight are available
        val drumSize = min(maxWidth.value, maxHeight.value) / 2.2f

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PanDrum(drumSize, noteLayout.leftDrumNotes, onNoteTapped = onPlayNote)
            PanDrum(drumSize, noteLayout.rightDrumNotes, onNoteTapped = onPlayNote)
        }
    }
}

@Composable
fun PanDrum(diameterDp: Float, notes: List<Note>, onNoteTapped: (Int) -> Unit) {
    val density = LocalDensity.current
    val diameterPx = with(density) { diameterDp.dp.toPx() }
    val drumRadiusPx = diameterPx / 2f

    // Determine the ratio for the inner radius of the outer ring.
    // This should ideally come from the layout or be a constant.
    // Let's assume the first outer note's centerRatio defines it for the whole drum.
    // Or pick a fixed value if all outer notes share it.
    val innerRadiusRatioForOuterRing = notes.firstOrNull { it.type == NoteType.OUTER }?.centerRatio ?: 0.6f

    val outerNotes = remember(notes) { notes.filter { it.type == NoteType.OUTER } }
    val innerNotes = remember(notes) { notes.filter { it.type == NoteType.INNER } }

    Canvas(modifier = Modifier
        .size(diameterDp.dp)
        .pointerInput(Unit) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            detectTapGestures { tapOffset ->
                var noteTapped = false
                // Check outer notes first (they are usually larger targets)
                outerNotes.forEachIndexed { index, note ->
                    if (note.isPointInside(
                            tapOffset,
                            canvasCenter,
                            drumRadiusPx,
                            outerNotes.size,
                            index, // Pass the note's index within the outer ring
                            innerRadiusRatioForOuterRing
                        )
                    ) {
                        onNoteTapped(note.id)
                        return@detectTapGestures // Found a note, no need to check others
                    }
                }

                innerNotes.forEach { note ->
                    // For inner notes, noteIndexInOuterRing is not relevant, pass 0 or any dummy
                    if (note.isPointInside(
                            tapOffset,
                            canvasCenter,
                            drumRadiusPx,
                            0, // outerNotesCount not relevant for inner note hit test logic
                            0, // noteIndexInOuterRing not relevant for inner
                            innerRadiusRatioForOuterRing // Still need this for inner area boundary
                        )
                    ) {
                        onNoteTapped(note.id)
                        return@detectTapGestures // Found a note
                    }
                }
            }
        }
    ) {
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
                innerRadiusRatioForOuterRing = innerRadiusRatioForOuterRing
            )
        }

        // 3. Draw Inner Notes
        innerNotes.forEach { note ->
            note.draw(
                drawScope = this,
                drumCenter = drawingCenter,
                drumRadius = drawingRadius,
                outerNotesCount = 0, // Not relevant for drawing inner notes
                noteIndexInOuterRing = 0, // Not relevant
                innerRadiusRatioForOuterRing = innerRadiusRatioForOuterRing // For inner area sizing
            )
        }
    }
}
