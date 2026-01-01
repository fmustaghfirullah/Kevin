package com.ghfir.whatsapp.kevin;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MenuActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        Button startDetectionButton = findViewById(R.id.start_detection_button);
        startDetectionButton.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, MainActivity.class);
            startActivity(intent);
        });

        Button instructionsButton = findViewById(R.id.instructions_button);
        instructionsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MenuActivity.this, InstructionsActivity.class);
            startActivity(intent);
        });
    }
}
