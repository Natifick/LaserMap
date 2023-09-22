package com.example.lasermap

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View

class MainScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_screen)

    }

    fun launchSearch(view: View?) {
        startActivity(Intent(this, MapsActivity::class.java))
    }

    fun onBlutOpenerClick(view: View?) {
        startActivity(Intent(this, BluetoothActivity::class.java))
    }
}