package com.example.llmedgeexample.demo.text;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.llmedgeexample.R;
import io.aatricks.llmedge.text.runtime.SmolLM;
import io.aatricks.llmedge.text.runtime.SmolLMJavaCompat;

import java.io.File;

/**
 * Minimal Java example showing how to use SmolLM via the Java compatibility helpers.
 * Note: This demo expects a GGUF model to be present at a given path. Replace
 * the modelPath with a valid model on device or call the Hugging Face helpers.
 */
public class SmolJavaExampleActivity extends AppCompatActivity {
    private static final String TAG = "SmolJavaExample";

    private SmolLM smol;
    private TextView tvOutput;
    private byte[] savedState = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_smol_java_example);

        tvOutput = findViewById(R.id.tvOutput);

        smol = new SmolLM(true);

        Button btnLoad = findViewById(R.id.btnLoadModel);
        Button btnAsk = findViewById(R.id.btnAsk);

        btnLoad.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Example model path — replace with a real GGUF file on device
                final String modelPath = getFilesDir().getAbsolutePath() + File.separator + "model.gguf";
                tvOutput.setText("Starting load (async)...\nModel: " + modelPath);

                SmolLMJavaCompat.loadAsync(smol, modelPath, null, new SmolLMJavaCompat.LoadCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> tvOutput.append("\nModel loaded successfully."));
                    }

                    @Override
                    public void onError(Throwable t) {
                        Log.e(TAG, "Failed to load model", t);
                        runOnUiThread(() -> tvOutput.append("\nLoad failed: " + t.getMessage()));
                    }
                });
            }
        });

        btnAsk.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Run the blocking getResponse on a background thread to avoid blocking UI
                new Thread(() -> {
                    try {
                        // Ensure model is loaded before calling getResponse
                        final String answer = smol.getResponse("Say hello from llmedge (Java demo)", -1);
                        runOnUiThread(() -> tvOutput.append("\nResponse:\n" + answer));
                        // Demonstrate KV-cache export/import
                        try {
                            final byte[] state = SmolLMJavaCompat.getStateBytes(smol);
                            savedState = state;
                            runOnUiThread(() -> tvOutput.append("\nSaved KV cache bytes: " + (state == null ? "null" : state.length)));
                            // Clear cache then restore to demonstrate round-trip
                            SmolLMJavaCompat.clearKvCache(smol);
                            SmolLMJavaCompat.setStateBytes(smol, state);
                            runOnUiThread(() -> tvOutput.append("\nKV cache cleared and restored."));
                        } catch (Throwable t) {
                            Log.w(TAG, "KV cache operations failed", t);
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error getting response", t);
                        runOnUiThread(() -> tvOutput.append("\nError: " + t.getMessage()));
                    }
                }).start();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (smol != null) {
            smol.close();
        }
    }
}
