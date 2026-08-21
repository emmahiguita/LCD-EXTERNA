package com.limelight.smartdisplay.files;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.text.format.Formatter;

import com.limelight.LimeLog;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SmartDisplay AI – Files Layer: transferencia de archivos por WiFi.
 *
 * El móvil levanta un servidor HTTP minimalista (sin dependencias) en la red
 * local. Desde el navegador del laptop (http://IP-del-movil:PUERTO) se puede:
 *   - Móvil → Laptop: descargar los archivos que el usuario compartió.
 *   - Laptop → Móvil: subir archivos (multipart/form-data) que se guardan en el
 *     almacenamiento del móvil.
 *
 * No requiere software en el laptop (solo un navegador) ni toca el core de
 * streaming. Pensado para red local de confianza.
 */
public class FileTransferServer {

    public static final int DEFAULT_PORT = 8080;
    private static final String TAG = "SD_FileTransfer";

    public interface Listener {
        void onStateChanged(boolean running);
        void onFileReceived(File file);
    }

    /** Archivo del móvil ofrecido para descarga (elegido vía SAF). */
    public static class SharedItem {
        public final Uri uri;
        public final String name;
        public final long size;
        public SharedItem(Uri uri, String name, long size) {
            this.uri = uri; this.name = name; this.size = size;
        }
    }

    private final Context ctx;
    private final int port;
    private final List<SharedItem> shared = new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Listener listener;

    public FileTransferServer(Context context, int port) {
        this.ctx = context.getApplicationContext();
        this.port = port;
    }

    public void setListener(Listener l) { this.listener = l; }
    public boolean isRunning() { return running; }
    public int getPort() { return port; }

    public void addShared(SharedItem item) { shared.add(item); }
    public List<SharedItem> getShared() { return shared; }

    /** Carpeta donde se guardan los archivos recibidos del laptop. */
    public File getReceivedDir() {
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SmartDisplay");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public synchronized void start() {
        if (running) return;
        try {
            serverSocket = new ServerSocket(port);
            running = true;
        } catch (Exception e) {
            LimeLog.warning(TAG + ": no se pudo abrir el puerto " + port + ": " + e.getMessage());
            running = false;
            notifyState();
            return;
        }
        acceptThread = new Thread(this::acceptLoop, "SD-FileServer");
        acceptThread.setDaemon(true);
        acceptThread.start();
        LimeLog.info(TAG + ": servidor iniciado en puerto " + port);
        notifyState();
    }

    public synchronized void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        serverSocket = null;
        notifyState();
    }

    private void notifyState() {
        if (listener != null) listener.onStateChanged(running);
    }

    private void acceptLoop() {
        while (running) {
            Socket socket = null;
            try {
                socket = serverSocket.accept();
                handle(socket);
            } catch (Exception e) {
                if (running) LimeLog.info(TAG + ": conexión cerrada (" + e.getMessage() + ")");
            } finally {
                if (socket != null) try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ── Manejo de petición HTTP ────────────────────────────────────────────────

    private void handle(Socket socket) throws Exception {
        InputStream in = socket.getInputStream();
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());

        // Leer la línea de petición + cabeceras (ASCII, terminadas en \r\n\r\n)
        ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
        int prev = -1, prev2 = -1, prev3 = -1, b;
        while ((b = in.read()) != -1) {
            headerBuf.write(b);
            if (prev3 == '\r' && prev2 == '\n' && prev == '\r' && b == '\n') break;
            prev3 = prev2; prev2 = prev; prev = b;
            if (headerBuf.size() > 32 * 1024) break; // protección
        }
        String headerText = new String(headerBuf.toByteArray(), StandardCharsets.ISO_8859_1);
        String[] lines = headerText.split("\r\n");
        if (lines.length == 0 || lines[0].isEmpty()) return;

        String[] reqLine = lines[0].split(" ");
        if (reqLine.length < 2) return;
        String method = reqLine[0];
        String path = reqLine[1];

        int contentLength = 0;
        String contentType = "";
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int c = line.indexOf(':');
            if (c <= 0) continue;
            String key = line.substring(0, c).trim().toLowerCase();
            String val = line.substring(c + 1).trim();
            if (key.equals("content-length")) {
                try { contentLength = Integer.parseInt(val); } catch (NumberFormatException ignored) {}
            } else if (key.equals("content-type")) {
                contentType = val;
            }
        }

        if (method.equals("GET") && (path.equals("/") || path.startsWith("/?"))) {
            sendHtml(out, buildIndexHtml());
        } else if (method.equals("GET") && path.startsWith("/dl")) {
            serveDownload(out, path);
        } else if (method.equals("POST") && path.startsWith("/up")) {
            receiveUpload(in, out, contentType, contentLength);
        } else {
            sendText(out, "404 Not Found", "404", "text/plain");
        }
        out.flush();
    }

    // ── Móvil → Laptop: servir un archivo compartido ───────────────────────────

    private void serveDownload(OutputStream out, String path) throws Exception {
        int idx = -1;
        int q = path.indexOf("i=");
        if (q >= 0) {
            String num = path.substring(q + 2);
            int amp = num.indexOf('&');
            if (amp >= 0) num = num.substring(0, amp);
            try { idx = Integer.parseInt(num); } catch (NumberFormatException ignored) {}
        }
        if (idx < 0 || idx >= shared.size()) {
            sendText(out, "404 Not Found", "Archivo no disponible", "text/plain");
            return;
        }
        SharedItem item = shared.get(idx);
        InputStream is = ctx.getContentResolver().openInputStream(item.uri);
        if (is == null) {
            sendText(out, "500 Internal Server Error", "No se pudo abrir", "text/plain");
            return;
        }
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + (item.size > 0 ? "Content-Length: " + item.size + "\r\n" : "")
                + "Content-Disposition: attachment; filename=\"" + sanitize(item.name) + "\"\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        is.close();
    }

    // ── Laptop → Móvil: recibir subida multipart ───────────────────────────────

    private void receiveUpload(InputStream in, OutputStream out, String contentType, int contentLength)
            throws Exception {
        String boundary = null;
        int bi = contentType.toLowerCase().indexOf("boundary=");
        if (bi >= 0) {
            boundary = contentType.substring(bi + 9).trim();
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
        }
        if (boundary == null) {
            sendText(out, "400 Bad Request", "Falta boundary", "text/plain");
            return;
        }

        // Streaming: el cuerpo se procesa por bloques y se vuelca a disco sin
        // retenerlo completo en RAM, así una subida de cientos de MB no provoca OOM.
        byte[] dashBoundary = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        MultipartReader reader = new MultipartReader(in, contentLength, dashBoundary);

        int received = 0;
        if (reader.skipToFirstBoundary()) {
            while (!reader.atClosingBoundary()) {
                String partHeaders = reader.readPartHeaders();
                if (partHeaders == null) break;
                String filename = extractFilename(partHeaders);
                if (filename != null && !filename.isEmpty()) {
                    File dest = uniqueFile(getReceivedDir(), sanitize(filename));
                    BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(dest));
                    long written;
                    try {
                        written = reader.streamContentTo(fos);
                    } finally {
                        fos.close();
                    }
                    received++;
                    LimeLog.info(TAG + ": recibido " + dest.getName() + " (" + written + " bytes)");
                    if (listener != null) listener.onFileReceived(dest);
                } else {
                    // Campo de formulario sin archivo: descartar su contenido.
                    reader.streamContentTo(null);
                }
            }
        }

        String msg = received > 0
                ? "<h2>OK</h2><p>" + received + " archivo(s) recibido(s) en el movil.</p><p><a href=\"/\">Volver</a></p>"
                : "<h2>Sin archivos</h2><p><a href=\"/\">Volver</a></p>";
        sendHtml(out, htmlPage(msg));
    }

    // ── Utilidades HTTP ─────────────────────────────────────────────────────────

    private void sendHtml(OutputStream out, String html) throws Exception {
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html; charset=UTF-8\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private void sendText(OutputStream out, String status, String text, String type) throws Exception {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + status + "\r\n"
                + "Content-Type: " + type + "; charset=UTF-8\r\n"
                + "Content-Length: " + data.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
    }

    private String buildIndexHtml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<h1>SmartDisplay — Transferencia de archivos</h1>");
        sb.append("<h2>Subir al móvil (Laptop → Móvil)</h2>");
        sb.append("<form method=\"post\" action=\"/up\" enctype=\"multipart/form-data\">");
        sb.append("<input type=\"file\" name=\"f\" multiple> ");
        sb.append("<button type=\"submit\">Subir</button></form>");
        sb.append("<h2>Descargar del móvil (Móvil → Laptop)</h2>");
        if (shared.isEmpty()) {
            sb.append("<p>No hay archivos compartidos. Añádelos desde la app en el móvil.</p>");
        } else {
            sb.append("<ul>");
            for (int i = 0; i < shared.size(); i++) {
                SharedItem it = shared.get(i);
                String sz = it.size > 0 ? " (" + Formatter.formatShortFileSize(ctx, it.size) + ")" : "";
                sb.append("<li><a href=\"/dl?i=").append(i).append("\">")
                  .append(escapeHtml(it.name)).append("</a>").append(sz).append("</li>");
            }
            sb.append("</ul>");
        }
        return htmlPage(sb.toString());
    }

    private String htmlPage(String body) {
        return "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>SmartDisplay Files</title>"
                + "<style>body{font-family:sans-serif;max-width:680px;margin:24px auto;padding:0 16px;"
                + "background:#101418;color:#e2e2e5}h1{font-size:20px}h2{font-size:16px;margin-top:24px}"
                + "a{color:#7db0ff}button{padding:8px 16px;border:0;border-radius:8px;background:#4f80c8;color:#fff}"
                + "li{margin:6px 0}</style></head><body>" + body + "</body></html>";
    }

    // ── Parser multipart en streaming ───────────────────────────────────────────

    /**
     * Lee un cuerpo multipart/form-data en streaming: localiza los boundaries con
     * KMP y vuelca el contenido de cada parte directo a disco, sin retener el
     * cuerpo completo en memoria (evita OutOfMemoryError con archivos grandes).
     */
    private static final class MultipartReader {
        private final InputStream in;
        private final byte[] dash;          // "--boundary"
        private final int[] dashLps;
        private final byte[] term;          // "\r\n--boundary" (boundary precedido por su CRLF)
        private final int[] termLps;
        private long remaining;             // bytes del cuerpo por leer (-1 = sin límite)
        private final byte[] buf = new byte[64 * 1024];
        private int bufPos = 0, bufLen = 0;

        MultipartReader(InputStream in, int contentLength, byte[] dashBoundary) {
            this.in = in;
            this.dash = dashBoundary;
            this.dashLps = buildLps(dashBoundary);
            byte[] t = new byte[dashBoundary.length + 2];
            t[0] = '\r'; t[1] = '\n';
            System.arraycopy(dashBoundary, 0, t, 2, dashBoundary.length);
            this.term = t;
            this.termLps = buildLps(t);
            this.remaining = contentLength > 0 ? contentLength : -1;
        }

        private int readByte() throws IOException {
            if (remaining == 0) return -1;
            if (bufPos >= bufLen) {
                bufLen = in.read(buf);
                bufPos = 0;
                if (bufLen <= 0) return -1;
            }
            int b = buf[bufPos++] & 0xff;
            if (remaining > 0) remaining--;
            return b;
        }

        /** Descarta el preámbulo hasta consumir el primer boundary. */
        boolean skipToFirstBoundary() throws IOException {
            return scanTo(dash, dashLps, null) >= 0;
        }

        /**
         * Tras un boundary, consume sus 2 bytes finales y decide si es el de
         * cierre ("--") o si vienen más partes ("\r\n").
         */
        boolean atClosingBoundary() throws IOException {
            int a = readByte();
            int b = readByte();
            return a == -1 || (a == '-' && b == '-');
        }

        /** Lee las cabeceras de la parte hasta \r\n\r\n (excluido). */
        String readPartHeaders() throws IOException {
            ByteArrayOutputStream hb = new ByteArrayOutputStream(256);
            int p3 = -1, p2 = -1, p1 = -1, c;
            while ((c = readByte()) != -1) {
                hb.write(c);
                if (p3 == '\r' && p2 == '\n' && p1 == '\r' && c == '\n') {
                    byte[] d = hb.toByteArray();
                    return new String(d, 0, d.length - 4, StandardCharsets.ISO_8859_1);
                }
                p3 = p2; p2 = p1; p1 = c;
                if (hb.size() > 16 * 1024) break; // protección
            }
            return null;
        }

        /**
         * Vuelca el contenido de la parte (hasta el siguiente boundary) en
         * {@code out}; si {@code out} es null, lo descarta. Deja el stream
         * posicionado justo tras el boundary.
         * @return bytes de contenido escritos
         */
        long streamContentTo(OutputStream out) throws IOException {
            long n = scanTo(term, termLps, out);
            return n < 0 ? -n - 1 : n;
        }

        /**
         * Lee hasta encontrar {@code pattern}, emitiendo en {@code out} todo lo
         * anterior (búsqueda KMP en streaming, sin bufferizar el cuerpo). Deja el
         * stream justo tras el patrón.
         * @return bytes emitidos si se halló el patrón; si llegó a EOF sin hallarlo,
         *         devuelve {@code -(emitidos) - 1} (negativo) como señal de fin.
         */
        private long scanTo(byte[] pattern, int[] lps, OutputStream out) throws IOException {
            long emitted = 0;
            int m = 0;  // bytes del patrón coincididos = últimos m bytes leídos
            int c;
            while ((c = readByte()) != -1) {
                while (m > 0 && c != (pattern[m] & 0xff)) {
                    int k = lps[m - 1];
                    // los (m-k) bytes que salen de la ventana ya no son patrón: emitir
                    if (out != null) out.write(pattern, 0, m - k);
                    emitted += (m - k);
                    m = k;
                }
                if (c == (pattern[m] & 0xff)) {
                    m++;
                    if (m == pattern.length) return emitted; // patrón consumido (no se emite)
                } else {
                    if (out != null) out.write(c);
                    emitted++;
                }
            }
            // EOF sin patrón: emitir lo retenido (contenido incompleto, best-effort)
            if (m > 0 && out != null) out.write(pattern, 0, m);
            emitted += m;
            return -emitted - 1;
        }

        private static int[] buildLps(byte[] p) {
            int[] lps = new int[p.length];
            int len = 0;
            for (int i = 1; i < p.length; ) {
                if (p[i] == p[len]) { lps[i++] = ++len; }
                else if (len != 0) { len = lps[len - 1]; }
                else { lps[i++] = 0; }
            }
            return lps;
        }
    }

    private static String extractFilename(String headers) {
        int fi = headers.toLowerCase().indexOf("filename=");
        if (fi < 0) return null;
        int q1 = headers.indexOf('"', fi);
        if (q1 < 0) return null;
        int q2 = headers.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        String name = headers.substring(q1 + 1, q2);
        try { name = URLDecoder.decode(name, "UTF-8"); } catch (Exception ignored) {}
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        return name;
    }

    private static String sanitize(String name) {
        if (name == null) return "archivo";
        return name.replaceAll("[\\\\/:*?\"<>|\r\n]", "_");
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 1; i < 1000; i++) {
            File c = new File(dir, base + "(" + i + ")" + ext);
            if (!c.exists()) return c;
        }
        return f;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
