package com.pijay.tikfollow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class FollowService extends AccessibilityService {
    public static volatile FollowService instance;

    private static final int MAX_PER_SESSION = 100;
    private static final List<String> LABELS = Arrays.asList(
        "follow", "ikuti", "follow back", "ikuti balik");

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean running = false;
    private int count = 0;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                if (clickFollow(root)) {
                    count++;          // masih ada tombol Follow: klik satu, jangan scroll dulu
                } else {
                    swipeUp();        // tidak ada lagi di layar: scroll ke halaman berikutnya
                }
            }
            if (count >= MAX_PER_SESSION) { stopLoop(); return; }
            h.postDelayed(this, 15000 + rnd.nextInt(3000)); // 15-18 detik
        }
    };

    @Override protected void onServiceConnected() { instance = this; }
    @Override public boolean onUnbind(android.content.Intent i) { stopLoop(); instance = null; return super.onUnbind(i); }

    public boolean isRunning() { return running; }
    public int getCount() { return count; }

    public void startLoop() {
        if (running) return;
        running = true;
        count = 0;
        h.postDelayed(loop, 3000);
    }

    public void stopLoop() {
        running = false;
        h.removeCallbacks(loop);
    }

    private boolean clickFollow(AccessibilityNodeInfo root) {
        for (String q : new String[]{"Follow", "Ikuti"}) {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(q);
            if (list == null) continue;
            for (AccessibilityNodeInfo n : list) {
                CharSequence t = n.getText();
                CharSequence d = n.getContentDescription();
                String s = (t != null ? t.toString() : (d != null ? d.toString() : "")).trim().toLowerCase();
                if (!LABELS.contains(s)) continue; // lewati "Following", "Friends", dll.
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        return false;
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
