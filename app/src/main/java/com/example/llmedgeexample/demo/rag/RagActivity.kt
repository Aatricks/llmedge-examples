package com.example.llmedgeexample.demo.rag

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*

/**
 * Activity demonstrating RAG (Retrieval Augmented Generation) capabilities.
 *
 * Features:
 * - PDF document indexing
 * - Semantic search and retrieval
 * - Context-aware question answering
 * - Memory-efficient operation via LLMEdge
 */
class RagActivity : AppCompatActivity() {
    private val edge by lazy(LazyThreadSafetyMode.NONE) { bindEdge(this, this, lifecycleScope) }
    private lateinit var views: RagViews
    private lateinit var controller: RagController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rag)

        views = RagViews.bind(this)
        controller = RagController(this, lifecycleScope, edge, views)

        views.pickButton.setOnClickListener { controller.pickPdf() }
        views.indexButton.setOnClickListener { controller.indexSelectedPdf() }
        views.askButton.setOnClickListener { controller.askQuestion() }
        views.previewButton.setOnClickListener { controller.previewRetrieval() }

        controller.initialize()
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.clear()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        controller.handleActivityResult(requestCode, resultCode, data)
    }
}
