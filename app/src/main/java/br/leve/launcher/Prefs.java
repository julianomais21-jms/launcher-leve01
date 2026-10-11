package br.leve.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Preferências simples (um único arquivo XML minúsculo). */
final class Prefs {
    static final String HIDDEN = "hidden";   // apps ocultos da lista
    static final String PINNED = "pinned";   // apps fixados no começo
    static final String KEEP = "keep";       // apps que a limpeza NUNCA encerra

    private final SharedPreferences sp;

    Prefs(Context c) {
        sp = c.getSharedPreferences("p", Context.MODE_PRIVATE);
    }

    /** Intervalo da limpeza automática em minutos: 0 = desligada, 30 ou 60. */
    int interval() { return sp.getInt("interval", 60); }
    void setInterval(int m) { sp.edit().putInt("interval", m).apply(); }

    /** Modo "1 app por vez": ao abrir um app, encerra os outros em segundo plano. */
    boolean single() { return sp.getBoolean("single", true); }
    void setSingle(boolean b) { sp.edit().putBoolean("single", b).apply(); }

    /** Ao voltar para o início, encerra o app que estava aberto. */
    boolean killOnHome() { return sp.getBoolean("home", false); }
    void setKillOnHome(boolean b) { sp.edit().putBoolean("home", b).apply(); }

    String last() { return sp.getString("last", null); }
    void setLast(String pkg) { sp.edit().putString("last", pkg).apply(); }

    Set<String> set(String key) {
        return new HashSet<String>(sp.getStringSet(key, Collections.<String>emptySet()));
    }

    boolean has(String key, String pkg) {
        return sp.getStringSet(key, Collections.<String>emptySet()).contains(pkg);
    }

    void toggle(String key, String pkg) {
        Set<String> s = set(key);
        if (!s.remove(pkg)) s.add(pkg);
        sp.edit().putStringSet(key, s).apply();
    }

    void clear(String key) { sp.edit().remove(key).apply(); }
}
