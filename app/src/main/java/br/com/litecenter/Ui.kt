package br.com.litecenter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

fun Activity.toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

/** Roda [work] numa thread com pilha grande (Python) e devolve o resultado na UI. */
fun <T> Activity.bg(
    work: () -> T,
    done: (T) -> Unit,
    fail: (Throwable) -> Unit = { toast(it.message ?: "Erro") }
) {
    Thread(null, Runnable {
        try {
            val r = work()
            runOnUiThread { if (!isFinishing) done(r) }
        } catch (t: Throwable) {
            runOnUiThread { if (!isFinishing) fail(t) }
        }
    }, "py-worker", 8L * 1024 * 1024).start()
}

fun Activity.prompt(
    title: String,
    initial: String = "",
    onOk: (String) -> Unit,
    onCancel: () -> Unit = {}
) {
    val et = EditText(this).apply { setSingleLine(); setText(initial) }
    AlertDialog.Builder(this)
        .setTitle(title)
        .setView(et)
        .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
        .setNegativeButton("Cancelar") { _, _ -> onCancel() }
        .show()
}

fun Activity.rowAdapter(data: List<String>) =
    object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, data) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent) as TextView
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            v.setPadding(context.dp(24), context.dp(14), context.dp(24), context.dp(14))
            return v
        }
    }

class Screen(val root: LinearLayout, val status: TextView, val list: ListView)

fun Activity.screen(title: String): Screen {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(40), dp(24), dp(40), dp(16))
    }
    root.addView(TextView(this).apply {
        text = title
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
        setPadding(0, 0, 0, dp(8))
    })
    val status = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(0, 0, 0, dp(8))
    }
    root.addView(status)
    val list = ListView(this).apply { itemsCanFocus = false }
    root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    return Screen(root, status, list)
}

/** Mostra "texto… 12s" na tela para o usuario saber que o app nao travou. */
class Ticker(private val tv: TextView, private val base: String) {
    private val h = Handler(Looper.getMainLooper())
    private var s = 0
    private var on = false
    private val r = object : Runnable {
        override fun run() {
            if (!on) return
            tv.text = "$base… ${s}s"
            s++
            h.postDelayed(this, 1000)
        }
    }
    fun start(): Ticker { on = true; s = 0; h.post(r); return this }
    fun stop() { on = false; h.removeCallbacks(r) }
}
