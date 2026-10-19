package br.com.lucasgasparino.niveldigital;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Preferencias e calibracoes (SharedPreferences "nivel_digital").
 * Valor ausente, invalido ou de tipo errado cai no padrao; nunca lanca excecao para quem le.
 */
final class LevelSettings {
    private static final String PREFS = "nivel_digital";
    private static final float[] TOLERANCES = {0.1f, 0.2f, 0.5f, 1.0f};
    private static final float DEFAULT_TOLERANCE = 0.2f;
    /** Calibracao maior que isso nunca e gravada; acima disso o valor lido e tratado como ausente. */
    private static final float MAX_CAL_DEG = 12f;

    private static final String K_MODE = "mode";
    private static final String K_SOUND = "sound";
    private static final String K_VIBRATE = "vibrate";
    private static final String K_UNIT = "unit";
    private static final String K_TOLERANCE = "tolerance";
    private static final String K_SURF_X = "cal_surf_x";
    private static final String K_SURF_Y = "cal_surf_y";

    private final SharedPreferences prefs;

    LevelSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    int mode() {
        int m = readInt(K_MODE, LevelMath.MODE_AUTO);
        return m >= LevelMath.MODE_AUTO && m <= LevelMath.MODE_EDGE ? m : LevelMath.MODE_AUTO;
    }

    void setMode(int mode) {
        prefs.edit().putInt(K_MODE, mode).apply();
    }

    boolean sound() {
        return readBool(K_SOUND, true);
    }

    void setSound(boolean on) {
        prefs.edit().putBoolean(K_SOUND, on).apply();
    }

    boolean vibrate() {
        return readBool(K_VIBRATE, true);
    }

    void setVibrate(boolean on) {
        prefs.edit().putBoolean(K_VIBRATE, on).apply();
    }

    int unit() {
        return readInt(K_UNIT, LevelMath.UNIT_DEG) == LevelMath.UNIT_PERCENT
                ? LevelMath.UNIT_PERCENT : LevelMath.UNIT_DEG;
    }

    void setUnit(int unit) {
        prefs.edit().putInt(K_UNIT, unit).apply();
    }

    /** Tolerancia em graus, sempre uma das opcoes da lista. */
    double tolerance() {
        Float t = readFloat(K_TOLERANCE);
        if (t == null) {
            return DEFAULT_TOLERANCE;
        }
        for (float option : TOLERANCES) {
            if (Math.abs(t - option) < 1e-4f) {
                return option;
            }
        }
        return DEFAULT_TOLERANCE;
    }

    /** Indice da tolerancia atual dentro das opcoes, para a interface. */
    int toleranceIndex() {
        double t = tolerance();
        for (int i = 0; i < TOLERANCES.length; i++) {
            if (Math.abs(t - TOLERANCES[i]) < 1e-4) {
                return i;
            }
        }
        return 1;
    }

    static int toleranceCount() {
        return TOLERANCES.length;
    }

    static double toleranceAt(int index) {
        return TOLERANCES[index];
    }

    void setTolerance(double deg) {
        prefs.edit().putFloat(K_TOLERANCE, (float) deg).apply();
    }

    boolean hasSurfaceCal() {
        return readCal(K_SURF_X) != null && readCal(K_SURF_Y) != null;
    }

    /** Offset lateral da superficie (0 se nao calibrado). */
    double surfX() {
        Float v = readCal(K_SURF_X);
        return v == null ? 0.0 : v;
    }

    double surfY() {
        Float v = readCal(K_SURF_Y);
        return v == null ? 0.0 : v;
    }

    void setSurfaceCal(double x, double y) {
        prefs.edit().putFloat(K_SURF_X, (float) x).putFloat(K_SURF_Y, (float) y).apply();
    }

    void clearSurfaceCal() {
        prefs.edit().remove(K_SURF_X).remove(K_SURF_Y).apply();
    }

    boolean hasEdgeCal(int q) {
        return readCal(edgeKey(q)) != null;
    }

    /** Offset da borda do quarto q (0 se nao calibrado). */
    double edgeCal(int q) {
        Float v = readCal(edgeKey(q));
        return v == null ? 0.0 : v;
    }

    void setEdgeCal(int q, double v) {
        prefs.edit().putFloat(edgeKey(q), (float) v).apply();
    }

    void clearEdgeCal(int q) {
        prefs.edit().remove(edgeKey(q)).apply();
    }

    void clearAllCal() {
        SharedPreferences.Editor e = prefs.edit().remove(K_SURF_X).remove(K_SURF_Y);
        for (int q = 0; q < 4; q++) {
            e.remove(edgeKey(q));
        }
        e.apply();
    }

    private static String edgeKey(int q) {
        return "cal_edge_" + LevelMath.wrapQuarter(q);
    }

    private int readInt(String key, int def) {
        try {
            return prefs.getInt(key, def);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private boolean readBool(String key, boolean def) {
        try {
            return prefs.getBoolean(key, def);
        } catch (RuntimeException e) {
            return def;
        }
    }

    private Float readFloat(String key) {
        try {
            if (!prefs.contains(key)) {
                return null;
            }
            float v = prefs.getFloat(key, Float.NaN);
            return LevelMath.finite(v) ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Float readCal(String key) {
        Float v = readFloat(key);
        if (v == null || Math.abs(v) > MAX_CAL_DEG) {
            return null;
        }
        return v;
    }
}
