package com.example.offlinemeshchat

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val splashDuration = 1800L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show the RESS splash screen
        setContentView(R.layout.activity_splash)

        // Open MainActivity after the splash
        Handler(Looper.getMainLooper()).postDelayed({

            val intent = Intent(this, MainActivity::class.java)

            startActivity(intent)

            // Prevent returning to splash with Back button
            finish()

        }, splashDuration)
    }
}