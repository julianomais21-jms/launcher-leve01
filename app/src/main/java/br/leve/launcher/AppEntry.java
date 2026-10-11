package br.leve.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Um app instalado que pode ser aberto pelo launcher. */
final class AppEntry {
    final String pkg;
    final String label;
    final ActivityInfo info;
    final Intent intent;

    AppEntry(String pkg, String label, ActivityInfo info, Intent intent) {
        this.pkg = pkg;
        this.label = label;
        this.info = info;
        this.intent = intent;
    }

    /** Lista todos os apps com ícone de launcher (TV ou comum), sem repetir pacotes e sem este app. */
    static List<AppEntry> loadAll(Context c) {
        PackageManager pm = c.getPackageManager();
        LinkedHashMap<String, AppEntry> map = new LinkedHashMap<String, AppEntry>();
        String[] cats = {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER};
        for (String cat : cats) {
            Intent q = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(c.getPackageName()) || map.containsKey(pkg)) continue;
                Intent li = new Intent(Intent.ACTION_MAIN)
                        .addCategory(cat)
                        .setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                map.put(pkg, new AppEntry(pkg, String.valueOf(ri.loadLabel(pm)), ri.activityInfo, li));
            }
        }
        return new ArrayList<AppEntry>(map.values());
    }
}
