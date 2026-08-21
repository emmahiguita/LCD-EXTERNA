package com.limelight.ui

import android.app.Activity
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.limelight.R
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * FileBrowserController — Explorador de archivos dentro del overlay.
 *
 * Dos modos (alternables con el botón de cabecera):
 *  • MÓVIL: lista el almacenamiento del teléfono (java.io.File) y permite elegir
 *    varios para ENVIAR al PC (callback onSend).
 *  • PC: lista los archivos del PC vía el companion (HTTP /list) y permite
 *    DESCARGAR un archivo al móvil (HTTP /get → carpeta de recibidos).
 *
 * Todo sin salir de la proyección.
 */
class FileBrowserController(
    private val root: View,
    private val context: Context,
    private val onSend: (List<File>) -> Unit
) {
    private val pathText: TextView = root.findViewById(R.id.fbPath)
    private val listContainer: LinearLayout = root.findViewById(R.id.fbList)
    private val sendButton: Button = root.findViewById(R.id.fbSend)
    private val toggleButton: TextView = root.findViewById(R.id.fbToggle)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    // ── Modo MÓVIL ──────────────────────────────────────────────────────────
    private var currentDir: File = Environment.getExternalStorageDirectory() ?: File("/")
    private val selected = LinkedHashSet<File>()

    // ── Modo PC ─────────────────────────────────────────────────────────────
    private var pcMode = false
    private var companionHost: String? = null
    private var companionPin: String = ""
    private var pcPath = ""   // "" = raíces (unidades + carpeta de usuario)

    private val density = context.resources.displayMetrics.density

    init {
        root.findViewById<View>(R.id.fbClose).setOnClickListener { hide() }
        sendButton.setOnClickListener {
            if (!pcMode && selected.isNotEmpty()) onSend(selected.toList())
            hide()
        }
        toggleButton.setOnClickListener { toggleMode() }
    }

    /** Game llama esto cuando ya conoce el host/PIN del companion. */
    fun setCompanionHost(host: String?, pin: String?) {
        companionHost = host
        companionPin = pin ?: ""
    }

    fun show() {
        selected.clear()
        if (pcMode) {
            pcPath = ""
            renderPc()
        } else {
            currentDir = Environment.getExternalStorageDirectory() ?: File("/")
            render()
        }
        root.visibility = View.VISIBLE
    }

    fun hide() {
        root.visibility = View.GONE
        selected.clear()
        listContainer.removeAllViews()
    }

    fun isShown(): Boolean = root.visibility == View.VISIBLE

    private fun toggleMode() {
        // Solo permitir modo PC si conocemos el host del companion.
        if (!pcMode && companionHost.isNullOrEmpty()) {
            Toast.makeText(context, "Conéctate al PC para ver sus archivos", Toast.LENGTH_SHORT).show()
            return
        }
        pcMode = !pcMode
        selected.clear()
        toggleButton.text = if (pcMode) "💻 PC" else "📱 Móvil"
        sendButton.visibility = if (pcMode) View.GONE else View.VISIBLE
        if (pcMode) { pcPath = ""; renderPc() } else { render() }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MODO MÓVIL (archivos del teléfono)
    // ════════════════════════════════════════════════════════════════════════

    private fun navigateTo(dir: File) {
        if (dir.isDirectory && dir.canRead()) { currentDir = dir; render() }
    }

    private fun render() {
        pathText.text = currentDir.absolutePath
        listContainer.removeAllViews()
        currentDir.parentFile?.let { parent -> addRow("⬆  ..", true) { navigateTo(parent) } }
        val entries = currentDir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
        ) ?: emptyList()
        for (f in entries) {
            if (f.isHidden) continue
            if (f.isDirectory) {
                addRow("📁  ${f.name}", true) { navigateTo(f) }
            } else {
                val row = addRow("📄  ${f.name}", false, null)
                row.setOnClickListener {
                    if (selected.contains(f)) selected.remove(f) else selected.add(f)
                    row.text = (if (selected.contains(f)) "✅  " else "📄  ") + f.name
                    updateSendLabel()
                }
            }
        }
        updateSendLabel()
    }

    private fun updateSendLabel() {
        sendButton.text = if (selected.isEmpty()) "Enviar al PC" else "Enviar al PC (${selected.size})"
    }

    // ════════════════════════════════════════════════════════════════════════
    //  MODO PC (archivos del PC vía companion HTTP)
    // ════════════════════════════════════════════════════════════════════════

    private fun baseUrl(): String = "http://" + companionHost + ":" + FILE_PORT

    private fun tokenSuffix(): String =
        if (companionPin.isNotEmpty()) "&token=" + enc(companionPin) else ""

    private fun renderPc() {
        pathText.text = if (pcPath.isEmpty()) "PC" else pcPath
        listContainer.removeAllViews()
        addRow("⏳  Cargando…", true, null)
        val url = baseUrl() + "/list?path=" + enc(pcPath) + tokenSuffix()
        http.newCall(Request.Builder().url(url).build())
            .enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    mainHandler.post {
                        listContainer.removeAllViews()
                        addRow("⚠  Sin conexión con el PC", true, null)
                    }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val txt = try { response.body?.string() ?: "" } catch (e: Exception) { "" }
                    response.close()
                    mainHandler.post { renderPcListing(txt) }
                }
            })
    }

    private fun renderPcListing(json: String) {
        listContainer.removeAllViews()
        val obj = try { JSONObject(json) } catch (e: Exception) {
            addRow("⚠  Respuesta inválida del PC", true, null); return
        }
        pcPath = obj.optString("path", pcPath)
        pathText.text = if (pcPath.isEmpty()) "PC" else pcPath
        val parent = obj.optString("parent", "")
        if (pcPath.isNotEmpty()) {
            addRow("⬆  ..", true) { pcPath = parent; renderPc() }
        }
        val arr = obj.optJSONArray("entries") ?: return
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val name = e.optString("name")
            val isDir = e.optBoolean("dir", false)
            val full = e.optString("path")
            if (isDir) {
                addRow("📁  $name", true) { pcPath = full; renderPc() }
            } else {
                addRow("📥  $name", false) { downloadFromPc(full, name) }
            }
        }
    }

    private fun downloadFromPc(remotePath: String, name: String) {
        Toast.makeText(context, "Descargando $name…", Toast.LENGTH_SHORT).show()
        val url = baseUrl() + "/get?path=" + enc(remotePath) + tokenSuffix()
        http.newCall(Request.Builder().url(url).build())
            .enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    mainHandler.post { Toast.makeText(context, "Falló la descarga", Toast.LENGTH_SHORT).show() }
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    var ok = false
                    try {
                        if (response.isSuccessful) {
                            val dir = receivedDir()
                            val dest = uniqueFile(dir, name)
                            response.body?.byteStream()?.use { input ->
                                dest.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
                            }
                            ok = true
                        }
                    } catch (e: Exception) {
                        ok = false
                    } finally {
                        response.close()
                    }
                    val msg = if (ok) "Descargado en SmartDisplay: $name" else "Falló la descarga"
                    mainHandler.post { Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
                }
            })
    }

    private fun receivedDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(base, "SmartDisplay")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (f.exists()) { f = File(dir, "$base($i)$ext"); i++ }
        return f
    }

    // ════════════════════════════════════════════════════════════════════════

    private fun addRow(label: String, isDir: Boolean, onClick: (() -> Unit)?): TextView {
        val tv = TextView(context)
        tv.text = label
        tv.setTextColor(if (isDir) 0xFF22D3EE.toInt() else 0xFFE6F2FF.toInt())
        tv.textSize = 14f
        tv.setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
        tv.isClickable = true
        tv.setBackgroundResource(android.R.drawable.list_selector_background)
        if (onClick != null) tv.setOnClickListener { onClick() }
        listContainer.addView(tv)
        return tv
    }

    private fun enc(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    companion object {
        private const val FILE_PORT = 8766  // = FILE_PORT en companion_server.py
    }
}
