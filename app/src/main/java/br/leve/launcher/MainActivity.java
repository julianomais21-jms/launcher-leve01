package br.leve.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Tela inicial: relógio, memória livre e grade de apps. Tudo em views do framework (sem bibliotecas). */
public class MainActivity extends Activity {

    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFF9AA0AA;
    private static final int C_CARD = 0xFF1C2028;
    private static final int C_PILL = 0xFF232730;

    private Prefs prefs;
    private GridView grid;
    private TextView status;
    private final List<AppEntry> apps = new ArrayList<AppEntry>();
    private AppAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private float dp;

    private int dp(float v) { return Math.round(v * dp); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        prefs = new Prefs(this);
        setContentView(buildUi());
        CleanJob.schedule(this);
    }

    /* ------------------------------------------------------------ UI */

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF161920, 0xFF0A0B0E}));
        root.setPadding(dp(48), dp(26), dp(48), 0);

        // Cabeçalho: relógio à esquerda; memória e botões à direita
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("HH:mm");
        time.setTextSize(44);
        time.setTextColor(C_TEXT);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(15);
        date.setTextColor(C_MUTED);
        left.addView(time);
        left.addView(date);
        head.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        status = new TextView(this);
        status.setTextSize(14);
        status.setTextColor(C_MUTED);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.rightMargin = dp(20);
        head.addView(status, sp);

        final TextView clean = pill("Limpar memória");
        clean.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Limpando…", Toast.LENGTH_SHORT).show();
                Cleaner.cleanNow(MainActivity.this, true, new Runnable() {
                    @Override public void run() { updateStatus(); }
                });
            }
        });
        final TextView cfg = pill("Ajustes");
        cfg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSettings(); }
        });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(10);
        head.addView(clean, pp);
        head.addView(cfg, pp);
        root.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText("Apps");
        label.setTextSize(18);
        label.setTextColor(C_TEXT);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(26);
        lp.bottomMargin = dp(4);
        lp.leftMargin = dp(8);
        root.addView(label, lp);

        grid = new GridView(this);
        grid.setNumColumns(GridView.AUTO_FIT);
        grid.setColumnWidth(dp(232));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(12));
        grid.setHorizontalSpacing(dp(6));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(dp(8), dp(14), dp(8), dp(60));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setDrawSelectorOnTop(false);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                launch(apps.get(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                appMenu(apps.get(pos));
                return true;
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            private View prev;
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (prev != null && prev != v) zoom(prev, false);
                if (v != null && grid.hasFocus()) zoom(v, true);
                prev = v;
            }
            @Override public void onNothingSelected(AdapterView<?> p) {
                if (prev != null) zoom(prev, false);
                prev = null;
            }
        });
        grid.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                View sel = grid.getSelectedView();
                if (sel != null) zoom(sel, has);
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void zoom(View v, boolean on) {
        v.animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f)
                .translationZ(on ? dp(10) : 0).setDuration(110).start();
    }

    /** Botão "pílula" estilo Google TV: cinza escuro; branco quando focado. */
    private TextView pill(String text) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(C_TEXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(20), dp(9), dp(20), dp(9));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(round(C_PILL, 100));
        t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                t.setBackground(round(has ? 0xFFFFFFFF : C_PILL, 100));
                t.setTextColor(has ? 0xFF111111 : C_TEXT);
            }
        });
        return t;
    }

    private Drawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    /* ------------------------------------------------------------ ciclo de vida */

    @Override
    protected void onResume() {
        super.onResume();
        refreshApps();
        updateStatus();
        if (!grid.hasFocus() && !anyPillFocused()) grid.requestFocus();
        final String last = prefs.last();
        if (last != null && prefs.killOnHome()) {
            prefs.setLast(null);
            // espera o app anterior sair da tela antes de pedir o encerramento
            handler.postDelayed(new Runnable() {
                @Override public void run() { Cleaner.killOne(MainActivity.this, last); updateStatus(); }
            }, 1800);
        }
    }

    private boolean anyPillFocused() {
        View f = getCurrentFocus();
        return f != null && f != grid && f instanceof TextView;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // botão HOME pressionado estando aqui: volta ao começo
        if (grid != null) {
            grid.setSelection(0);
            grid.requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // launcher não "fecha": Voltar não faz nada
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettings();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* ------------------------------------------------------------ dados */

    private void refreshApps() {
        List<AppEntry> all = AppEntry.loadAll(this);
        final Set<String> hidden = prefs.set(Prefs.HIDDEN);
        final Set<String> pinned = prefs.set(Prefs.PINNED);
        List<AppEntry> list = new ArrayList<AppEntry>();
        for (AppEntry a : all) if (!hidden.contains(a.pkg)) list.add(a);
        Collections.sort(list, new Comparator<AppEntry>() {
            @Override public int compare(AppEntry x, AppEntry y) {
                boolean px = pinned.contains(x.pkg), py = pinned.contains(y.pkg);
                if (px != py) return px ? -1 : 1;
                return x.label.compareToIgnoreCase(y.label);
            }
        });
        apps.clear();
        apps.addAll(list);
        adapter.notifyDataSetChanged();
    }

    private void updateStatus() {
        long free = Cleaner.avail(this) / (1024 * 1024);
        long total = Cleaner.total(this) / (1024 * 1024);
        String mode = prefs.single() ? "1 app por vez" : "modo livre";
        int m = prefs.interval();
        String auto = m == 0 ? "limpeza manual" : (m == 30 ? "limpa a cada 30 min" : "limpa a cada 1 h");
        status.setText("RAM livre " + free + " de " + total + " MB  ·  " + mode + "  ·  " + auto);
    }

    /* ------------------------------------------------------------ ações */

    private void launch(AppEntry a) {
        if (prefs.single()) {
            final String keepPkg = a.pkg;
            new Thread(new Runnable() {
                @Override public void run() { Cleaner.killAll(getApplicationContext(), keepPkg); }
            }, "limpa-antes").start();
        }
        prefs.setLast(a.pkg);
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível abrir " + a.label, Toast.LENGTH_SHORT).show();
        }
    }

    private AlertDialog.Builder dialog() {
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
    }

    private void appMenu(final AppEntry a) {
        final boolean pinned = prefs.has(Prefs.PINNED, a.pkg);
        final boolean keep = prefs.has(Prefs.KEEP, a.pkg);
        String[] items = {
                pinned ? "Desafixar do começo" : "Fixar no começo",
                keep ? "Voltar a encerrar este app na limpeza" : "Nunca encerrar este app na limpeza",
                "Ocultar da lista",
                "Informações do app (forçar parada / desinstalar)"
        };
        dialog().setTitle(a.label).setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                if (which == 0) { prefs.toggle(Prefs.PINNED, a.pkg); refreshApps(); }
                else if (which == 1) { prefs.toggle(Prefs.KEEP, a.pkg); refreshApps(); }
                else if (which == 2) { prefs.toggle(Prefs.HIDDEN, a.pkg); refreshApps(); }
                else {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + a.pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Tela de informações indisponível", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }).show();
    }

    private void showSettings() {
        int m = prefs.interval();
        String auto = m == 0 ? "desligada" : (m == 30 ? "a cada 30 minutos" : "a cada 1 hora");
        int hiddenCount = prefs.set(Prefs.HIDDEN).size();
        String[] items = {
                "Limpeza automática: " + auto,
                "Modo 1 app por vez: " + (prefs.single() ? "ligado" : "desligado"),
                "Fechar app ao voltar ao início: " + (prefs.killOnHome() ? "ligado" : "desligado"),
                "Mostrar apps ocultos (" + hiddenCount + ")",
                "Tornar este o início padrão",
                "Ajuda"
        };
        dialog().setTitle("Ajustes").setItems(items, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface d, int which) {
                switch (which) {
                    case 0: {
                        int cur = prefs.interval();
                        prefs.setInterval(cur == 0 ? 30 : (cur == 30 ? 60 : 0));
                        CleanJob.schedule(MainActivity.this);
                        updateStatus();
                        showSettings();
                        break;
                    }
                    case 1:
                        prefs.setSingle(!prefs.single());
                        updateStatus();
                        showSettings();
                        break;
                    case 2:
                        prefs.setKillOnHome(!prefs.killOnHome());
                        showSettings();
                        break;
                    case 3:
                        prefs.clear(Prefs.HIDDEN);
                        refreshApps();
                        showSettings();
                        break;
                    case 4:
                        try {
                            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                        } catch (ActivityNotFoundException e) {
                            showHelp();
                        }
                        break;
                    default:
                        showHelp();
                }
            }
        }).show();
    }

    private void showHelp() {
        dialog().setTitle("Como funciona")
                .setMessage("• Abrir um app encerra os outros que estiverem em segundo plano (modo 1 app por vez).\n\n"
                        + "• A limpeza automática pede ao Android para encerrar apps em segundo plano. "
                        + "O app que está na tela não é afetado.\n\n"
                        + "• Segure OK sobre um app para fixar, ocultar, impedir que seja encerrado "
                        + "ou abrir as informações dele.\n\n"
                        + "• Apps com serviço ativo (música tocando, por exemplo) podem resistir; "
                        + "nesse caso use \"Informações do app → Forçar parada\".")
                .setPositiveButton("OK", null)
                .show();
    }

    /* ------------------------------------------------------------ adapter */

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return apps.size(); }
        @Override public Object getItem(int i) { return apps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convert, ViewGroup parent) {
            Holder h;
            if (convert == null) {
                LinearLayout item = new LinearLayout(MainActivity.this);
                item.setOrientation(LinearLayout.VERTICAL);
                item.setLayoutParams(new AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(160)));

                FrameLayout card = new FrameLayout(MainActivity.this);
                card.setBackground(round(C_CARD, 12));
                card.setForeground(focusRing());
                card.setClipToOutline(true);
                card.setOutlineProvider(new ViewOutlineProvider() {
                    @Override public void getOutline(View v, Outline o) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(12));
                    }
                });
                h = new Holder();
                h.img = new ImageView(MainActivity.this);
                card.addView(h.img, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                item.addView(card, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(116)));

                h.text = new TextView(MainActivity.this);
                h.text.setTextSize(13);
                h.text.setSingleLine(true);
                h.text.setEllipsize(android.text.TextUtils.TruncateAt.END);
                h.text.setGravity(Gravity.CENTER_HORIZONTAL);
                h.text.setTextColor(new ColorStateList(
                        new int[][]{{android.R.attr.state_selected}, {}},
                        new int[]{C_TEXT, C_MUTED}));
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tp.topMargin = dp(8);
                item.addView(h.text, tp);
                item.setTag(h);
                convert = item;
            } else {
                h = (Holder) convert.getTag();
            }

            AppEntry a = apps.get(pos);
            Drawable banner = a.info.loadBanner(getPackageManager());
            if (banner != null) {
                h.img.setPadding(0, 0, 0, 0);
                h.img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                h.img.setImageDrawable(banner);
            } else {
                int pad = dp(26);
                h.img.setPadding(pad, pad, pad, pad);
                h.img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                h.img.setImageDrawable(a.info.loadIcon(getPackageManager()));
            }
            String t = a.label;
            if (prefs.has(Prefs.PINNED, a.pkg)) t = "★ " + t;
            if (prefs.has(Prefs.KEEP, a.pkg)) t = t + " ∞";
            h.text.setText(t);

            // views recicladas não podem manter o zoom antigo
            boolean sel = grid != null && grid.hasFocus() && pos == grid.getSelectedItemPosition();
            convert.setScaleX(sel ? 1.08f : 1f);
            convert.setScaleY(sel ? 1.08f : 1f);
            convert.setTranslationZ(sel ? dp(10) : 0);
            return convert;
        }
    }

    private Drawable focusRing() {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(3), 0xFFFFFFFF);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_selected}, ring);
        sl.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        return sl;
    }

    private static final class Holder {
        ImageView img;
        TextView text;
    }
}
