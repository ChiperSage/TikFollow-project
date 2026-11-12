package com.pijay.tikfollow;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;
import java.util.Random;

public class FollowService extends AccessibilityService {
    public static volatile FollowService instance;

    private static final int MAX_PER_SESSION = 100;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean running = false;
    private int count = 0;

    private final Runnable loop = new Runnable() {
        @Override public void run() {
            if (!running) return;
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                if (clickFollow(root)) count++;
                if (!scrollNode(root)) swipeUp();
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
        for (String label : new String[]{"Follow", "Ikuti"}) {
            List<AccessibilityNodeInfo> list = root.findAccessibilityNodeInfosByText(label);
            if (list == null) continue;
            for (AccessibilityNodeInfo n : list) {
                CharSequence t = n.getText();
                CharSequence d = n.getContentDescription();
                String s = t != null ? t.toString() : (d != null ? d.toString() : "");
                if (!s.equalsIgnoreCase(label)) continue; // lewati "Following"/"Mengikuti"
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            }
        }
        return false;
    }

    private boolean scrollNode(AccessibilityNodeInfo n) {
        if (n == null) return false;
        if (n.isScrollable() && n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
        for (int i = 0; i < n.getChildCount(); i++) {
            if (scrollNode(n.getChild(i))) return true;
        }
        return false;
    }

    private void swipeUp() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        float x = m.widthPixels / 2f;
        Path p = new Path();
        p.moveTo(x, m.heightPixels * 0.75f);
        p.lineTo(x, m.heightPixels * 0.35f);
        GestureDescription g = new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p, 0, 400)).build();
        dispatchGesture(g, null, null);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() { stopLoop(); }
}
