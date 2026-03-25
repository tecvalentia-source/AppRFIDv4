package com.confa.apprfid;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Escribe archivos visibles en el explorador del usuario bajo {@code Descargas/ConfaAppRFID/}
 * usando {@link MediaStore.Downloads} (sin permisos de almacenamiento en API 29+).
 */
public final class PublicDownloadsExport {

    /** Subcarpeta dentro de Descargas (pública). */
    public static final String DOWNLOADS_SUBFOLDER = "ConfaAppRFID";

    private static final String MIME_SPREADSHEET = "application/vnd.ms-excel";

    private PublicDownloadsExport() {
    }

    @FunctionalInterface
    public interface SpreadsheetWriter {
        void write(@NonNull OutputStream out) throws IOException;
    }

    /**
     * Crea el archivo en Descargas, escribe el contenido y lo publica ({@code IS_PENDING = 0}).
     *
     * @return {@code content://} URI para compartir con {@code FLAG_GRANT_READ_URI_PERMISSION}.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    @NonNull
    public static Uri insertWriteAndPublish(@NonNull Context context,
            @NonNull String displayName,
            @NonNull SpreadsheetWriter writer) throws IOException {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, MIME_SPREADSHEET);
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOADS_SUBFOLDER);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("No se pudo crear entrada en Descargas (MediaStore)");
        }
        try {
            OutputStream out = resolver.openOutputStream(uri);
            if (out == null) {
                throw new IOException("Salida nula al escribir en Descargas");
            }
            writer.write(out);
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            int updated = resolver.update(uri, values, null, null);
            if (updated < 1) {
                throw new IOException("No se pudo publicar el archivo en Descargas");
            }
        } catch (IOException e) {
            try {
                resolver.delete(uri, null, null);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
        return uri;
    }
}
