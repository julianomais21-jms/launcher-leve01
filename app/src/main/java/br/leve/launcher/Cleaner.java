package br.leve.launcher;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.Set;

/**
 * Pede ao Android para encerrar os processos em segundo plano dos apps.
 * Sem root, é a única forma permitida. Não mexe no app que está na tela
 * nem em apps do sistema, e respeita a lista "manter sempre ativo".
 */
final class Cleaner {
    private Cleaner() {}

    static long avail(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    static long total(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.totalMem;
    }

    /** Encerra o segundo plano de todos os apps, exceto {@code except} e os da lista "manter". */
    static int killAll(Context c, String except) {
        Set<String> keep = new Prefs(c).set(Prefs.KEEP);
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        int n = 0;
        for (AppEntry a : AppEntry.loadAll(c)) {
            if (a.pkg.equals(except) || keep.contains(a.pkg)) continue;
            try {
                am.killBackgroundProcesses(a.pkg);
                n++;
            } catch (Exception ignored) {
            }
        }
        return n;
    }

    static void killOne(Context c, String pkg) {
        if (pkg == null || new Prefs(c).has(Prefs.KEEP, pkg)) return;
        try {
            ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).killBackgroundProcesses(pkg);
        } catch (Exception ignored) {
        }
    }

    /** Limpeza completa em thread separada; mostra quanto de RAM foi liberada. */
    static void cleanNow(final Context c, final boolean showToast, final Runnable done) {
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                long before = avail(app);
                killAll(app, null);
                try { Thread.sleep(900); } catch (InterruptedException ignored) {}
                long after = avail(app);
                final long freed = Math.max(0, after - before) / (1024 * 1024);
                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    @Override public void run() {
                        if (showToast) {
                            Toast.makeText(app, "Memória limpa  (+" + freed + " MB)", Toast.LENGTH_LONG).show();
                        }
                        if (done != null) done.run();
                    }
                });
            }
        }, "limpeza").start();
    }
}
