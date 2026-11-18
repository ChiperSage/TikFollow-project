package com.pijay.tikfollow;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {
    public int batchMin = 1, batchMax = 5;
    public int stepSec = 5;
    public int restMinSec = 5, restMaxSec = 30;
    public int likeMinSec = 6, likeMaxSec = 12;
    public int maxTotal = 0; // 0 = tanpa batas

    public static Prefs load(Context c) {
        SharedPreferences p = c.getSharedPreferences("tikfollow", Context.MODE_PRIVATE);
        Prefs s = new Prefs();
        s.batchMin = p.getInt("batchMin", s.batchMin);
        s.batchMax = p.getInt("batchMax", s.batchMax);
        s.stepSec = p.getInt("stepSec", s.stepSec);
        s.restMinSec = p.getInt("restMinSec", s.restMinSec);
        s.restMaxSec = p.getInt("restMaxSec", s.restMaxSec);
        s.likeMinSec = p.getInt("likeMinSec", s.likeMinSec);
        s.likeMaxSec = p.getInt("likeMaxSec", s.likeMaxSec);
        s.maxTotal = p.getInt("maxTotal", s.maxTotal);
        return s.sanitize();
    }

    public void save(Context c) {
        c.getSharedPreferences("tikfollow", Context.MODE_PRIVATE).edit()
            .putInt("batchMin", batchMin).putInt("batchMax", batchMax)
            .putInt("stepSec", stepSec)
            .putInt("restMinSec", restMinSec).putInt("restMaxSec", restMaxSec)
            .putInt("likeMinSec", likeMinSec).putInt("likeMaxSec", likeMaxSec)
            .putInt("maxTotal", maxTotal)
            .apply();
    }

    public Prefs sanitize() {
        batchMin = Math.max(1, batchMin);
        batchMax = Math.max(batchMin, batchMax);
        stepSec = Math.max(3, stepSec);
        restMinSec = Math.max(1, restMinSec);
        restMaxSec = Math.max(restMinSec, restMaxSec);
        likeMinSec = Math.max(3, likeMinSec);
        likeMaxSec = Math.max(likeMinSec, likeMaxSec);
        maxTotal = Math.max(0, maxTotal);
        return this;
    }
}
