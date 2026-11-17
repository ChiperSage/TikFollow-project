package com.pijay.tikfollow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private Button toggle, modeBtn;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 1000); }
    };

    private Button mk(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 0, 0, pad / 2);
        root.addView(status);

        Button btnEnable = mk("Aktifkan service (Accessibility)");
        modeBtn = mk("Mode: Follow");
        toggle = mk("Start");
        Button btnOpen = mk("Buka TikTok");
        root.addView(btnEnable);
        root.addView(modeBtn);
        root.addView(toggle);
        root.addView(btnOpen);

        TextView info = new TextView(this);
        info.setPadding(0, pad / 2, 0, 0);
        info.setText("Mode Follow: buka halaman Suggested / Activity, lalu Start. "
            + "Bot follow 1-10 akun (jeda 5 dtk), istirahat 1-15 menit, lalu ulang. "
            + "Mode Like + Scroll: buka feed For You, lalu Start.");
        root.addView(info);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        btnEnable.setOnClickListener(v ->
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

        btnOpen.setOnClickListener(v -> {
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
