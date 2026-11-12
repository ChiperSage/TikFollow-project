package com.pijay.tikfollow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private Button toggle;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        toggle = findViewById(R.id.btnToggle);

        findViewById(R.id.btnEnable).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        toggle.setOnClickListener(v -> {
            FollowService s = FollowService.instance;
            if (s == null) {
                Toast.makeText(this, "Aktifkan service dulu di Accessibility", Toast.LENGTH_LONG).show();
                return;
            }
            if (s.isRunning()) s.stopLoop(); else s.startLoop();
            refresh();
        });

        findViewById(R.id.btnOpen).setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.zhiliaoapp.musically");
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.ss.android.ugc.trill");
            if (i != null) startActivity(i);
            else Toast.makeText(this, "TikTok tidak ditemukan", Toast.LENGTH_SHORT).show();
        });
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        FollowService s = FollowService.instance;
        if (s == null) {
            status.setText("Service belum aktif");
            toggle.setText("Start");
        } else {
            status.setText(s.isRunning() ? "Berjalan. Follow: " + s.getCount() : "Service aktif, berhenti. Follow: " + s.getCount());
            toggle.setText(s.isRunning() ? "Stop" : "Start");
        }
    }
}
