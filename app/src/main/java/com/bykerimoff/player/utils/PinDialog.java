package com.bykerimoff.player.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

public class PinDialog {

    public interface PinListener {
        void onSuccess();
        void onCancel();
    }

    public static void show(Context context, PinListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Valideyn Nəzarəti");
        builder.setMessage("Zəhmət olmasa PİN kodu daxil edin:");

        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        
        android.content.SharedPreferences prefs = context.getSharedPreferences("neoplay_prefs", Context.MODE_PRIVATE);
        String savedPin = prefs.getString("app_pin", "2266"); // Standart: 2266
        
        input.setHint("PİN (" + savedPin + ")");
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        builder.setView(input);

        builder.setPositiveButton("Təsdiqlə", (dialog, which) -> {
            String pin = input.getText().toString();
            if (savedPin.equals(pin)) {
                listener.onSuccess();
            } else {
                android.widget.Toast.makeText(context, "Yanlış PIN!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Ləğv et", (dialog, which) -> {
            dialog.cancel();
            listener.onCancel();
        });

        builder.show();
    }
}
