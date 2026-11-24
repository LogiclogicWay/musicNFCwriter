package org.musicnfc.writer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class SplashActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val openApp = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(25, 37, 63)
        window.navigationBarColor = Color.rgb(25, 37, 63)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(25, 37, 63))
        }
        val mark = TextView(this).apply {
            text = "T"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(Color.rgb(55, 103, 222))
            }
        }
        content.addView(mark, LinearLayout.LayoutParams(dp(76), dp(76)).apply {
            bottomMargin = dp(22)
        })
        content.addView(TextView(this).apply {
            text = "musicNFCwriter"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, LinearLayout.LayoutParams(-1, -2))


        setContentView(content)
        content.setOnClickListener { openApp.run() }
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(openApp)
        handler.postDelayed(openApp, 850)
    }

    override fun onPause() {
        handler.removeCallbacks(openApp)
        super.onPause()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
