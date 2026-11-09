package br.com.litecenter

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import java.io.File

class MainActivity : Activity() {
    private class Row(val label: String, val addonId: String? = null, val action: () -> Unit)

    private lateinit var ui: Screen
    private var rows = listOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Py.start(this)
        ui = screen("LiteCenter")
        setContentView(ui.root)
        ui.list.setOnItemClickListener { _, _, pos, _ -> rows[pos].action() }
        ui.list.setOnItemLongClickListener { _, _, pos, _ ->
            rows[pos].addonId?.let { confirmRemove(it) }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        bg({ Py.installed(this) }, { arr ->
            val list = mutableListOf<Row>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getString("id")
                val name = o.optString("name", id)
                list += Row(name, id) {
                    startActivity(
                        Intent(this, BrowseActivity::class.java)
                            .putExtra("url", "plugin://$id/").putExtra("title", name)
                    )
                }
            }
            list += Row("+ Instalar addon/repositório por URL (.zip)") { installFromUrl() }
            list += Row("+ Instalar addon/repositório de arquivo (.zip)") { installFromFile() }
            list += Row("+ Procurar addons nos repositórios instalados") { browseRepos() }
            rows = list
            ui.list.adapter = rowAdapter(list.map { it.label })
            ui.status.text = if (arr.length() == 0)
                "Nenhum addon de vídeo instalado. Comece instalando o .zip de um repositório."
            else "OK abre o addon. Segure OK para remover."
            ui.list.requestFocus()
        })
    }

    private fun installFromUrl() = prompt("URL do .zip", "https://", onOk = { url ->
        if (url.startsWith("http")) runInstall { Py.installZip(this, url) }
    })

    private fun installFromFile() {
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
            }, 1)
        } catch (e: Exception) {
            toast("Este aparelho não tem seletor de arquivos. Use a opção por URL.")
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 1 && resultCode == RESULT_OK && uri != null) {
            runInstall {
                val f = File(cacheDir, "install.zip")
                contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                Py.installZip(this, f.absolutePath)
            }
        }
    }

    private fun browseRepos() {
        val t = Ticker(ui.status, "Consultando repositórios (a 1ª vez é mais lenta)").start()
        bg({ Py.available(this) }, { r ->
            t.stop()
            val arr = r.getJSONArray("items")
            val errs = r.optJSONArray("errors")
            val erroTxt = if (errs == null) "" else (0 until errs.length()).joinToString("\n") { errs.getString(it) }
            if (arr.length() == 0) {
                ui.status.text = if (erroTxt.isNotEmpty()) "Falha ao consultar:\n" + erroTxt.take(400)
                else "Nenhum addon de vídeo encontrado nos repositórios instalados."
                return@bg
            }
            ui.status.text = if (erroTxt.isNotEmpty()) "Alguns repositórios falharam:\n" + erroTxt.take(300)
            else "${arr.length()} addons de vídeo (fonte: ${r.optString("source")})"
            val ids = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
            val names = (0 until arr.length()).map {
                val o = arr.getJSONObject(it); "${o.getString("name")}  (${o.optString("version")})"
            }
            AlertDialog.Builder(this).setTitle("Instalar addon")
                .setItems(names.toTypedArray()) { _, which -> runInstall { Py.install(this, ids[which]) } }
                .show()
        }, { e ->
            t.stop()
            ui.status.text = "Erro: " + (e.message ?: "falha ao consultar")
        })
    }

    private fun runInstall(work: () -> JSONObject) {
        val t = Ticker(ui.status, "Instalando").start()
        bg(work, { r -> t.stop(); toast(describe(r)); refresh() },
            { e -> t.stop(); ui.status.text = "Erro: " + (e.message ?: "falha na instalação") })
    }

    private fun describe(r: JSONObject): String {
        if (r.optString("error").isNotEmpty()) return "Erro: " + r.getString("error")
        val sb = StringBuilder("Instalado: ").append(r.optString("id"))
        val inst = r.optJSONArray("installed")
        if (inst != null && inst.length() > 1) sb.append(" (+${inst.length() - 1} dependências)")
        val miss = r.optJSONArray("missing")
        if (miss != null && miss.length() > 0)
            sb.append("\nDependências não encontradas: ")
                .append((0 until miss.length()).joinToString { miss.getString(it) })
        return sb.toString()
    }

    private fun confirmRemove(id: String) {
        AlertDialog.Builder(this).setTitle("Remover $id?")
            .setPositiveButton("Remover") { _, _ -> bg({ Py.uninstall(this, id) }, { refresh() }) }
            .setNegativeButton("Cancelar", null).show()
    }
}
