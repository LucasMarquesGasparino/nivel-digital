package br.com.lucasgasparino.niveldigital;

import java.util.Locale;

/**
 * Matematica do nivel, em Java puro (sem Android), para ser testada na JVM.
 *
 * Convencoes: (x, y, z) sao valores do acelerometro no referencial da TELA
 * (x = direita, y = topo, z = saindo da tela), com +g no eixo que aponta para cima.
 * Quarto q (0..3) e qual borda do aparelho esta para baixo: q0 = borda inferior,
 * q1 = lateral esquerda, q2 = borda superior, q3 = lateral direita.
 * u = direita do usuario, v = cima do usuario.
 */
final class LevelMath {
    static final int MODE_AUTO = 0;
    static final int MODE_SURFACE = 1;
    static final int MODE_EDGE = 2;

    static final int UNIT_DEG = 0;
    static final int UNIT_PERCENT = 1;

    static final double SURFACE_SCALE_DEG = 8.0;
    static final double EDGE_SCALE_DEG = 6.0;

    private static final Locale PT_BR = new Locale.Builder().setLanguage("pt").setRegion("BR").build();
    private static final char MINUS_SIGN = '−';

    private LevelMath() {
    }

    /** Angulo (graus) do eixo "a" acima da horizontal, dado o vetor de gravidade (a, b, c). */
    static double axisElevation(double a, double b, double c) {
        return Math.toDegrees(Math.atan2(a, Math.hypot(b, c)));
    }

    static int wrapQuarter(int q) {
        return ((q % 4) + 4) % 4;
    }

    /** Componente u (direita do usuario) de um vetor da tela, para o quarto q. */
    static double toU(int q, double x, double y) {
        switch (wrapQuarter(q)) {
            case 0:
                return x;
            case 1:
                return -y;
            case 2:
                return -x;
            default:
                return y;
        }
    }

    /** Componente v (cima do usuario) de um vetor da tela, para o quarto q. */
    static double toV(int q, double x, double y) {
        switch (wrapQuarter(q)) {
            case 0:
                return y;
            case 1:
                return x;
            case 2:
                return -y;
            default:
                return -x;
        }
    }

    /** Leva um angulo a (-180, 180]. */
    static double normalize180(double deg) {
        double d = deg % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        } else if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }

    /** Quarto candidato com histerese de 57 graus (45 + 12) em torno do quarto atual. */
    static int updateQuarter(int current, double x, double y, double z) {
        int cur = wrapQuarter(current);
        double norm = Math.sqrt(x * x + y * y + z * z);
        if (!(norm > 1e-6)) {
            return cur;
        }
        double inPlane = Math.hypot(x, y) / norm;
        if (inPlane < 0.5) {
            // Aparelho quase deitado: o quarto nao tem sentido, mantem.
            return cur;
        }
        double phi = Math.toDegrees(Math.atan2(x, y));
        double diff = normalize180(phi - cur * 90.0);
        if (Math.abs(diff) <= 57.0) {
            return cur;
        }
        return wrapQuarter((int) Math.round(phi / 90.0));
    }

    /** Inclinacao em relacao ao plano deitado (0 = tela para cima). */
    static double tiltFromFlat(double x, double y, double z) {
        double norm = Math.sqrt(x * x + y * y + z * z);
        if (!(norm > 1e-9)) {
            return 90.0;
        }
        return Math.toDegrees(Math.acos(clamp(Math.abs(z) / norm, 0.0, 1.0)));
    }

    /** Modo Auto com histerese: superficie abaixo de 45 graus, sai dela acima de 55. */
    static boolean autoIsSurface(boolean currentSurface, double tiltFromFlatDeg) {
        return currentSurface ? tiltFromFlatDeg < 55.0 : tiltFromFlatDeg < 45.0;
    }

    static final class Reading {
        int mode = MODE_SURFACE;
        /** Inclinacao lateral (direita positiva) e frente/tras (cima positivo), em graus. */
        double xu;
        double yu;
        /** Inclinacao total, em graus. */
        double total;
        /** Borda: positivo = lado direito mais alto. */
        double edge;
        /** Prumo: 0 = aparelho na vertical; positivo = tela virada para cima. */
        double lean;
        double tiltFromFlat;

        void copyFrom(Reading o) {
            mode = o.mode;
            xu = o.xu;
            yu = o.yu;
            total = o.total;
            edge = o.edge;
            lean = o.lean;
            tiltFromFlat = o.tiltFromFlat;
        }
    }

    /**
     * Calcula a leitura. (x, y, z) no referencial da tela; mode ja resolvido (SURFACE ou EDGE);
     * calibracoes ausentes valem 0.
     */
    static void compute(Reading out, double x, double y, double z, int q, int mode,
                        double calSx, double calSy, double calEdge) {
        double xd = axisElevation(x, y, z) - calSx;
        double yd = axisElevation(y, x, z) - calSy;
        out.xu = toU(q, xd, yd);
        out.yu = toV(q, xd, yd);
        double su = Math.sin(Math.toRadians(out.xu));
        double sv = Math.sin(Math.toRadians(out.yu));
        out.total = Math.toDegrees(Math.asin(Math.min(1.0, Math.sqrt(su * su + sv * sv))));
        double u = toU(q, x, y);
        double v = toV(q, x, y);
        out.edge = axisElevation(u, v, z) - calEdge;
        out.lean = axisElevation(z, u, v);
        out.tiltFromFlat = tiltFromFlat(x, y, z);
        out.mode = mode;
    }

    /**
     * Filtro adaptativo sobre o vetor de gravidade em dois estagios: "fast" suaviza o ruido,
     * "slow" fica estavel parado (0,5 s) e agil em movimento (0,05 s).
     */
    static final class Filter {
        final double[] fast = new double[3];
        final double[] slow = new double[3];
        boolean init;

        void reset() {
            init = false;
        }

        void step(double x, double y, double z, double dtSec) {
            if (!finite(x) || !finite(y) || !finite(z)) {
                return;
            }
            double[] raw = {x, y, z};
            if (!init || !(dtSec > 0.0) || dtSec > 0.5) {
                System.arraycopy(raw, 0, fast, 0, 3);
                System.arraycopy(raw, 0, slow, 0, 3);
                init = true;
                return;
            }
            double dt = clamp(dtSec, 0.001, 0.2);
            double a = dt / (0.05 + dt);
            for (int i = 0; i < 3; i++) {
                fast[i] += a * (raw[i] - fast[i]);
            }
            double dev = angleBetweenDeg(slow, fast);
            double t = clamp((dev - 0.25) / (2.5 - 0.25), 0.0, 1.0);
            double tau = 0.5 + (0.05 - 0.5) * t;
            double b = dt / (tau + dt);
            for (int i = 0; i < 3; i++) {
                slow[i] += b * (fast[i] - slow[i]);
            }
        }
    }

    /** Arredonda para 1 casa, simetrico: round1(-1.25) = -1.3. */
    static double round1(double v) {
        if (!finite(v)) {
            return 0.0;
        }
        return Math.signum(v) * Math.round(Math.abs(v) * 10.0) / 10.0;
    }

    /** Valor exibido sem tremor: so muda quando o valor se afasta 0,07 do exibido. */
    static double stepDisplay(double shown, double value, boolean hasShown) {
        if (!hasShown || !finite(shown)) {
            return round1(value);
        }
        if (Math.abs(value - shown) < 0.07) {
            return shown;
        }
        return round1(value);
    }

    static double toUnit(double deg, int unit) {
        if (unit == UNIT_PERCENT) {
            return Math.tan(Math.toRadians(clamp(deg, -89.9, 89.9))) * 100.0;
        }
        return deg;
    }

    /** Formato pt-BR com 1 casa, sem "-0,0" e com sinal de menos U+2212. */
    static String format1(double v) {
        if (!finite(v) || Math.abs(v) < 0.05) {
            return "0,0";
        }
        return String.format(PT_BR, "%.1f", v).replace('-', MINUS_SIGN);
    }

    static String formatSigned1(double v) {
        if (!finite(v) || Math.abs(v) < 0.05) {
            return "0,0";
        }
        String s = format1(v);
        return v > 0 ? "+" + s : s;
    }

    /** Nivel com histerese: entra com mag <= tol e sai com mag > tol + max(0,1; tol/2). */
    static boolean updateLevel(boolean wasLevel, double magnitudeDeg, double tolDeg) {
        if (wasLevel) {
            return magnitudeDeg <= tolDeg + Math.max(0.1, tolDeg * 0.5);
        }
        return magnitudeDeg <= tolDeg;
    }

    /** Posicao da bolha 2D (modulo menor que 1), em out[0..1]. */
    static void bubble2D(double xu, double yu, double scaleDeg, double[] out) {
        double m = Math.hypot(xu, yu);
        if (m < 1e-9) {
            out[0] = 0.0;
            out[1] = 0.0;
            return;
        }
        double k = Math.tanh(m / scaleDeg) / m;
        out[0] = xu * k;
        out[1] = yu * k;
    }

    static double bubble1D(double edgeDeg, double scaleDeg) {
        return Math.tanh(edgeDeg / scaleDeg);
    }

    static double clamp(double v, double lo, double hi) {
        if (v < lo) {
            return lo;
        }
        return v > hi ? hi : v;
    }

    static boolean finite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private static double norm3(double[] a) {
        return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    private static double angleBetweenDeg(double[] a, double[] b) {
        double na = norm3(a);
        double nb = norm3(b);
        if (!(na > 1e-9) || !(nb > 1e-9)) {
            return 0.0;
        }
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        return Math.toDegrees(Math.acos(clamp(dot / (na * nb), -1.0, 1.0)));
    }
}
