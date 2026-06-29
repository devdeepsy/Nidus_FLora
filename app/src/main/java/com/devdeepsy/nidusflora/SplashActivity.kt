package com.devdeepsy.nidusflora

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val appName = findViewById<TextView>(R.id.app_name)
        val appLogo = findViewById<ImageView>(R.id.app_logo)

        // Animate app name
        val nameFade = AlphaAnimation(0f, 1f)
        nameFade.duration = 1000
        nameFade.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}
            override fun onAnimationEnd(animation: Animation) {
                // Animate logo after name
                val logoFade = AlphaAnimation(0f, 1f)
                logoFade.duration = 1000
                appLogo.startAnimation(logoFade)
                appLogo.alpha = 1f

                // Launch MainActivity after delay
                appLogo.postDelayed({
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }, 1500)
            }
            override fun onAnimationRepeat(animation: Animation) {}
        })
        appName.startAnimation(nameFade)
        appName.alpha = 1f
    }
}
