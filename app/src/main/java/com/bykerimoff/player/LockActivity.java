package com.bykerimoff.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bykerimoff.player.databinding.ActivityLockBinding;

public class LockActivity extends AppCompatActivity {

    private ActivityLockBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLockBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        com.bykerimoff.player.utils.WallpaperManager.INSTANCE.applyWallpaper(this, binding.ivAppBackground);

        SharedPreferences prefs = getSharedPreferences("neoplay_prefs", MODE_PRIVATE);
        String savedPin = prefs.getString("app_pin", "0000");

        binding.btnUnlock.setOnClickListener(v -> {
            String enteredPin = binding.etLockPin.getText().toString();
            if (savedPin.equals(enteredPin)) {
                Intent intent = new Intent(LockActivity.this, MainActivity.class);
                intent.putExtra("is_unlocked", true);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(this, "Yanlış PİN kod!", Toast.LENGTH_SHORT).show();
                binding.etLockPin.setText("");
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishAffinity();
                System.exit(0);
            }
        });
    }
}
