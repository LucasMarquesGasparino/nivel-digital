package br.com.lucasgasparino.niveldigital;

/** Testes da matematica do nivel na JVM (sem Android). Rode por tests/run.sh. */
public final class LevelMathTest {
    private static int checks;
    private static int failures;

    private static void check(boolean cond, String msg) {
        checks++;
        if (!cond) {
            failures++;
            System.out.println("FALHA: " + msg);
        }
    }

    private static boolean near(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    private static double rad(double deg) {
        return Math.toRadians(deg);
    }

    /** Vetor de gravidade cuja elevacao do eixo x e xDeg e a do eixo y e yDeg. */
    private static double[] vecFromElevations(double xDeg, double yDeg) {
        double sx = Math.sin(rad(xDeg));
        double sy = Math.sin(rad(yDeg));
        return new double[] {sx, sy, Math.sqrt(1.0 - sx * sx - sy * sy)};
    }

    public static void main(String[] args) {
        testAxisElevation();
        testQuarterMapping();
        testNormalize();
        testUpdateQuarter();
        testCompute();
        testAutoHysteresis();
        testFilter();
        testDisplayRounding();
        testFormatting();
        testLevelAndBubble();

        System.out.println("OK: " + checks + " verificacoes");
        if (failures > 0) {
            System.out.println(failures + " falha(s)");
            System.exit(1);
        }
    }

    private static void testAxisElevation() {
        check(near(LevelMath.axisElevation(0, 0, 1), 0, 1e-9), "axisElevation deitado deve ser 0");
        check(near(LevelMath.axisElevation(Math.sin(rad(2)), 0, Math.cos(rad(2))), 2, 1e-9),
                "axisElevation de 2 graus");
        check(near(LevelMath.axisElevation(0, 1, 0), 0, 1e-9), "axisElevation no plano deve ser 0");
        check(near(LevelMath.axisElevation(-Math.sin(rad(30)), 0, Math.cos(rad(30))), -30, 1e-9),
                "axisElevation negativo de 30 graus");
    }

    private static void testQuarterMapping() {
        // "Cima" do aparelho em cada quarto: q0 = +y, q1 = +x, q2 = -y, q3 = -x.
        double[][] up = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int q = 0; q < 4; q++) {
            double cx = up[q][0];
            double cy = up[q][1];
            check(near(LevelMath.toU(q, cx, cy), 0, 1e-12) && near(LevelMath.toV(q, cx, cy), 1, 1e-12),
                    "cima do quarto " + q + " deve mapear para (0,+1)");
        }
        // Mapeamento e rotacao propria: toU/toV preservam comprimento.
        for (int q = 0; q < 4; q++) {
            double u = LevelMath.toU(q, 0.3, -0.7);
            double v = LevelMath.toV(q, 0.3, -0.7);
            check(near(u * u + v * v, 0.3 * 0.3 + 0.7 * 0.7, 1e-12), "toU/toV devem ser rotacao, q=" + q);
        }
        check(near(LevelMath.toU(5, 1, 0), LevelMath.toU(1, 1, 0), 1e-12), "q deve ser tomado mod 4");
        check(near(LevelMath.toV(-1, 1, 0), LevelMath.toV(3, 1, 0), 1e-12), "q negativo deve ser tomado mod 4");
    }

    private static void testNormalize() {
        check(near(LevelMath.normalize180(190), -170, 1e-9), "normalize180(190) = -170");
        check(near(LevelMath.normalize180(-190), 170, 1e-9), "normalize180(-190) = 170");
        check(near(LevelMath.normalize180(180), 180, 1e-9), "normalize180(180) = 180");
        check(near(LevelMath.normalize180(-180), 180, 1e-9), "normalize180(-180) = 180");
        check(near(LevelMath.normalize180(45), 45, 1e-9), "normalize180(45) = 45");
    }

    private static void testUpdateQuarter() {
        check(LevelMath.updateQuarter(0, 0, 1, 0) == 0, "+y deve dar q0");
        check(LevelMath.updateQuarter(0, 1, 0, 0) == 1, "+x a partir de q0 deve dar q1");
        check(LevelMath.updateQuarter(0, 0, -1, 0) == 2, "-y a partir de q0 deve dar q2");
        check(LevelMath.updateQuarter(0, -1, 0, 0) == 3, "-x a partir de q0 deve dar q3");
        check(LevelMath.updateQuarter(2, 0.1, 0.1, 1) == 2, "quase deitado deve manter o quarto");
        check(LevelMath.updateQuarter(0, Math.sin(rad(50)), Math.cos(rad(50)), 0) == 0,
                "50 graus de phi deve manter q0 (histerese)");
        check(LevelMath.updateQuarter(0, Math.sin(rad(60)), Math.cos(rad(60)), 0) == 1,
                "60 graus de phi deve ir para q1");
    }

    private static void testCompute() {
        LevelMath.Reading r = new LevelMath.Reading();
        double[] v = vecFromElevations(1, 2);
        LevelMath.compute(r, v[0], v[1], v[2], 1, LevelMath.MODE_SURFACE, 0, 0, 0);
        check(near(r.xu, -2, 1e-9), "q1 com xd=1 yd=2 deve dar xu=-2, era " + r.xu);
        check(near(r.yu, 1, 1e-9), "q1 com xd=1 yd=2 deve dar yu=1, era " + r.yu);

        LevelMath.compute(r, 0, 0, 1, 0, LevelMath.MODE_SURFACE, 0, 0, 0);
        check(near(r.total, 0, 1e-9), "total deve ser 0 deitado");
        check(near(r.xu, 0, 1e-9) && near(r.yu, 0, 1e-9), "xu e yu devem ser 0 deitado");

        double[] w = vecFromElevations(3, 0);
        LevelMath.compute(r, w[0], w[1], w[2], 0, LevelMath.MODE_SURFACE, 0, 0, 0);
        check(near(r.total, 3, 1e-9), "total com so xu=3 deve ser 3, era " + r.total);

        // Total exato tambem para quartos diferentes (so troca eixos).
        LevelMath.compute(r, w[0], w[1], w[2], 1, LevelMath.MODE_SURFACE, 0, 0, 0);
        check(near(r.total, 3, 1e-9), "total deve ser invariante ao quarto");

        // Calibracao de superficie subtrai o offset.
        LevelMath.compute(r, w[0], w[1], w[2], 0, LevelMath.MODE_SURFACE, 3, 0, 0);
        check(near(r.total, 0, 1e-9), "calibracao de 3 graus deve zerar o total");

        // Borda: lado direito mais alto em q0 da positivo.
        LevelMath.compute(r, Math.sin(rad(2)), 0, Math.cos(rad(2)), 0, LevelMath.MODE_EDGE, 0, 0, 0);
        check(near(r.edge, 2, 1e-9), "borda com direita 2 graus mais alta deve dar +2, era " + r.edge);
        LevelMath.compute(r, Math.sin(rad(2)), 0, Math.cos(rad(2)), 0, LevelMath.MODE_EDGE, 0, 0, 2);
        check(near(r.edge, 0, 1e-9), "calibracao de borda de 2 graus deve zerar");

        // Prumo: 0 na vertical, +90 com a tela para cima.
        LevelMath.compute(r, 0, 1, 0, 0, LevelMath.MODE_EDGE, 0, 0, 0);
        check(near(r.lean, 0, 1e-9), "prumo deve ser 0 na vertical");
        LevelMath.compute(r, 0, 0, 1, 0, LevelMath.MODE_EDGE, 0, 0, 0);
        check(near(r.lean, 90, 1e-9), "prumo deve ser 90 com a tela para cima");
    }

    private static void testAutoHysteresis() {
        check(!LevelMath.autoIsSurface(false, 50), "de borda, 50 graus nao vira superficie");
        check(LevelMath.autoIsSurface(true, 50), "de superficie, 50 graus fica superficie");
        check(LevelMath.autoIsSurface(false, 40), "de borda, 40 graus vira superficie");
        check(!LevelMath.autoIsSurface(true, 60), "de superficie, 60 graus vira borda");
    }

    private static void testFilter() {
        LevelMath.Filter f = new LevelMath.Filter();
        for (int i = 0; i < 100; i++) {
            f.step(0, 9.8, 0, 0.02);
        }
        check(near(f.slow[1], 9.8, 1e-6) && near(f.fast[1], 9.8, 1e-6),
                "entrada constante deve convergir");

        // Degrau grande (de em pe para deitado) acompanhado em menos de 0,5 s.
        for (int i = 0; i < 50; i++) {
            f.step(9.8, 0, 0, 0.02);
        }
        double[] target = {9.8, 0, 0};
        double dev = angleDeg(f.slow, target);
        check(dev < 1.0, "degrau deve ser acompanhado em 1 s, desvio " + dev);

        LevelMath.Filter g = new LevelMath.Filter();
        g.step(0, 9.8, 0, 0.02);
        g.step(Double.NaN, 0, 0, 0.02);
        check(near(g.slow[1], 9.8, 1e-9), "amostra NaN deve ser ignorada");
    }

    private static double angleDeg(double[] a, double[] b) {
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double na = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
        double nb = Math.sqrt(b[0] * b[0] + b[1] * b[1] + b[2] * b[2]);
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot / (na * nb)))));
    }

    private static void testDisplayRounding() {
        check(near(LevelMath.round1(0.26), 0.3, 1e-9), "round1(0.26) = 0.3");
        check(near(LevelMath.round1(-1.25), -1.3, 1e-9), "round1(-1.25) = -1.3");
        check(near(LevelMath.stepDisplay(0.2, 0.26, true), 0.2, 1e-9), "exibido 0,2 com valor 0,26 segura 0,2");
        check(near(LevelMath.stepDisplay(0.2, 0.28, true), 0.3, 1e-9), "exibido 0,2 com valor 0,28 vai para 0,3");
        check(near(LevelMath.stepDisplay(0, 0.26, false), 0.3, 1e-9), "sem exibido anterior arredonda");
    }

    private static void testFormatting() {
        check(LevelMath.format1(-0.04).equals("0,0"), "format1(-0.04) deve ser 0,0");
        check(LevelMath.format1(-1.25).equals("−1,3"), "format1(-1.25) deve ser -1,3 com U+2212");
        check(LevelMath.format1(0.4).equals("0,4"), "format1(0.4) deve ser 0,4");
        check(LevelMath.formatSigned1(0.8).equals("+0,8"), "formatSigned1(0.8) deve ser +0,8");
        check(LevelMath.formatSigned1(-1.1).equals("−1,1"), "formatSigned1(-1.1) deve ser -1,1");
        check(LevelMath.formatSigned1(-0.01).equals("0,0"), "formatSigned1 nao deve gerar -0,0");
        check(near(LevelMath.toUnit(45, LevelMath.UNIT_PERCENT), 100, 1e-6), "45 graus valem 100 por cento");
        check(near(LevelMath.toUnit(3, LevelMath.UNIT_DEG), 3, 1e-12), "graus ficam iguais");
    }

    private static void testLevelAndBubble() {
        check(!LevelMath.updateLevel(false, 0.25, 0.2), "0,25 nao entra no nivel com tolerancia 0,2");
        check(LevelMath.updateLevel(true, 0.25, 0.2), "0,25 continua nivelado (histerese)");
        check(!LevelMath.updateLevel(true, 0.35, 0.2), "0,35 sai do nivel com tolerancia 0,2");
        check(LevelMath.updateLevel(false, 0.2, 0.2), "limite exato entra no nivel");

        double[] out = new double[2];
        LevelMath.bubble2D(50, 50, LevelMath.SURFACE_SCALE_DEG, out);
        check(Math.hypot(out[0], out[1]) < 1.0, "bolha deve ficar dentro do frasco");
        check(near(out[0], out[1], 1e-9), "bolha deve manter a direcao");
        LevelMath.bubble2D(0, 0, LevelMath.SURFACE_SCALE_DEG, out);
        check(out[0] == 0 && out[1] == 0, "bolha no centro sem inclinacao");
        LevelMath.bubble2D(1, 0, LevelMath.SURFACE_SCALE_DEG, out);
        check(near(out[0], Math.tanh(1.0 / 8.0), 1e-9), "bolha pequena segue tanh");
        check(near(LevelMath.bubble1D(6, LevelMath.EDGE_SCALE_DEG), Math.tanh(1.0), 1e-9),
                "bubble1D(6, 6) = tanh(1)");
    }
}
