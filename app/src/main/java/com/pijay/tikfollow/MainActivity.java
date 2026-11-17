package com.pijay.tikfollow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private Button toggle, modeBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        toggle = findViewById(R.id.btnToggle);
        modeBtn = findViewById(R.id.btnMode);

        findViewById(R.id.btnEnable).setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        modeBtn.setOnClickListener(v -> {
            FollowService s = FollowService.instance;
            if (s == null) { needService(); return; }
            if (s.isRunning()) {
                Toast.makeText(this, "Stop dulu sebelum ganti mode", Toast.LENGTH_SHORT).show();
                return;
            }
            s.setMode(s.getMode() == FollowService.MODE_FOLLOW
                ? FollowService.MODE_LIKE : FollowService.MODE_FOLLOW);
            refresh();
        });

        toggle.setOnClickListener(v -> {
            FollowService s = FollowService.instance;
            if (s == null) { needService(); return; }
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

    private void needService() {
        Toast.makeText(this, "Aktifkan service dulu di Accessibility", Toast.LENGTH_LONG).show();
    }

    @Override protected void onResume() { super.onResume(); ui.post(tick); }
    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }

    private void refresh() {
        FollowService s = FollowService.instance;
        if (s == null) {
            status.setText("Service belum aktif");
            toggle.setText("Start");
            modeBtn.setText("Mode: Follow");
            return;
        }
        boolean like = s.getMode() == FollowService.MODE_LIKE;
        String label = like ? "Like" : "Follow";
        modeBtn.setText(like ? "Mode: Like + Scroll" : "Mode: Follow");
        if (s.isRunning()) {
            String d = s.getDetail();
            status.setText("Berjalan. " + label + ": " + s.getCount() + (d.isEmpty() ? "" : "\n" + d));
            toggle.setText("Stop");
        } else {
            String r = s.getStopReason();
            status.setText("Berhenti" + (r.isEmpty() ? "" : " (" + r + ")") + ". " + label + ": " + s.getCount());
            toggle.setText("Start");
        }
    }
}
