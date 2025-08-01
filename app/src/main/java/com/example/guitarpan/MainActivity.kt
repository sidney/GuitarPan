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
//import androidx.compose.foundation.layout.BoxWithConstraintsScope // Potentially needed if you were to make helper extensions
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.graphics.drawscope.DrawScope
//import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.guitarpan.ui.theme.GuitarPanTheme
import kotlin.math.min

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAudioEngine()
        setContent {
            GuitarPanTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PanUIRoot(onPlayNote = ::playNote)
                }
            }
        }
    }

    override fun onDestroy() {
        stopAudioEngine()
        super.onDestroy()
    }

    // JNI function declarations
    private external fun startAudioEngine()
    private external fun stopAudioEngine()
    private external fun playNote(noteId: Int)

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

    Canvas(modifier = Modifier
        .size(diameterDp.dp) // It's good practice to set a size for the Canvas
        .pointerInput(Unit) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)

            detectTapGestures { tapOffset ->
                notes.forEach { note ->
                    if (note.isPointInside(tapOffset, canvasCenter, drumRadiusPx)) { // Pass drumCenter and drumRadius
                        onNoteTapped(note.id)
                    }
                }
            }
        }
    ) {
        val drawingCenter = Offset(size.width / 2f, size.height / 2f)
        val drawingRadius = size.minDimension / 2f // or use drumRadiusPx if you prefer consistency
        notes.forEach { note ->
            note.draw(this, drawingCenter, drawingRadius)
        }
    }
}