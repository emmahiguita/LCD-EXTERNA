package com.limelight;

import android.app.Activity;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.computers.ComputerManagerListener;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.nvstream.wol.WakeOnLanSender;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.Dialog;
import com.limelight.utils.ServerHelper;
import com.limelight.utils.SpinnerDialog;
import com.limelight.utils.UiHelper;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import android.util.Log;

public class ShortcutTrampoline extends Activity {
    private String uuidString;
    private NvApp app;
    private ArrayList<Intent> intentStack = new ArrayList<>();

    private int wakeHostTries = 10;
    private ComputerDetails computer;
    private SpinnerDialog blockingLoadSpinner;
    
    public static boolean isTrampolining = false;
    private volatile boolean isPairing = false;

    private ComputerManagerService.ComputerManagerBinder managerBinder;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            final ComputerManagerService.ComputerManagerBinder localBinder =
                    ((ComputerManagerService.ComputerManagerBinder)binder);

            // Wait in a separate thread to avoid stalling the UI
            new Thread() {
                @Override
                public void run() {
                    // Wait for the binder to be ready
                    localBinder.waitForReady();

                    // Now make the binder visible
                    managerBinder = localBinder;

                    // Get the computer object
                    computer = managerBinder.getComputer(uuidString);

                    if (computer == null) {
                        Dialog.displayDialog(ShortcutTrampoline.this,
                                getResources().getString(R.string.conn_error_title),
                                getResources().getString(R.string.scut_pc_not_found),
                                true);

                        if (blockingLoadSpinner != null) {
                            blockingLoadSpinner.dismiss();
                            blockingLoadSpinner = null;
                        }

                        if (managerBinder != null) {
                            unbindService(serviceConnection);
                            managerBinder = null;
                        }

                        return;
                    }

                    // Force CMS to repoll this machine
                    managerBinder.invalidateStateForComputer(computer.uuid);

                    // Start polling
                    managerBinder.startPolling(new ComputerManagerListener() {
                        @Override
                        public void notifyComputerUpdated(final ComputerDetails details) {
                            // Don't care about other computers
                            if (!details.uuid.equalsIgnoreCase(uuidString)) {
                                return;
                            }

                            // Try to wake the target PC if it's offline (up to some retry limit)
                            if (details.state == ComputerDetails.State.OFFLINE && details.macAddress != null && --wakeHostTries >= 0) {
                                try {
                                    // Make a best effort attempt to wake the target PC
                                    WakeOnLanSender.sendWolPacket(computer);

                                    // If we sent at least one WoL packet, reset the computer state
                                    // to force ComputerManager to poll it again.
                                    managerBinder.invalidateStateForComputer(computer.uuid);
                                    return;
                                } catch (IOException e) {
                                    // If we got an exception, we couldn't send a single WoL packet,
                                    // so fallthrough into the offline error path.
                                    e.printStackTrace();
                                }
                            }

                            if (details.state != ComputerDetails.State.UNKNOWN) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Stop showing the spinner
                                        if (blockingLoadSpinner != null) {
                                            blockingLoadSpinner.dismiss();
                                            blockingLoadSpinner = null;
                                        }

                                        // If the managerBinder was destroyed before this callback,
                                        // just finish the activity.
                                        if (managerBinder == null) {
                                            finish();
                                            return;
                                        }

                                        if (details.state == ComputerDetails.State.ONLINE && details.pairState == PairingManager.PairState.PAIRED) {
                                            
                                            // Launch game if provided app ID, otherwise launch app view
                                            if (app != null) {
                                                if (details.runningGameId == 0 || details.runningGameId == app.getAppId()) {
                                                    intentStack.add(ServerHelper.createStartIntent(ShortcutTrampoline.this, app, details, managerBinder));

                                                    // Close this activity
                                                    finish();

                                                    // Now start the activities
                                                    startActivities(intentStack.toArray(new Intent[]{}));
                                                } else {
                                                    // Create the start intent immediately, so we can safely unbind the managerBinder
                                                    // below before we return.
                                                    final Intent startIntent = ServerHelper.createStartIntent(ShortcutTrampoline.this, app, details, managerBinder);

                                                    UiHelper.displayQuitConfirmationDialog(ShortcutTrampoline.this, new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            intentStack.add(startIntent);

                                                            // Close this activity
                                                            finish();

                                                            // Now start the activities
                                                            startActivities(intentStack.toArray(new Intent[]{}));
                                                        }
                                                    }, new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            // Close this activity
                                                            finish();
                                                        }
                                                    });
                                                }
                                            } else if (getIntent().getBooleanExtra("AUTO_CONNECT", false)) {
                                                // Automatically query app list and connect
                                                new Thread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        try {
                                                            final com.limelight.nvstream.http.ComputerDetails.AddressTuple connectAddress;
                                                            if (details.activeAddress != null) {
                                                                connectAddress = details.activeAddress;
                                                            } else if (details.manualAddress != null) {
                                                                connectAddress = details.manualAddress;
                                                            } else {
                                                                connectAddress = details.localAddress;
                                                            }

                                                            com.limelight.nvstream.http.NvHTTP http = new com.limelight.nvstream.http.NvHTTP(
                                                                    connectAddress, 
                                                                    details.httpsPort, 
                                                                    managerBinder.getUniqueId(), 
                                                                    details.serverCert, 
                                                                    com.limelight.binding.PlatformBinding.getCryptoProvider(ShortcutTrampoline.this)
                                                            );
                                                            
                                                            Log.i("ShortcutTrampoline", "Fetching app list from " + connectAddress.address);
                                                            List<NvApp> appList = http.getAppList();
                                                            if (appList != null && !appList.isEmpty()) {
                                                                NvApp targetApp = null;
                                                                for (NvApp a : appList) {
                                                                    if (a.getAppName().toLowerCase().contains("desktop") || 
                                                                        a.getAppName().toLowerCase().contains("escritorio") ||
                                                                        a.getAppName().toLowerCase().contains("mstsc")) {
                                                                        targetApp = a;
                                                                        break;
                                                                    }
                                                                }
                                                                if (targetApp == null) {
                                                                    targetApp = appList.get(0);
                                                                }
                                                                
                                                                Log.i("ShortcutTrampoline", "Auto-starting app: " + targetApp.getAppName());
                                                                final Intent startIntent = ServerHelper.createStartIntent(ShortcutTrampoline.this, targetApp, details, managerBinder);
                                                                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                                
                                                                ShortcutTrampoline.isTrampolining = false;
                                                                runOnUiThread(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        finish();
                                                                        startActivity(startIntent);
                                                                    }
                                                                });
                                                            } else {
                                                                Log.e("ShortcutTrampoline", "App list is empty or null, cannot auto-connect.");
                                                                ShortcutTrampoline.isTrampolining = false;
                                                                runOnUiThread(new Runnable() {
                                                                    @Override
                                                                    public void run() {
                                                                        Dialog.displayDialog(ShortcutTrampoline.this, "Error", "No se encontraron aplicaciones (Desktop) en Sunshine.", true);
                                                                    }
                                                                });
                                                            }
                                                        } catch (final Exception e) {
                                                            e.printStackTrace();
                                                            Log.e("ShortcutTrampoline", "Error fetching app list: " + e.getMessage());
                                                            ShortcutTrampoline.isTrampolining = false;
                                                            runOnUiThread(new Runnable() {
                                                                @Override
                                                                public void run() {
                                                                    Dialog.displayDialog(ShortcutTrampoline.this, "Error de Conexión", "No se pudo iniciar la transmisión: " + e.getMessage(), true);
                                                                }
                                                            });
                                                        }
                                                    }
                                                }).start();
                                            } else {
                                                // Close this activity
                                                finish();

                                                // Add the PC view at the back (and clear the task)
                                                Intent i;
                                                i = new Intent(ShortcutTrampoline.this, PcView.class);
                                                i.setAction(Intent.ACTION_MAIN);
                                                i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                                intentStack.add(i);

                                                // Take this intent's data and create an intent to start the app view
                                                i = new Intent(getIntent());
                                                i.setClass(ShortcutTrampoline.this, AppView.class);
                                                intentStack.add(i);

                                                // If a game is running, we'll make the stream the top level activity
                                                if (details.runningGameId != 0) {
                                                    intentStack.add(ServerHelper.createStartIntent(ShortcutTrampoline.this,
                                                            new NvApp(null, details.runningGameId, false), details, managerBinder));
                                                }

                                                // Now start the activities
                                                startActivities(intentStack.toArray(new Intent[]{}));
                                            }
                                            
                                        }
                                        else if (details.state == ComputerDetails.State.OFFLINE) {
                                            // Computer offline - display an error dialog
                                            Dialog.displayDialog(ShortcutTrampoline.this,
                                                    getResources().getString(R.string.conn_error_title),
                                                    getResources().getString(R.string.error_pc_offline),
                                                    true);
                                        } else if (details.pairState != PairingManager.PairState.PAIRED) {
                                            if (getIntent().getBooleanExtra("AUTO_CONNECT", false)) {
                                                // CRITICAL: Prevent multiple concurrent pairing threads.
                                                // notifyComputerUpdated fires every 1.5s from the polling thread.
                                                // Without this guard, we'd spawn a new pairing thread each time.
                                                if (isPairing) {
                                                    Log.i("ShortcutTrampoline", "Already pairing, ignoring duplicate callback");
                                                    return;
                                                }
                                                isPairing = true;

                                                final String pinStr = "9999";
                                                Log.i("ShortcutTrampoline", "Auto-pairing with PIN " + pinStr);

                                                // Use the activeAddress that polling CONFIRMED is reachable.
                                                final com.limelight.nvstream.http.ComputerDetails.AddressTuple pairAddress;
                                                if (details.activeAddress != null) {
                                                    pairAddress = details.activeAddress;
                                                } else if (details.manualAddress != null) {
                                                    pairAddress = details.manualAddress;
                                                } else {
                                                    pairAddress = details.localAddress;
                                                }

                                                Log.i("ShortcutTrampoline", "Auto-pairing using address: " + pairAddress 
                                                    + " (httpsPort=" + details.httpsPort + ")");

                                                // CRITICAL: Stop polling BEFORE pairing.
                                                // Polling hits Sunshine every 1.5s on the same HTTP port.
                                                // Concurrent HTTP requests during pairing cause Sunshine
                                                // to drop connections ("unexpected end of stream").
                                                managerBinder.stopPolling();
                                                Log.i("ShortcutTrampoline", "Stopped polling to avoid interference with pairing");

                                                Dialog.displayDialog(ShortcutTrampoline.this,
                                                        "Emparejando...",
                                                        "Ingresa este PIN en Sunshine (tu PC):\n\n" + pinStr +
                                                        "\n\nConectando a: " + pairAddress.address, false);

                                                // Start pairing in background - ONLY ONE thread
                                                new Thread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        String lastError = "";
                                                        
                                                        for (int attempt = 1; attempt <= 3; attempt++) {
                                                            try {
                                                                Log.i("ShortcutTrampoline", "Pairing attempt " + attempt + "/3 to " + pairAddress);
                                                                com.limelight.nvstream.http.NvHTTP httpConn = new com.limelight.nvstream.http.NvHTTP(
                                                                        pairAddress,
                                                                        details.httpsPort,
                                                                        managerBinder.getUniqueId(),
                                                                        details.serverCert,
                                                                        com.limelight.binding.PlatformBinding.getCryptoProvider(ShortcutTrampoline.this));

                                                                // First verify we can reach the server
                                                                String serverInfo = httpConn.getServerInfo(true);
                                                                Log.i("ShortcutTrampoline", "Got serverInfo OK, proceeding to pair");

                                                                PairingManager pm = httpConn.getPairingManager();
                                                                PairingManager.PairState pairState = pm.pair(serverInfo, pinStr);

                                                                Log.i("ShortcutTrampoline", "Pair result: " + pairState);

                                                                if (pairState == PairingManager.PairState.PAIRED) {
                                                                    // Save cert to in-memory AND database
                                                                    com.limelight.nvstream.http.ComputerDetails paired = managerBinder.getComputer(details.uuid);
                                                                    if (paired != null) {
                                                                        paired.serverCert = pm.getPairedCert();
                                                                    }
                                                                    ComputerDatabaseManager db = new ComputerDatabaseManager(ShortcutTrampoline.this);
                                                                    com.limelight.nvstream.http.ComputerDetails dbComp = db.getComputerByUUID(details.uuid);
                                                                    if (dbComp != null) {
                                                                        dbComp.serverCert = pm.getPairedCert();
                                                                        db.updateComputer(dbComp);
                                                                    }
                                                                    db.close();
                                                                    managerBinder.invalidateStateForComputer(details.uuid);

                                                                    isPairing = false;
                                                                    runOnUiThread(new Runnable() {
                                                                        @Override
                                                                        public void run() {
                                                                            Dialog.closeDialogs();
                                                                            Intent retryIntent = new Intent(getIntent());
                                                                            finish();
                                                                            startActivity(retryIntent);
                                                                        }
                                                                    });
                                                                    return;
                                                                } else if (pairState == PairingManager.PairState.ALREADY_IN_PROGRESS) {
                                                                    lastError = "Otro dispositivo ya está emparejando";
                                                                    Thread.sleep(5000);
                                                                } else if (pairState == PairingManager.PairState.PIN_WRONG) {
                                                                    lastError = "PIN rechazado - ingresa 9999 en Sunshine";
                                                                    Thread.sleep(5000);
                                                                } else {
                                                                    lastError = "Estado: " + pairState;
                                                                    break;
                                                                }
                                                            } catch (InterruptedException ie) {
                                                                Thread.currentThread().interrupt();
                                                                isPairing = false;
                                                                return;
                                                            } catch (Exception e) {
                                                                lastError = (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                                                                Log.e("ShortcutTrampoline", "Pairing exception attempt " + attempt + ": " + lastError);
                                                                e.printStackTrace();
                                                                if (attempt < 3) {
                                                                    try { Thread.sleep(2000); } catch (InterruptedException ie2) { break; }
                                                                }
                                                            }
                                                        }
                                                        
                                                        isPairing = false;
                                                        final String finalError = lastError;
                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                Dialog.closeDialogs();
                                                                Dialog.displayDialog(ShortcutTrampoline.this,
                                                                        "Error de Emparejamiento",
                                                                        "Dirección: " + pairAddress.address + ":" + pairAddress.port + "\n" +
                                                                        "Error: " + finalError + "\n\n" +
                                                                        "Verifica:\n1. Sunshine está corriendo\n2. Ingresaste 9999 en Sunshine\n3. El firewall permite el puerto " + pairAddress.port,
                                                                        true);
                                                            }
                                                        });
                                                    }
                                                }).start();

                                                // Do NOT unbind while pairing is in progress
                                                return;
                                            } else {
                                                // Computer not paired - display an error dialog
                                                Dialog.displayDialog(ShortcutTrampoline.this,
                                                        getResources().getString(R.string.conn_error_title),
                                                        getResources().getString(R.string.scut_not_paired),
                                                        true);
                                            }
                                        }

                                        // We don't want any more callbacks from now on, so go ahead
                                        // and unbind from the service
                                        if (managerBinder != null) {
                                            managerBinder.stopPolling();
                                            unbindService(serviceConnection);
                                            managerBinder = null;
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }.start();
        }

        public void onServiceDisconnected(ComponentName className) {
            managerBinder = null;
        }
    };

    protected boolean validateInput(String uuidString, String appIdString, String nameString) {
        // Validate PC UUID/Name
        if (uuidString == null && nameString == null) {
            Dialog.displayDialog(ShortcutTrampoline.this,
                    getResources().getString(R.string.conn_error_title),
                    getResources().getString(R.string.scut_invalid_uuid),
                    true);
            return false;
        }

        if (uuidString != null && !uuidString.isEmpty()) {
            try {
                UUID.fromString(uuidString);
            } catch (IllegalArgumentException ex) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_uuid),
                        true);
                return false;
            }
        } else {
            // UUID is null, so fallback to Name
            if (nameString == null || nameString.isEmpty()) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_uuid),
                        true);
                return false;
            }
        }

        // Validate App ID (if provided)
        if (appIdString != null && !appIdString.isEmpty()) {
            try {
                Integer.parseInt(appIdString);
            } catch (NumberFormatException ex) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_app_id),
                        true);
                return false;
            }
        }

        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isTrampolining = true;

        UiHelper.notifyNewRootView(this);
        ComputerDatabaseManager dbManager = new ComputerDatabaseManager(this);
        ComputerDetails _computer = null;

        // PC arguments, both are optional, but at least one must be provided
        uuidString = getIntent().getStringExtra(AppView.UUID_EXTRA);
        String nameString = getIntent().getStringExtra(AppView.NAME_EXTRA);

        // App arguments, both are optional, but one must be provided in order to start an app
        String appIdString = getIntent().getStringExtra(Game.EXTRA_APP_ID);
        String appNameString = getIntent().getStringExtra(Game.EXTRA_APP_NAME);

        if (!validateInput(uuidString, appIdString, nameString)) {
            // Invalid input, so just return
            return;
        }

        if (uuidString == null || uuidString.isEmpty()) {
            // Use nameString to find the corresponding UUID
            _computer = dbManager.getComputerByName(nameString);

            if (_computer == null) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_pc_not_found),
                        true);
                return;
            }

            uuidString = _computer.uuid;

            // Set the AppView UUID intent, since it wasn't provided
            setIntent(new Intent(getIntent()).putExtra(AppView.UUID_EXTRA, uuidString));
        }

        if (appIdString != null && !appIdString.isEmpty()) {
            app = new NvApp(getIntent().getStringExtra(Game.EXTRA_APP_NAME),
                    Integer.parseInt(appIdString),
                    getIntent().getBooleanExtra(Game.EXTRA_APP_HDR, false));
        }
        else if (appNameString != null && !appNameString.isEmpty()) {
            // Use appNameString to find the corresponding AppId
            try {
                int appId = -1;
                String rawAppList = CacheHelper.readInputStreamToString(CacheHelper.openCacheFileForInput(getCacheDir(), "applist", uuidString));

                if (rawAppList.isEmpty()) {
                    Dialog.displayDialog(ShortcutTrampoline.this,
                            getResources().getString(R.string.conn_error_title),
                            getResources().getString(R.string.scut_invalid_app_id),
                            true);
                    return;
                }
                List<NvApp> applist = NvHTTP.getAppListByReader(new StringReader(rawAppList));

                for (NvApp _app : applist) {
                    if (_app.getAppName().equals(appNameString)) {
                        appId = _app.getAppId();
                        break;
                    }
                }
                if (appId < 0) {
                    Dialog.displayDialog(ShortcutTrampoline.this,
                            getResources().getString(R.string.conn_error_title),
                            getResources().getString(R.string.scut_invalid_app_id),
                            true);
                    return;
                }
                setIntent(new Intent(getIntent()).putExtra(Game.EXTRA_APP_ID, appId));
                app = new NvApp(
                        appNameString,
                        appId,
                        getIntent().getBooleanExtra(Game.EXTRA_APP_HDR, false));
            } catch (IOException | XmlPullParserException e) {
                Dialog.displayDialog(ShortcutTrampoline.this,
                        getResources().getString(R.string.conn_error_title),
                        getResources().getString(R.string.scut_invalid_app_id),
                        true);
                return;
            }
        }

        // Bind to the computer manager service
        bindService(new Intent(this, ComputerManagerService.class), serviceConnection,
                Service.BIND_AUTO_CREATE);

        blockingLoadSpinner = SpinnerDialog.displayDialog(this, getResources().getString(R.string.conn_establishing_title),
                getResources().getString(R.string.applist_connect_msg), true);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (blockingLoadSpinner != null) {
            blockingLoadSpinner.dismiss();
            blockingLoadSpinner = null;
        }

        Dialog.closeDialogs();

        if (managerBinder != null) {
            managerBinder.stopPolling();
            unbindService(serviceConnection);
            managerBinder = null;
        }
        
        isTrampolining = false;

        finish();
    }
}
