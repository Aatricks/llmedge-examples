package com.example.llmedgeexample.demo.text;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.llmedgeexample.R;
import io.aatricks.llmedge.LLMEdge;

import java.io.File;

/**
 * Minimal Java example showing how to use the high-level LLMEdge facade from Java.
 * Note: This demo expects a GGUF model to be present at a given path. Replace
 * the modelPath with a valid model on device or call the Hugging Face helpers.
 */
public class SmolJavaExampleActivity extends AppCompatActivity {
    private static final String TAG = "SmolJavaExample";

    private LLMEdge edge;
    private TextView tvOutput;
    private String modelPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smol_java_example);

        tvOutput = findViewById(R.id.tvOutput);
        edge = EdgeFacadeJavaCompat.createBoundEdge(this);
        modelPath = getFilesDir().getAbsolutePath() + File.separator + "model.gguf";

        Button btnLoad = findViewById(R.id.btnLoadModel);
        Button btnAsk = findViewById(R.id.btnAsk);

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tvOutput.setText("Starting load (async)...\nModel: " + modelPath);

                EdgeFacadeJavaCompat.prepareLocalTextModelAsync(edge, modelPath, new EdgeFacadeJavaCompat.PrepareCallback() {
                    @Override
                    public void onSuccess(String preparedModelPath) {
                        runOnUiThread(() -> tvOutput.append("\nModel prepared successfully via edge.text.prepare(...).\nPath: " + preparedModelPath));
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "Failed to prepare model", t);
                        runOnUiThread(() -> tvOutput.append("\nPrepare failed: " + t.getMessage()));
                    }
                });
            }
        });

        btnAsk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EdgeFacadeJavaCompat.generateWithThinkingDisabledAsync(
                        edge,
                        modelPath,
                        "Say hello from llmedge (Java facade demo)",
                        new EdgeFacadeJavaCompat.GenerateCallback() {
                            @Override
                            public void onSuccess(EdgeFacadeJavaCompat.TextGenerationResult result) {
                                runOnUiThread(() -> {
                                    tvOutput.append("\nResponse:\n" + result.getResponse());
                                    if (result.getTokenCount() != null && result.getTokensPerSecond() != null) {
                                        tvOutput.append(
                                                "\nMetrics: tokens=" + result.getTokenCount() +
                                                        ", throughput=" + String.format("%.2f", result.getTokensPerSecond()) + " tok/s"
                                        );
                                    }
                                });
                            }

                            @Override
                            public void onError(Throwable t) {
                                Log.e(TAG, "Error getting response", t);
                                runOnUiThread(() -> tvOutput.append("\nError: " + t.getMessage()));
                            }
                        }
                );
            }
        });
    }
}
