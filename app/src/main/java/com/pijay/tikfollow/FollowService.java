package com.pijay.tikfollow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class FollowService extends AccessibilityService {
    public static volatile FollowService instance;

    public static final int MODE_FOLLOW = 0;
    public static final int MODE_LIKE = 1;

    private static final int MAX_FAIL_STREAK = 3;
    private static final int MAX_EMPTY_SCROLLS = 5;

    private static final String[] QUERIES = {"Follow", "Ikuti"};
    private static final List<String> LABELS = Arrays.asList(
        "follow", "ikuti", "follow back", "ikuti balik");

    private static final List<String> WARNINGS = Arrays.asList(
        "too fast", "too quickly", "following too many", "try again later",
        "action blocked", "temporarily blocked", "temporarily restricted",
        "you've reached", "reached the limit", "suspicious activity",
        "terlalu cepat", "coba lagi nanti",
        "diblokir sementara", "dibatasi sementara", "mencapai batas",
        "telah mencapai", "aktivitas mencurigakan", "tindakan diblokir");

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private Prefs cfg = new Prefs();
    private boolean running = false;
    private int mode = MODE_FOLLOW;
    private int count = 0;
    private int failStreak = 0;
    private int warnHits = 0;
    private int emptyStreak = 0;
    private int batchTarget = 0;
    private int batchDone = 0;
    private boolean resting = false;
    private long restEndAt = 0;
    private Rect lastBounds = null;
    private String stopReason = "";

    private boolean limitReached() {
        return cfg.maxTotal > 0 && count >= cfg.maxTotal;
    }

    // ===== MODE FOLLOW (batch + istirahat) =====
    private final Runnable startBatch = new Runnable() {
        @Override public void run() {
            if (!running) return;
            resting = false;
            batchTarget = cfg.batchMin + rnd.nextInt(cfg.batchMax - cfg.batchMin + 1);
            batchDone = 0;
            emptyStreak = 0;
            h.post(step);
        }
    };

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (!running) return;
            long stepMs = cfg.stepSec * 1000L;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || !isTikTok(root)) { h.postDelayed(this, stepMs); return; }
            if (hasWarning(root)) { stopWith("Peringatan TikTok terdeteksi"); return; }

            Rect clicked = clickFollow(root);
            if (clicked != null) {
                lastBounds = clicked;
                batchDone++;
                emptyStreak = 0;
                h.postDelayed(checkAfterClick, 2500);
            } else {
                emptyStreak++;
                if (emptyStreak >= MAX_EMPTY_SCROLLS) {
                    stopWith("Tidak ada tombol Follow di layar (ujung daftar / halaman salah)");
                    return;
                }
                swipeUp();
            }

            if (batchDone >= batchTarget) scheduleRest();
            else h.postDelayed(this, stepMs);
        }
    };

    private void scheduleRest() {
        long sec = cfg.restMinSec + rnd.nextInt(cfg.restMaxSec - cfg.restMinSec + 1);
        resting = true;
        restEndAt = System.currentTimeMillis() + sec * 1000L;
        h.postDelayed(startBatch, sec * 1000L);
    }

    private final Runnable checkAfterClick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || !isTikTok(root)) return;
            if (hasWarning(root)) { stopWith("Peringatan TikTok terdeteksi"); return; }
            if (stillThere(root, lastBounds)) {
                failStreak++;
                if (failStreak >= MAX_FAIL_STREAK) {
                    stopWith("Follow tidak berubah " + MAX_FAIL_STREAK + "x berturut-turut (kemungkinan dibatasi)");
                }
            } else {
                failStreak = 0;
                count++;
                if (limitReached()) stopWith("Batas jumlah tercapai");
            }
        }
    };

    // ===== MODE LIKE + SCROLL =====
    private final Runnable swipeNext = new Runnable() {
        @Override public void run() { if (running) swipeUp(); }
    };

    private final Runnable likeLoop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && isTikTok(root)) {
                if (hasWarning(root)) {
                    warnHits++;
                    if (warnHits >= 2) { stopWith("Peringatan TikTok terdeteksi"); return; }
                } else {
                    warnHits = 0;
                }
                doubleTapCenter();
                count++;
                if (limitReached()) { stopWith("Batas jumlah tercapai"); return; }
                h.postDelayed(swipeNext, 1200);
            }
            long ms = cfg.likeMinSec * 1000L
                + rnd.nextInt((cfg.likeMaxSec - cfg.likeMinSec) * 1000 + 1);
            h.postDelayed(this, ms);
        }
    };

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(android.content.Intent i) { stopLoop(); instance = null; return super.onUnbind(i); }

    public boolean isRunning() { return running; }
    public int getCount() { return count; }
    public String getStopReason() { return stopReason; }
    public int getMode() { return mode; }
    public void setMode(int m) { if (!running) mode = m; }

    public String getDetail() {
        if (!running || mode == MODE_LIKE) return "";
        if (resting) {
            long s = Math.max(0, (restEndAt - System.currentTimeMillis()) / 1000);
            return "Istirahat, lanjut dalam " + (s / 60) + " mnt " + (s % 60) + " dtk";
        }
        return "Batch: " + batchDone + "/" + batchTarget;
    }

    public void startLoop() {
        if (running) return;
        cfg = Prefs.load(getApplicationContext());
        running = true;
        count = 0;
        failStreak = 0;
        warnHits = 0;
        emptyStreak = 0;
        batchTarget = 0;
        batchDone = 0;
        resting = false;
        lastBounds = null;
        stopReason = "";
        h.postDelayed(mode == MODE_LIKE ? likeLoop : startBatch, 3000);
    }

    public void stopLoop() {
        running = false;
        resting = false;
        h.removeCallbacks(startBatch);
        h.removeCallbacks(step);
        h.removeCallbacks(checkAfterClick);
        h.removeCallbacks(likeLoop);
        h.removeCallbacks(swipeNext);
    }

    private void stopWith(String reason) {
        stopReason = reason;
        stopLoop();
        Toast.makeText(getApplicationContext(), "TikFollow berhenti: " + reason, Toast.LENGTH_LONG).show();
    }

    private boolean isTikTok(AccessibilityNodeInfo root) {
        CharSequence p = root.getPackageName();
        if (p == null) return false;
        String s = p.toString();
        return s.equals("com.zhiliaoapp.musically") || s.equals("com.ss.android.ugc.trill");
    }

    private boolean isFollowLabel(AccessibilityNodeInfo n) {
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String s = (t != null ? t.toString() : (d != null ? d.toString() : "")).trim().toLowerCase();
        return LABELS.contains(s);
    }

    private boolean hasWarning(AccessibilityNodeInfo node) {
        return scanWarning(node, 0);
    }

    private boolean scanWarning(AccessibilityNodeInfo n, int depth) {
        if (n == null || depth > 25) return false;
        CharSequence t = n.getText();
        CharSequence d = n.getContentDescription();
        String s = ((t != null ? t.toString() : "") + " " + (d != null ? d.toString() : "")).toLowerCase();
        if (s.length() > 3) {
            for (String w : WARNINGS) if (s.contains(w)) return true;
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            if (scanWarning(n.getChild(i), depth + 1)) return true;
        }
        return false;
    }

    private Rect clickFollow(AccessibilityNodeInfo root) {
        for (String q : QUERIES) {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(q);
            if (list == null) continue;
            for (AccessibilityNodeInfo n : list) {
                if (!isFollowLabel(n)) continue;
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    return r;
                }
            }
        }
        return null;
    }

    private boolean stillThere(AccessibilityNodeInfo root, Rect target) {
        if (target == null) return false;
        for (String q : QUERIES) {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(q);
            if (list == null) continue;
            for (AccessibilityNodeInfo n : list) {
                if (!isFollowLabel(n)) continue;
                Rect r = new Rect();
                n.getBoundsInScreen(r);
                if (r.equals(target)) return true;
            }
        }
        return false;
    }

    private void doubleTapCenter() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        float x = m.widthPixels * 0.5f;
        float y = m.heightPixels * 0.45f;
        Path p1 = new Path(); p1.moveTo(x, y);
        Path p2 = new Path(); p2.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p1, 0, 40))
            .addStroke(new GestureDescription.StrokeDescription(p2, 120, 40))
            .build();
        dispatchGesture(g, null, null);
    }

    private void swipeUp() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        float x = m.widthPixels / 2f;
        Path p = new Path();
        p.moveTo(x, m.heightPixels * 0.80f);
        p.lineTo(x, m.heightPixels * 0.30f);
        GestureDescription g = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p, 0, 450)).build();
        dispatchGesture(g, null, null);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() { stopLoop(); }
}
