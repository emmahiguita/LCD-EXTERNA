package com.limelight;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.smartdisplay.files.FileTransferServer;
import com.limelight.utils.UiHelper;

import java.io.File;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Pantalla de transferencia de archivos por WiFi (SmartDisplay Files).
 *
 * Levanta {@link FileTransferServer} en el móvil y muestra la URL para abrir
 * desde el navegador del laptop. Permite compartir archivos del móvil
 * (Móvil→Laptop) y recibir los que suba el laptop (Laptop→Móvil).
 */
public class FileTransferActivity extends Activity implements FileTransferServer.Listener {

    private static final int REQ_PICK = 4001;

    private FileTransferServer server;
    private TextView statusText;
    private TextView urlText;
    private LinearLayout sharedList;
    private TextView receivedText;
    private LinearLayout receivedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        com.limelight.utils.ThemeManager.apply(this);
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        setContentView(R.layout.activity_file_transfer);

        View btnClose = findViewById(R.id.btnClose);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> finish());
        }

        statusText   = findViewById(R.id.ft_status);
        urlText      = findViewById(R.id.ft_url);
        sharedList   = findViewById(R.id.ft_shared_list);
        receivedText = findViewById(R.id.ft_received);
        receivedList = findViewById(R.id.ft_received_list);

        Button pick = findViewById(R.id.ft_btn_share);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickFiles(); }
        });

        Button openReceived = findViewById(R.id.ft_btn_received);
        openReceived.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshReceived(); }
        });

        server = new FileTransferServer(this, FileTransferServer.DEFAULT_PORT);
        server.setListener(this);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.activity_fade_enter, R.anim.activity_slide_out_right);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (server != null && !server.isRunning()) {
            server.start();
        }
        updateUi();
        refreshReceived();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
    }

    // ── Elegir archivos del móvil para compartir (Móvil → Laptop) ───────────────

    private void pickFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "No hay selector de archivos disponible", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK || resultCode != RESULT_OK || data == null) return;

        if (data.getClipData() != null) {
            int n = data.getClipData().getItemCount();
            for (int k = 0; k < n; k++) {
                addSharedUri(data.getClipData().getItemAt(k).getUri());
            }
        } else if (data.getData() != null) {
            addSharedUri(data.getData());
        }
        updateUi();
    }

    private void addSharedUri(Uri uri) {
        if (uri == null) return;
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {}
        String name = "archivo";
        long size = 0;
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int si = c.getColumnIndex(OpenableColumns.SIZE);
                if (ni >= 0) name = c.getString(ni);
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si);
            }
        } catch (Exception ignored) {}
        server.addShared(new FileTransferServer.SharedItem(uri, name, size));
    }

    // ── UI ──────────────────────────────────────────────────────────────────────

    private void updateUi() {
        boolean running = server != null && server.isRunning();
        statusText.setText(running ? "● Servidor activo" : "○ Servidor detenido");
        statusText.setTextColor(running ? 0xFF7DDAFF : 0xFF94A3B8);
        if (running) {
            String ip = getWifiIpAddress();
            if (ip != null) {
                urlText.setText("Abre en el navegador del PC:\n\nhttp://" + ip + ":" + server.getPort());
            } else {
                urlText.setText("Conéctate a la misma red Wi-Fi que el PC para obtener la dirección.");
            }
        } else {
            urlText.setText("");
        }
        // Lista de archivos compartidos
        sharedList.removeAllViews();
        if (server != null) {
            for (FileTransferServer.SharedItem it : server.getShared()) {
                TextView tv = new TextView(this);
                tv.setText("• " + it.name);
                tv.setTextColor(0xFFF8FAFC);
                tv.setTextSize(14f);
                tv.setPadding(8, 10, 8, 10);
                sharedList.addView(tv);
            }
        }
    }

    private void refreshReceived() {
        if (server == null) return;
        File dir = server.getReceivedDir();
        File[] files = dir.listFiles();
        receivedList.removeAllViews();

        if (files == null || files.length == 0) {
            receivedText.setText("(aún no hay archivos recibidos del PC)");
            return;
        }
        receivedText.setText("Toca un archivo para abrirlo o compartirlo:");
        for (final File f : files) {
            if (f.isDirectory()) continue;
            TextView row = new TextView(this);
            String sz = android.text.format.Formatter.formatShortFileSize(this, f.length());
            row.setText("📄  " + f.getName() + "   (" + sz + ")");
            row.setTextColor(0xFFF8FAFC);
            row.setTextSize(15f);
            row.setPadding(8, 16, 8, 16);
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showFileOptions(f); }
            });
            receivedList.addView(row);
        }
    }

    /** Diálogo Abrir / Compartir para un archivo recibido. */
    private void showFileOptions(final File file) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(file.getName())
                .setItems(new CharSequence[]{"Abrir", "Compartir", "Eliminar"},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                if (which == 0) openReceived(file);
                                else if (which == 1) shareReceived(file);
                                else { if (file.delete()) refreshReceived(); }
                            }
                        })
                .show();
    }

    private Uri providerUri(File f) {
        return androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", f);
    }

    private String mimeOf(File f) {
        String name = f.getName();
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase();
            String m = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (m != null) return m;
        }
        return "*/*";
    }

    private void openReceived(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(providerUri(f), mimeOf(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Abrir con"));
        } catch (Exception e) {
            Toast.makeText(this, "No hay app para abrir este archivo", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReceived(File f) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mimeOf(f));
            i.putExtra(Intent.EXTRA_STREAM, providerUri(f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Compartir"));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo compartir", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onStateChanged(boolean running) {
        runOnUiThread(this::updateUi);
    }

    @Override
    public void onFileReceived(File file) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Recibido: " + file.getName(), Toast.LENGTH_SHORT).show();
            com.limelight.utils.NotificationHelper.notifyFileTransfer(this, file.getName(), false);
            refreshReceived();
        });
    }

    /** Devuelve la IPv4 de la interfaz WiFi (o cualquier IPv4 no-loopback). */
    private String getWifiIpAddress() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String name = ni.getName().toLowerCase();
                boolean wifiLike = name.contains("wlan") || name.contains("ap") || name.contains("eth");
                Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        if (wifiLike) return addr.getHostAddress();
                    }
                }
            }
            // Fallback: cualquier IPv4 no loopback
            ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                Enumeration<java.net.InetAddress> addrs = ifaces.nextElement().getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
