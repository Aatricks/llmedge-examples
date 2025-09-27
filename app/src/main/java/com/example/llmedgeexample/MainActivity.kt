package com.example.llmedgeexample

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnOpenLocal).setOnClickListener {
            startActivity(Intent(this, LocalAssetDemoActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenHuggingFace).setOnClickListener {
            startActivity(Intent(this, HuggingFaceDemoActivity::class.java))
        }

        findViewById<Button>(R.id.btnOpenRag).setOnClickListener {
            startActivity(Intent(this, RagActivity::class.java))
        }
    }
}