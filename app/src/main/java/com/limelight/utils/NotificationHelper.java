package com.limelight.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.limelight.R;

/**
 * Centraliza las notificaciones de estado de SmartDisplay (equipo en línea,
 * emparejamiento requerido, conexión perdida y transferencia de archivos).
 * Independiente de la notificación de servicio en primer plano que usa la
 * captura de pantalla.
 */
public final class NotificationHelper {

    private static final String CHANNEL_ID = "smartdisplay_status";

    // IDs base por tipo (para no pisarse entre sí).
    private static final int ID_ONLINE   = 2001;
    private static final int ID_PAIRING  = 2002;
    private static final int ID_LOST     = 2003;
    private static final int ID_TRANSFER = 2004;

    private NotificationHelper() {
    }

    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mgr = context.getSystemService(NotificationManager.class);
            if (mgr != null && mgr.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_status),
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(context.getString(R.string.notif_channel_status_desc));
                mgr.createNotificationChannel(channel);
            }
        }
    }

    private static void post(Context context, int id, String title, String text) {
        ensureChannel(context);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_computer)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build());
        } catch (SecurityException ignored) {
            // El usuario no concedió POST_NOTIFICATIONS; se ignora silenciosamente.
        }
    }

    public static void notifyPcOnline(Context context, String pcName) {
        post(context, ID_ONLINE + safeOffset(pcName),
                context.getString(R.string.notif_pc_online_title),
                context.getString(R.string.notif_pc_online_text, pcName));
    }

    public static void notifyPairingRequired(Context context, String pcName) {
        post(context, ID_PAIRING + safeOffset(pcName),
                context.getString(R.string.notif_pairing_title),
                context.getString(R.string.notif_pairing_text, pcName));
    }

    public static void notifyConnectionLost(Context context, String pcName) {
        post(context, ID_LOST + safeOffset(pcName),
                context.getString(R.string.notif_lost_title),
                context.getString(R.string.notif_lost_text, pcName));
    }

    public static void notifyFileTransfer(Context context, String fileName, boolean sent) {
        post(context, ID_TRANSFER,
                context.getString(sent ? R.string.notif_file_sent_title : R.string.notif_file_recv_title),
                context.getString(R.string.notif_file_text, fileName));
    }

    /** Desplazamiento estable por nombre para que cada PC tenga su propia notificación. */
    private static int safeOffset(String name) {
        if (name == null) {
            return 0;
        }
        return Math.abs(name.hashCode() % 500);
    }
}
