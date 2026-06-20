package com.limelight;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.limelight.computers.ComputerDatabaseManager;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.computers.ComputerManagerService;

/**
 * SmartDisplayReceiver - Handles intents from the PC-side Electron app.
 *
 * ADD_PC: Injects or UPDATES an existing PC entry (merges IPs, does NOT
 *         create a duplicate). Auto-connect is only triggered if the PC
 *         was not already in the database (first-time pairing).
 */
public class SmartDisplayReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if ("com.limelight.smartdisplay.START_SCREEN_CAPTURE".equals(action)) {
            String pcIp = intent.getStringExtra("PC_IP");
            String token = intent.getStringExtra("TOKEN");

            Intent activityIntent = new Intent(context, ScreenCaptureActivity.class);
            activityIntent.putExtra("PC_IP", pcIp);
            activityIntent.putExtra("TOKEN", token);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
        else if ("com.limelight.smartdisplay.STOP_SCREEN_CAPTURE".equals(action)) {
            Intent serviceIntent = new Intent(context, ScreenCaptureService.class);
            context.stopService(serviceIntent);
        }
        else if ("com.limelight.smartdisplay.ADD_PC".equals(action)) {
            String ip          = intent.getStringExtra("IP");
            String tailscaleIp = intent.getStringExtra("TAILSCALE_IP");
            String name        = intent.getStringExtra("NAME");
            String uuid        = intent.getStringExtra("UUID");

            if (ip == null || uuid == null) return;

            ComputerDatabaseManager db = new ComputerDatabaseManager(context.getApplicationContext());

            // Check if this UUID already exists in the database
            ComputerDetails existing = db.getComputerByUUID(uuid);
            boolean isNew = (existing == null);

            // Build the updated details object
            ComputerDetails details = isNew ? new ComputerDetails() : existing;
            details.uuid = uuid;

            // Only override name if it is a new entry (keep "emma" / user-set name)
            if (isNew && name != null && !name.trim().isEmpty()) {
                details.name = name;
            }

            // Always update addresses so Tailscale IP stays fresh
            details.localAddress = new ComputerDetails.AddressTuple(ip, NvHTTP.DEFAULT_HTTP_PORT);
            if (tailscaleIp != null && !tailscaleIp.trim().isEmpty()) {
                details.manualAddress = new ComputerDetails.AddressTuple(tailscaleIp, NvHTTP.DEFAULT_HTTP_PORT);
                details.remoteAddress = new ComputerDetails.AddressTuple(tailscaleIp, NvHTTP.DEFAULT_HTTP_PORT);
            } else {
                details.manualAddress = new ComputerDetails.AddressTuple(ip, NvHTTP.DEFAULT_HTTP_PORT);
            }

            db.updateComputer(details);
            db.close();

            // Notify ComputerManagerService to reload polling state
            Intent reloadIntent = new Intent(context, ComputerManagerService.class);
            reloadIntent.setAction("com.limelight.computers.RELOAD");
            context.startService(reloadIntent);

            // Auto-connect when the PC is recognized, unless it's just a background heartbeat
            boolean isHeartbeat = intent.getBooleanExtra("HEARTBEAT", false);
            if (!isHeartbeat) {
                if (com.limelight.Game.isStreaming || com.limelight.ShortcutTrampoline.isTrampolining) {
                    android.util.Log.i("SmartDisplayReceiver", "Ignoring ADD_PC auto-connect because a stream or pairing is already active.");
                    return;
                }
                Intent trampolineIntent = new Intent(context, ShortcutTrampoline.class);
                trampolineIntent.putExtra(AppView.UUID_EXTRA, uuid);
                trampolineIntent.putExtra(AppView.NAME_EXTRA, details.name);
                trampolineIntent.putExtra("AUTO_CONNECT", true);
                trampolineIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(trampolineIntent);
            }
        }
    }
}
