package com.example.guitarpan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout

class MainActivity : AppCompatActivity() {

    private var debugMode   = false
    private var overlayMode = false

    private lateinit var padsPx: List<Pad>

    private val kNoteNames = listOf(
        "D3","G#3","E3","E4","C4","F#3","Bb3",
        "D4","G#4","F#4","C#3","G3","Eb3","Eb4",
        "B3","F3","A3","C#4","G4","F4"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        computePads()
        nativeStartEngine()
    }

    private fun computePads() {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels.toFloat()
        val sh = dm.heightPixels.toFloat()
        padsPx = ratios.mapIndexed { idx, it ->
            Pad(
                (it.cx * sw).toInt(),
                (it.cy * sh).toInt(),
                (it.r  * sw.coerceAtMost(sh)).toInt(), // keep circles round
                idx
            )
        }
    }

    private fun fillDebugGrid(debugRoot: ConstraintLayout) {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val cols = 10
        val rows = 2
        val bw = w / cols
        val bh = h / rows
        kNoteNames.forEachIndexed { idx, name ->
            val btn = Button(this).apply {
                text = name
                setOnClickListener { nativePlayNote(idx, 100) }
            }
            debugRoot.addView(btn)
            btn.layoutParams = ConstraintLayout.LayoutParams(bw, bh).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                topToTop     = ConstraintLayout.LayoutParams.PARENT_ID
                leftMargin   = (idx % cols) * bw
                topMargin    = (idx / cols) * bh
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_debug -> {
                debugMode = !debugMode
                updateUI()
                true
            }
            R.id.action_overlay -> {
                overlayMode = !overlayMode
                updateUI()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun updateUI() {
        val root = findViewById<CoordinatorLayout>(R.id.root)
        root.removeAllViews()

        if (debugMode) {
            val debugView = layoutInflater.inflate(R.layout.button_debug, root, false)
            fillDebugGrid(debugView as ConstraintLayout)
            root.addView(debugView)
        } else {
            val content = layoutInflater.inflate(R.layout.activity_main, root, false)
            root.addView(content)

            if (overlayMode) {
                content.post {  // wait until measured
                    root.overlay.add(RegionOverlay(this, content))
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val x = event.x.toInt()
            val y = event.y.toInt()
            padsPx.find { (x - it.cx) * (x - it.cx) + (y - it.cy) * (y - it.cy) <= it.r * it.r }
                ?.let {
                    val vel = (kotlin.math.sqrt(event.pressure) * 127f).coerceIn(0f, 127f).toInt()
                    nativePlayNote(it.noteIdx, vel)
                }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        init { System.loadLibrary("guitar_pan") }
    }

    private external fun nativeStartEngine(): Boolean
    private external fun nativeStopEngine()
    private external fun nativePlayNote(note: Int, velocity: Int)

    data class Pad(val cx: Int, val cy: Int, val r: Int, val noteIdx: Int)
    private val ratios = listOf(
        // left outer
        PadRatio(0.125f, 0.815f, 0.070f), // 0 D3
        PadRatio(0.198f, 0.703f, 0.070f), // 1 G#3
        PadRatio(0.271f, 0.602f, 0.070f), // 2 E3
        PadRatio(0.344f, 0.602f, 0.070f), // 3 E4
        PadRatio(0.417f, 0.703f, 0.070f), // 4 C4
        PadRatio(0.490f, 0.815f, 0.070f), // 5 F#3
        PadRatio(0.417f, 0.926f, 0.070f), // 6 Bb3

        // left inner
        PadRatio(0.307f, 0.815f, 0.060f), // 7 D4
        PadRatio(0.307f, 0.722f, 0.060f), // 8 G#4
        PadRatio(0.307f, 0.907f, 0.060f), // 9 F#4

        // right outer
        PadRatio(0.667f, 0.815f, 0.070f), //10 C#3
        PadRatio(0.740f, 0.703f, 0.070f), //11 G3
        PadRatio(0.813f, 0.602f, 0.070f), //12 Eb3
        PadRatio(0.885f, 0.602f, 0.070f), //13 Eb4
        PadRatio(0.958f, 0.703f, 0.070f), //14 B3
        PadRatio(1.031f,0.815f, 0.070f), //15 F3
        PadRatio(0.958f,0.926f, 0.070f), //16 A3

        // right inner
        PadRatio(0.849f,0.815f, 0.060f), //17 C#4
        PadRatio(0.849f,0.722f, 0.060f), //18 G4
        PadRatio(0.849f,0.907f, 0.060f)  //19 F4
    )

    data class PadRatio(val cx: Float, val cy: Float, val r: Float)

    /* ----------------------------------------------------------
       UI swap: always keep the toolbar, only replace content
       ---------------------------------------------------------- */
    override fun onDestroy() {
        nativeStopEngine()
        super.onDestroy()
    }

    private inner class RegionOverlay(ctx: Context, private val parent: View) : View(ctx) {
        private val paint = Paint().apply {
            color = Color.argb(120, 255, 0, 0)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val scaleX = parent.width.toFloat() / resources.displayMetrics.widthPixels
            val scaleY = parent.height.toFloat() / resources.displayMetrics.heightPixels
            val scale = kotlin.math.min(scaleX, scaleY)  // keep circles round
            for (pad in padsPx) {
                canvas.drawCircle(
                    pad.cx.toFloat() * scaleX,
                    pad.cy.toFloat() * scaleY,
                    pad.r.toFloat() * scale,
                    paint
                )
            }
        }
    }
}
