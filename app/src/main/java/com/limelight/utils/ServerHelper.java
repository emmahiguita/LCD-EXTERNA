package com.limelight.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.limelight.AppView;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.ShortcutTrampoline;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;

public class ServerHelper {
    public static final String CONNECTION_TEST_SERVER = "android.conntest.moonlight-stream.org";

    public static ComputerDetails.AddressTuple getCurrentAddressFromComputer(ComputerDetails computer) throws IOException {
        if (computer.activeAddress == null) {
            throw new IOException("No active address for "+computer.name);
        }
        return computer.activeAddress;
    }

    public static Intent createPcShortcutIntent(Activity parent, ComputerDetails computer) {
        Intent i = new Intent(parent, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.setAction(Intent.ACTION_DEFAULT);
        return i;
    }

    public static Intent createAppShortcutIntent(Activity parent, ComputerDetails computer, NvApp app) {
        Intent i = new Intent(parent, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        i.putExtra(Game.EXTRA_APP_ID, ""+app.getAppId());
        i.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        i.setAction(Intent.ACTION_DEFAULT);
        return i;
    }

    public static Intent createStartIntent(Activity parent, NvApp app, ComputerDetails computer,
                                           ComputerManagerService.ComputerManagerBinder managerBinder) {
        Intent intent = new Intent(parent, Game.class);
        intent.putExtra(Game.EXTRA_HOST, computer.activeAddress.address);
        intent.putExtra(Game.EXTRA_PORT, computer.activeAddress.port);
        intent.putExtra(Game.EXTRA_HTTPS_PORT, computer.httpsPort);
        intent.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        intent.putExtra(Game.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(Game.EXTRA_PC_UUID, computer.uuid);
        intent.putExtra(Game.EXTRA_PC_NAME, computer.name);
        if (computer.macAddress != null && !computer.macAddress.isEmpty()) {
            intent.putExtra(Game.EXTRA_MAC, computer.macAddress);
        }
        try {
            if (computer.serverCert != null) {
                intent.putExtra(Game.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        }

        // SmartDisplay AI (FASE 2): registrar la última sesión para el acceso rápido
        try {
            new com.limelight.smartdisplay.recents.LastSessionStore(parent)
                    .record(computer.uuid, computer.name,
                            app.getAppName(), app.getAppId(), app.isHdrSupported());
        } catch (Exception e) {
            // El registro de "última sesión" nunca debe afectar al lanzamiento
        }

        return intent;
    }

    public static void doStart(Activity parent, NvApp app, ComputerDetails computer,
                               ComputerManagerService.ComputerManagerBinder managerBinder) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(parent, parent.getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        parent.startActivity(createStartIntent(parent, app, computer, managerBinder));
    }

    public static void doNetworkTest(final Activity parent) {
        if (parent == null || parent.isFinishing()) return;

        // Crear diálogo de progreso Material 3
        android.widget.LinearLayout layout = new android.widget.LinearLayout(parent);
        layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        layout.setPadding(60, 40, 60, 40);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);

        com.google.android.material.progressindicator.CircularProgressIndicator progress =
                new com.google.android.material.progressindicator.CircularProgressIndicator(parent);
        progress.setIndeterminate(true);
        progress.setIndicatorSize(90);
        progress.setTrackCornerRadius(45);
        layout.addView(progress);

        android.widget.TextView textView = new android.widget.TextView(parent);
        textView.setText(parent.getResources().getString(R.string.nettest_text_waiting));
        textView.setPadding(40, 0, 0, 0);
        textView.setTextSize(14);
        textView.setTextColor(parent.getResources().getColor(android.R.color.white));
        layout.addView(textView);

        final androidx.appcompat.app.AlertDialog progressDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(parent)
                .setTitle(parent.getResources().getString(R.string.nettest_title_waiting))
                .setView(layout)
                .setCancelable(false)
                .create();

        progressDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Obtener info de interfaz de red local
                final StringBuilder netInfo = new StringBuilder();
                try {
                    android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                            parent.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
                    if (cm != null) {
                        android.net.Network activeNet = cm.getActiveNetwork();
                        if (activeNet != null) {
                            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                            if (caps != null) {
                                if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
                                    netInfo.append("• Red: Wi-Fi\n");
                                } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) {
                                    netInfo.append("• Red: Ethernet\n");
                                } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)) {
                                    netInfo.append("• Red: Datos Móviles\n");
                                } else if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) {
                                    netInfo.append("• Red: VPN / Tailscale\n");
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}

                int ret = MoonBridge.testClientConnectivity(CONNECTION_TEST_SERVER, 443, MoonBridge.ML_PORT_FLAG_ALL);

                final StringBuilder dialogSummary = new StringBuilder();
                if (netInfo.length() > 0) {
                    dialogSummary.append(netInfo).append("\n");
                }

                if (ret == 0) {
                    dialogSummary.append("✓ ").append(parent.getResources().getString(R.string.nettest_text_success));
                } else if (ret == MoonBridge.ML_TEST_RESULT_INCONCLUSIVE) {
                    dialogSummary.append("ℹ Red local operativa.\n\n")
                            .append("La prueba con el servidor externo de diagnóstico no pudo completarse, pero la conexión local (LAN / Wi-Fi) está disponible para transmitir hacia tus equipos.");
                } else {
                    dialogSummary.append("⚠ ").append(parent.getResources().getString(R.string.nettest_text_failure));
                    dialogSummary.append(MoonBridge.stringifyPortFlags(ret, "\n"));
                }

                parent.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (progressDialog.isShowing()) {
                                progressDialog.dismiss();
                            }
                        } catch (Exception ignored) {}

                        if (parent.isFinishing() || (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1 && parent.isDestroyed())) {
                            return;
                        }

                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(parent)
                                .setTitle(parent.getResources().getString(R.string.nettest_title_done))
                                .setMessage(dialogSummary.toString())
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                });
            }
        }).start();
    }

    public static void doQuit(final Activity parent,
                              final ComputerDetails computer,
                              final NvApp app,
                              final ComputerManagerService.ComputerManagerBinder managerBinder,
                              final Runnable onComplete) {
        Toast.makeText(parent, parent.getResources().getString(R.string.applist_quit_app) + " " + app.getAppName() + "...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                NvHTTP httpConn;
                String message;
                try {
                    httpConn = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                            managerBinder.getUniqueId(), computer.serverCert, PlatformBinding.getCryptoProvider(parent));
                    if (httpConn.quitApp()) {
                        message = parent.getResources().getString(R.string.applist_quit_success) + " " + app.getAppName();
                    } else {
                        message = parent.getResources().getString(R.string.applist_quit_fail) + " " + app.getAppName();
                    }
                } catch (HostHttpResponseException e) {
                    if (e.getErrorCode() == 599) {
                        message = "This session wasn't started by this device," +
                                " so it cannot be quit. End streaming on the original " +
                                "device or the PC itself. (Error code: "+e.getErrorCode()+")";
                    }
                    else {
                        message = e.getMessage();
                    }
                } catch (UnknownHostException e) {
                    message = parent.getResources().getString(R.string.error_unknown_host);
                } catch (FileNotFoundException e) {
                    message = parent.getResources().getString(R.string.error_404);
                } catch (IOException | XmlPullParserException e) {
                    message = e.getMessage();
                    e.printStackTrace();
                } finally {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                final String toastMessage = message;
                parent.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(parent, toastMessage, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
}
