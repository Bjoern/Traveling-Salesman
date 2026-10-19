import java.io.File;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;

/**
 * Runs the original and the fixed Environment (loaded from separate class directories) plus two
 * reference searches on the same random city layouts with the same number of offspring evaluations.
 * Reports the best tour found as % above the optimum (exact for n <= 13, otherwise best known from
 * many 2-opt/or-opt restarts).
 */
public class Bench {
    static final int MAP = 200, POP = 500;

    public static void main(String[] a) throws Exception {
        String origDir = a[0], fixedDir = a[1];
        int[] sizes = {10, 30, 60};
        int seeds = Integer.parseInt(a[2]);
        long budget = Long.parseLong(a[3]);

        ClassLoader orig = new URLClassLoader(new URL[]{new File(origDir).toURI().toURL()}, null);
        ClassLoader fixed = new URLClassLoader(new URL[]{new File(fixedDir).toURI().toURL()}, null);

        String[] names = {"original code", "fixed, with crossover", "fixed, mutation only",
                          "order crossover + swap (reference)", "random tours"};
        System.out.printf("budget %,d offspring per run, %d layouts per size, population %d%n", budget, seeds, POP);
        System.out.println("mean % above optimum / best known (lower is better); [best..worst]");
        for (int n : sizes) {
            double[][] gap = new double[names.length][seeds];
            for (int s = 0; s < seeds; s++) {
                int[][] xy = coords(n, new Random(1000 + s));
                double[][] d = dist(xy);
                double ref = n <= 13 ? heldKarp(d) : bestKnown(d, new Random(s));
                double[] got = {
                    runEnv(orig, xy, budget, 1.0, 77 + s),
                    runEnv(fixed, xy, budget, 1.0, 77 + s),
                    runEnv(fixed, xy, budget, 0.0, 77 + s),
                    oxGA(d, budget, new Random(77 + s)),
                    randomTours(d, budget, new Random(77 + s)),
                };
                for (int v = 0; v < names.length; v++) gap[v][s] = 100 * (got[v] / ref - 1);
            }
            System.out.printf("%nn = %d%s%n", n, n <= 13 ? " (exact optimum)" : " (best known)");
            for (int v = 0; v < names.length; v++) {
                double[] g = gap[v].clone(); Arrays.sort(g);
                System.out.printf("  %-36s %6.1f%%   [%.1f .. %.1f]%n", names[v],
                        Arrays.stream(g).average().getAsDouble(), g[0], g[g.length - 1]);
            }
        }
    }

    // ---- the repo's GA, driven through its own (private) step methods ----
    static double runEnv(ClassLoader cl, int[][] xy, long budget, double crossoverRate, long seed) throws Exception {
        Class<?> envC = cl.loadClass("net.blinker.ga.Environment");
        Class<?> tsC = cl.loadClass("net.blinker.tsm.TravelingSalesman");
        envC.getField("random").set(null, new Random(seed));
        Object ts = tsC.getConstructor(int[][].class).newInstance((Object) xy);
        Object env = envC.getConstructor(tsC).newInstance(ts);
        try { envC.getField("crossoverRate").set(env, crossoverRate); }
        catch (NoSuchFieldException e) { if (crossoverRate != 1.0) throw e; }
        Method init = envC.getDeclaredMethod("calculateFitnesses"); init.setAccessible(true);
        Method step = envC.getDeclaredMethod("createNewOffspring"); step.setAccessible(true);
        Field min = envC.getDeclaredField("minCost"); min.setAccessible(true);
        init.invoke(env);
        for (long i = 0; i < budget; i++) step.invoke(env);
        return min.getDouble(env);
    }

    // ---- reference: same steady-state GA (tournament 3, replace worst), permutation genome ----
    static double oxGA(double[][] d, long budget, Random r) {
        int n = d.length;
        int[][] pop = new int[POP][]; double[] cost = new double[POP];
        for (int i = 0; i < POP; i++) { pop[i] = randomPerm(n, r); cost[i] = len(pop[i], d); }
        double best = Arrays.stream(cost).min().getAsDouble();
        for (long it = 0; it < budget; it++) {
            int p1 = tour(cost, r), p2 = tour(cost, r);
            int[] c = ox(pop[p1], pop[p2], r);
            for (int k = 0; k < n; k++) if (r.nextInt(n) == 0) { // about one swap per child
                int x = r.nextInt(n), y = r.nextInt(n); int t = c[x]; c[x] = c[y]; c[y] = t; }
            int w = -1;
            for (int i = 0; i < POP; i++) if (i != p1 && i != p2 && (w < 0 || cost[i] > cost[w])) w = i;
            pop[w] = c; cost[w] = len(c, d); best = Math.min(best, cost[w]);
        }
        return best;
    }
    static int tour(double[] cost, Random r) {
        int b = r.nextInt(cost.length);
        for (int i = 1; i < 3; i++) { int c = r.nextInt(cost.length); if (cost[c] < cost[b]) b = c; }
        return b;
    }
    static int[] ox(int[] p1, int[] p2, Random r) {
        int n = p1.length, a = r.nextInt(n), b = r.nextInt(n);
        if (a > b) { int t = a; a = b; b = t; }
        int[] c = new int[n]; boolean[] used = new boolean[n];
        for (int i = a; i < b; i++) { c[i] = p1[i]; used[p1[i]] = true; }
        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i == a) i = b; if (i >= n) break;
            while (used[p2[j]]) j++;
            c[i] = p2[j++];
        }
        return c;
    }

    static double randomTours(double[][] d, long budget, Random r) {
        double best = Double.MAX_VALUE;
        for (long i = 0; i < budget + POP; i++) best = Math.min(best, len(randomPerm(d.length, r), d));
        return best;
    }

    // ---- reference optimum ----
    static double heldKarp(double[][] d) {
        int n = d.length, F = 1 << (n - 1);
        double[][] dp = new double[F][n];
        for (double[] row : dp) Arrays.fill(row, Double.MAX_VALUE);
        for (int j = 1; j < n; j++) dp[1 << (j - 1)][j] = d[0][j];
        for (int m = 1; m < F; m++) for (int j = 1; j < n; j++) {
            if ((m & 1 << (j - 1)) == 0 || dp[m][j] == Double.MAX_VALUE) continue;
            for (int k = 1; k < n; k++) if ((m & 1 << (k - 1)) == 0) {
                int m2 = m | 1 << (k - 1);
                dp[m2][k] = Math.min(dp[m2][k], dp[m][j] + d[j][k]);
            }
        }
        double best = Double.MAX_VALUE;
        for (int j = 1; j < n; j++) best = Math.min(best, dp[F - 1][j] + d[j][0]);
        return best;
    }
    static double bestKnown(double[][] d, Random r) {
        double best = Double.MAX_VALUE;
        for (int rep = 0; rep < 300; rep++) {
            int[] t = randomPerm(d.length, r);
            boolean improved = true;
            while (improved) {
                improved = false;
                int n = t.length;
                for (int i = 0; i < n - 1; i++) for (int j = i + 2; j < n; j++) {     // 2-opt
                    int a = t[i], b = t[i + 1], c = t[j], e = t[(j + 1) % n];
                    if (a == e) continue;
                    if (d[a][c] + d[b][e] < d[a][b] + d[c][e] - 1e-9) { reverse(t, i + 1, j); improved = true; }
                }
                for (int len = 1; len <= 3 && !improved; len++)                      // or-opt
                    for (int i = 0; i + len <= n && !improved; i++) {
                        double before = len(t, d);
                        int[] seg = Arrays.copyOfRange(t, i, i + len);
                        int[] rest = new int[n - len]; int k = 0;
                        for (int x = 0; x < n; x++) if (x < i || x >= i + len) rest[k++] = t[x];
                        for (int p = 0; p <= rest.length && !improved; p++) {
                            int[] cand = new int[n]; int q = 0;
                            for (int x = 0; x < p; x++) cand[q++] = rest[x];
                            for (int x : seg) cand[q++] = x;
                            for (int x = p; x < rest.length; x++) cand[q++] = rest[x];
                            if (len(cand, d) < before - 1e-9) { t = cand; improved = true; }
                        }
                    }
            }
            best = Math.min(best, len(t, d));
        }
        return best;
    }
    static void reverse(int[] t, int i, int j) { while (i < j) { int x = t[i]; t[i++] = t[j]; t[j--] = x; } }

    // ---- helpers (coordinates generated like TravelingSalesman.createRandomCoordinates) ----
    static int[][] coords(int n, Random r) {
        int[][] c = new int[n][2];
        for (int[] p : c) { p[0] = Math.abs(r.nextInt() % MAP); p[1] = Math.abs(r.nextInt() % MAP); }
        return c;
    }
    static double[][] dist(int[][] xy) {
        int n = xy.length; double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++)
            d[i][j] = Math.hypot(xy[i][0] - xy[j][0], xy[i][1] - xy[j][1]);
        return d;
    }
    static int[] randomPerm(int n, Random r) {
        int[] p = new int[n]; for (int i = 0; i < n; i++) p[i] = i;
        for (int i = n - 1; i > 0; i--) { int j = r.nextInt(i + 1); int t = p[i]; p[i] = p[j]; p[j] = t; }
        return p;
    }
    static double len(int[] t, double[][] d) {
        double s = 0; for (int i = 0; i < t.length; i++) s += d[t[i]][t[(i + 1) % t.length]]; return s;
    }
}
