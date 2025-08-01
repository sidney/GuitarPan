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
    @Suppress("KotlinJniMissingFunction")
    private external fun startAudioEngineNative(): Boolean
    @Suppress("KotlinJniMissingFunction")
    private external fun stopAudioEngine()
    @Suppress("KotlinJniMissingFunction")
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
            detectTapGestures(
                onPress = { pressOffset ->
                    // This block is executed on pointer down (touch)
                    val canvasCenter = Offset(size.width / 2f, size.height / 2f)

                    var noteFound = false
                    // Check outer notes first
                    outerNotes.forEachIndexed { index, note ->
                        if (note.isPointInside(
                                pressOffset, // Use the offset from onPress
                                canvasCenter,
                                drumRadiusPx,
                                outerNotes.size,
                                index,
                                innerRadiusRatioForOuterRing
                            )
                        ) {
                            onNoteTapped(note.id)
                            noteFound = true
                            return@forEachIndexed // Found a note in outer, exit this loop
                        }
                    }

                    if (noteFound) {
                        // If you want to consume the event and not proceed to onTap or other gestures
                        // you might need to handle the press interaction differently or use tryAwaitRelease()
                        // For simply triggering on down, this is often enough.
                        // The default behavior of detectTapGestures will still try to detect onTap, onLongPress etc.
                        // after onPress completes.
                        tryAwaitRelease() // Wait for the pointer to release to complete the press sequence
                        return@detectTapGestures
                    }

                    // Check inner notes if no outer note was hit
                    innerNotes.forEach { note ->
                        if (note.isPointInside(
                                pressOffset, // Use the offset from onPress
                                canvasCenter,
                                drumRadiusPx,
                                0,
                                0,
                                innerRadiusRatioForOuterRing
                            )
                        ) {
                            onNoteTapped(note.id)
                            noteFound = true
                            return@forEach // Found a note in inner, exit this loop
                        }
                    }

                    if (noteFound) {
                        tryAwaitRelease() // Wait for the pointer to release
                        return@detectTapGestures
                    }

                    // If you reach here, no note was pressed.
                    // Allow detectTapGestures to continue trying to detect onTap, onLongPress, etc.
                    // by calling tryAwaitRelease(). If you don't call it, other gesture detectors
                    // might not work as expected.
                    tryAwaitRelease()
                }
                // You can still keep onTap if you need it for other reasons,
                // or remove it if onPress handles everything.
                // onTap = { tapOffset ->
                //    // This is called on pointer up if no other gesture consumed it
                // }
            )
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
