package com.example.guitarpan

import android.os.Bundle
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private external fun nativePlayNote(note: Int, velocity: Int)
    private external fun nativeStartEngine(): Boolean
    private external fun nativeStopEngine()

    companion object { init { System.loadLibrary("guitar_pan") } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        nativeStartEngine()
    }

    override fun onDestroy() { nativeStopEngine(); super.onDestroy() }

    // 20 hot-spots (cx,cy,r) in px – adjust once for your PNG
    private val pads = listOf(
        // Left drum – outer ring
        Pad( 250, 680, 55, 0),  // D3
        Pad( 330, 520, 55, 1),  // G#3
        Pad( 450, 410, 55, 2),  // E3
        Pad( 580, 410, 55, 3),  // E4
        Pad( 700, 520, 55, 4),  // C4
        Pad( 780, 680, 55, 5),  // F#3
        Pad( 700, 840, 55, 6),  // Bb3
        // Left drum – inner ring
        Pad( 515, 640, 45, 7),  // D4
        Pad( 515, 565, 45, 8),  // G#4
        Pad( 515, 715, 45, 9),  // F#4
        // Right drum – outer ring
        Pad(1080, 680, 55, 10), // C#3
        Pad(1160, 520, 55, 11), // G3
        Pad(1280, 410, 55, 12), // Eb3
        Pad(1410, 410, 55, 13), // Eb4
        Pad(1530, 520, 55, 14), // B3
        Pad(1610, 680, 55, 15), // F3
        Pad(1530, 840, 55, 16), // A3
        // Right drum – inner ring
        Pad(1345, 640, 45, 17), // C#4
        Pad(1345, 565, 45, 18), // G4
        Pad(1345, 715, 45, 19)  // F4
    )

    data class Pad(val cx: Int, val cy: Int, val r: Int, val noteIdx: Int)

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x.toInt()
            val y = event.y.toInt()
            pads.find { (x - it.cx) * (x - it.cx) + (y - it.cy) * (y - it.cy) <= it.r * it.r }
                ?.let {
                    val vel = (sqrt(event.pressure) * 127).coerceIn(0f, 127f).toInt()
                    nativePlayNote(it.noteIdx, vel)
                }
        }
        return super.onTouchEvent(event)
    }
}
