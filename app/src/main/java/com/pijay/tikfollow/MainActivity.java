package com.pijay.tikfollow;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private TextView status;
    private Button toggle, modeBtn;
    private EditText fBatchMin, fBatchMax, fStep, fRestMin, fRestMax, fLikeMin, fLikeMax, fMax;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 1000); }
    };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private Button mk(String text) {
        Button b = new Button(this);
        b.setText(text);
        return b;
    }

    private EditText num() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setGravity(Gravity.CENTER);
        e.setSingleLine(true);
        e.setLayoutParams(new LinearLayout.LayoutParams(dp(72), LinearLayout.LayoutParams.WRAP_CONTENT));
        return e;
    }

    // Satu baris form: label + 1 atau 2 kolom angka
    private void row(LinearLayout parent, String label, EditText a, EditText b2) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(label);
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.addView(t);
        r.addView(a);
        if (b2 != null) r.addView(b2);
        parent.addView(r);
    }

    private int parse(EditText e, int def) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return def; }
    }

    private void fill(Prefs p) {
        fBatchMin.setText(String.valueOf(p.batchMin));
        fBatchMax.setText(String.valueOf(p.batchMax));
        fStep.setText(String.valueOf(p.stepSec));
        fRestMin.setText(String.valueOf(p.restMinSec));
        fRestMax.setText(String.valueOf(p.restMaxSec));
        fLikeMin.setText(String.valueOf(p.likeMinSec));
        fLikeMax.setText(String.valueOf(p.likeMaxSec));
        fMax.setText(String.valueOf(p.maxTotal));
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 0, 0, dp(12));
        root.addView(status);

        Button btnEnable = mk("Aktifkan service (Accessibility)");
        modeBtn = mk("Mode: Follow");
        toggle = mk("Start");
        Button btnOpen = mk("Buka TikTok");
        root.addView(btnEnable);
        root.addView(modeBtn);
        root.addView(toggle);
        root.addView(btnOpen);

        // ===== Form pengaturan =====
        TextView head = new TextView(this);
        head.setText("Pengaturan");
        head.setTextSize(18);
        head.setPadding(0, dp(20), 0, dp(8));
        root.addView(head);

        fBatchMin = num(); fBatchMax = num();
        fStep = num();
        fRestMin = num(); fRestMax = num();
        fLikeMin = num(); fLikeMax = num();
        fMax = num();

        row(root, "Klik per batch (min / maks)", fBatchMin, fBatchMax);
        row(root, "Jeda antar klik (detik, min 3)", fStep, null);
        row(root, "Jeda antar batch (detik, min / maks)", fRestMin, fRestMax);
        row(root, "Interval Like (detik, min / maks, min 3)", fLikeMin, fLikeMax);
        row(root, "Batas total (0 = tanpa batas)", fMax, null);

        Button save = mk("Simpan pengaturan");
        Button reset = mk("Kembalikan default");
        root.addView(save);
        root.addView(reset);

        fill(Prefs.load(this));

        TextView info = new TextView(this);
        info.setPadding(0, dp(12), 0, 0);
        info.setText("Pengaturan berlaku saat Start berikutnya. Bot berhenti otomatis jika ada peringatan TikTok.\n\n"
            + "Mode Follow: buka halaman Suggested / Activity lalu Start.\n"
            + "Mode Like + Scroll: buka feed For You lalu Start.");
        root.addView(info);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        save.setOnClickListener(v -> {
            Prefs p = new Prefs();
            p.batchMin = parse(fBatchMin, p.batchMin);
            p.batchMax = parse(fBatchMax, p.batchMax);
            p.stepSec = parse(fStep, p.stepSec);
            p.restMinSec = parse(fRestMin, p.restMinSec);
            p.restMaxSec = parse(fRestMax, p.restMaxSec);
            p.likeMinSec = parse(fLikeMin, p.likeMinSec);
            p.likeMaxSec = parse(fLikeMax, p.likeMaxSec);
            p.maxTotal = parse(fMax, p.maxTotal);
            p.sanitize().save(this);
            fill(p);
            FollowService s = FollowService.instance;
            boolean run = s != null && s.isRunning();
            Toast.makeText(this, run ? "Tersimpan, berlaku saat Start berikutnya" : "Tersimpan", Toast.LENGTH_SHORT).show();
        });

        reset.setOnClickListener(v -> {
            Prefs p = new Prefs();
            p.save(this);
            fill(p);
            Toast.makeText(this, "Dikembalikan ke default", Toast.LENGTH_SHORT).show();
        });

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
