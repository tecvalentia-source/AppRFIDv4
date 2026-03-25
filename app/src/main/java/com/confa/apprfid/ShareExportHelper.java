package com.confa.apprfid;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Comparte un .xls guardado en MediaStore (mismo flujo que exportación en {@link MainActivity}).
 */
public final class ShareExportHelper {

    private static final String TAG = "ShareExportHelper";

    private ShareExportHelper() {
    }

    public static void shareSingleSpreadsheet(@NonNull Activity activity, @NonNull Uri uri,
            @NonNull String subject, @NonNull String chooserTitle) {
        int readFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/vnd.ms-excel");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.setClipData(ClipData.newUri(activity.getContentResolver(), subject, uri));
        send.addFlags(readFlags);
        Intent chooser = Intent.createChooser(send, chooserTitle);
        chooser.addFlags(readFlags);
        try {
            activity.startActivity(chooser);
        } catch (android.content.ActivityNotFoundException ex) {
            Log.w(TAG, "share", ex);
            UiDialogs.showOk(activity, activity.getString(R.string.export_io_error));
        }
    }
}
