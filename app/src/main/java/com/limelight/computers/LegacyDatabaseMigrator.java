package com.limelight.computers;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

/**
 * Consolidates all legacy database migration logic (V1, V2, and V3)
 * into a single helper class, reducing code duplication.
 */
public class LegacyDatabaseMigrator {
    private static final String COMPUTER_TABLE_NAME = "Computers";

    // --- V1 Helper Methods & Constants ---
    private static final String COMPUTER_DB_V1 = "computers.db";
    private static final String ADDRESS_PREFIX_V1 = "ADDRESS_PREFIX__";

    private static ComputerDetails getComputerFromCursorV1(Cursor c) {
        ComputerDetails details = new ComputerDetails();
        details.name = c.getString(0);
        details.uuid = c.getString(1);

        try {
            details.localAddress = new ComputerDetails.AddressTuple(InetAddress.getByAddress(c.getBlob(2)).getHostAddress(), NvHTTP.DEFAULT_HTTP_PORT);
            LimeLog.warning("DB: Legacy local address for " + details.name);
        } catch (UnknownHostException e) {
            String stringData = c.getString(2);
            if (stringData.startsWith(ADDRESS_PREFIX_V1)) {
                details.localAddress = new ComputerDetails.AddressTuple(c.getString(2).substring(ADDRESS_PREFIX_V1.length()), NvHTTP.DEFAULT_HTTP_PORT);
            } else {
                LimeLog.severe("DB: Corrupted local address for " + details.name);
            }
        }

        try {
            details.remoteAddress = new ComputerDetails.AddressTuple(InetAddress.getByAddress(c.getBlob(3)).getHostAddress(), NvHTTP.DEFAULT_HTTP_PORT);
            LimeLog.warning("DB: Legacy remote address for " + details.name);
        } catch (UnknownHostException e) {
            String stringData = c.getString(3);
            if (stringData.startsWith(ADDRESS_PREFIX_V1)) {
                details.remoteAddress = new ComputerDetails.AddressTuple(c.getString(3).substring(ADDRESS_PREFIX_V1.length()), NvHTTP.DEFAULT_HTTP_PORT);
            } else {
                LimeLog.severe("DB: Corrupted remote address for " + details.name);
            }
        }

        details.manualAddress = details.remoteAddress;
        details.macAddress = c.getString(4);
        details.state = ComputerDetails.State.UNKNOWN;

        return details;
    }

    private static List<ComputerDetails> getAllComputersV1(SQLiteDatabase db) {
        try (final Cursor c = db.rawQuery("SELECT * FROM " + COMPUTER_TABLE_NAME, null)) {
            LinkedList<ComputerDetails> computerList = new LinkedList<>();
            while (c.moveToNext()) {
                ComputerDetails details = getComputerFromCursorV1(c);
                if (details.uuid != null) {
                    computerList.add(details);
                }
            }
            return computerList;
        }
    }

    public static List<ComputerDetails> migrateV1(Context c) {
        try (final SQLiteDatabase computerDb = SQLiteDatabase.openDatabase(
                c.getDatabasePath(COMPUTER_DB_V1).getPath(),
                null, SQLiteDatabase.OPEN_READONLY)
        ) {
            return getAllComputersV1(computerDb);
        } catch (SQLiteException e) {
            return new LinkedList<>();
        } finally {
            c.deleteDatabase(COMPUTER_DB_V1);
        }
    }


    // --- V2 Helper Methods & Constants ---
    private static final String COMPUTER_DB_V2 = "computers2.db";

    private static ComputerDetails getComputerFromCursorV2(Cursor c) {
        ComputerDetails details = new ComputerDetails();
        details.uuid = c.getString(0);
        details.name = c.getString(1);
        details.localAddress = new ComputerDetails.AddressTuple(c.getString(2), NvHTTP.DEFAULT_HTTP_PORT);
        details.remoteAddress = new ComputerDetails.AddressTuple(c.getString(3), NvHTTP.DEFAULT_HTTP_PORT);
        details.manualAddress = new ComputerDetails.AddressTuple(c.getString(4), NvHTTP.DEFAULT_HTTP_PORT);
        details.macAddress = c.getString(5);

        if (c.getColumnCount() >= 7) {
            try {
                byte[] derCertData = c.getBlob(6);
                if (derCertData != null) {
                    details.serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                            .generateCertificate(new ByteArrayInputStream(derCertData));
                }
            } catch (CertificateException e) {
                e.printStackTrace();
            }
        }

        details.state = ComputerDetails.State.UNKNOWN;
        return details;
    }

    private static List<ComputerDetails> getAllComputersV2(SQLiteDatabase db) {
        try (final Cursor c = db.rawQuery("SELECT * FROM " + COMPUTER_TABLE_NAME, null)) {
            LinkedList<ComputerDetails> computerList = new LinkedList<>();
            while (c.moveToNext()) {
                ComputerDetails details = getComputerFromCursorV2(c);
                if (details.uuid != null) {
                    computerList.add(details);
                }
            }
            return computerList;
        }
    }

    public static List<ComputerDetails> migrateV2(Context c) {
        try (final SQLiteDatabase computerDb = SQLiteDatabase.openDatabase(
                c.getDatabasePath(COMPUTER_DB_V2).getPath(),
                null, SQLiteDatabase.OPEN_READONLY)
        ) {
            return getAllComputersV2(computerDb);
        } catch (SQLiteException e) {
            return new LinkedList<>();
        } finally {
            c.deleteDatabase(COMPUTER_DB_V2);
        }
    }


    // --- V3 Helper Methods & Constants ---
    private static final String COMPUTER_DB_V3 = "computers3.db";
    private static final char ADDRESS_DELIMITER_V3 = ';';
    private static final char PORT_DELIMITER_V3 = '_';

    private static String readNonEmptyString(String input) {
        return input.isEmpty() ? null : input;
    }

    private static ComputerDetails.AddressTuple splitAddressToTuple(String input) {
        if (input == null) {
            return null;
        }
        String[] parts = input.split("" + PORT_DELIMITER_V3, -1);
        if (parts.length == 1) {
            return new ComputerDetails.AddressTuple(parts[0], NvHTTP.DEFAULT_HTTP_PORT);
        } else {
            return new ComputerDetails.AddressTuple(parts[0], Integer.parseInt(parts[1]));
        }
    }

    private static ComputerDetails getComputerFromCursorV3(Cursor c) {
        ComputerDetails details = new ComputerDetails();
        details.uuid = c.getString(0);
        details.name = c.getString(1);

        String[] addresses = c.getString(2).split("" + ADDRESS_DELIMITER_V3, -1);
        details.localAddress = splitAddressToTuple(readNonEmptyString(addresses[0]));
        details.remoteAddress = splitAddressToTuple(readNonEmptyString(addresses[1]));
        details.manualAddress = splitAddressToTuple(readNonEmptyString(addresses[2]));
        details.ipv6Address = splitAddressToTuple(readNonEmptyString(addresses[3]));

        if (details.remoteAddress != null) {
            details.externalPort = details.remoteAddress.port;
        } else {
            details.externalPort = NvHTTP.DEFAULT_HTTP_PORT;
        }

        details.macAddress = c.getString(3);

        try {
            byte[] derCertData = c.getBlob(4);
            if (derCertData != null) {
                details.serverCert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new ByteArrayInputStream(derCertData));
            }
        } catch (CertificateException e) {
            e.printStackTrace();
        }

        details.state = ComputerDetails.State.UNKNOWN;
        return details;
    }

    private static List<ComputerDetails> getAllComputersV3(SQLiteDatabase db) {
        try (final Cursor c = db.rawQuery("SELECT * FROM " + COMPUTER_TABLE_NAME, null)) {
            LinkedList<ComputerDetails> computerList = new LinkedList<>();
            while (c.moveToNext()) {
                ComputerDetails details = getComputerFromCursorV3(c);
                if (details.uuid != null) {
                    computerList.add(details);
                }
            }
            return computerList;
        }
    }

    public static List<ComputerDetails> migrateV3(Context c) {
        try (final SQLiteDatabase computerDb = SQLiteDatabase.openDatabase(
                c.getDatabasePath(COMPUTER_DB_V3).getPath(),
                null, SQLiteDatabase.OPEN_READONLY)
        ) {
            return getAllComputersV3(computerDb);
        } catch (SQLiteException e) {
            return new LinkedList<>();
        } finally {
            c.deleteDatabase(COMPUTER_DB_V3);
        }
    }
}
