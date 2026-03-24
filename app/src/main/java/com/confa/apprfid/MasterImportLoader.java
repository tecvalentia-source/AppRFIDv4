package com.confa.apprfid;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public final class MasterImportLoader {

    public interface MasterCallback {
        void onSuccess(MasterTableParser.ParseResult result);

        void onFailure(String message);
    }

    public interface MissingListCallback {
        void onSuccess(List<String> orderedRfidsRaw);

        void onFailure(String message);
    }

    private MasterImportLoader() {
    }

    public static void loadMasterAsync(@NonNull Context ctx, @NonNull Uri uri, @NonNull ExecutorService ex,
            @NonNull Handler h, @NonNull MasterCallback cb) {
        ex.execute(() -> {
            try {
                String displayName = queryDisplayName(ctx, uri);
                try (InputStream in = openStreamOrThrow(ctx, uri)) {
                    MasterTableParser.ParseResult result = MasterTableParser.parseMaster(in, displayName);
                    h.post(() -> cb.onSuccess(result));
                }
            } catch (MasterTableParser.ParseException e) {
                h.post(() -> cb.onFailure(e.getMessage() != null ? e.getMessage()
                        : ctx.getString(R.string.import_format_error)));
            } catch (SecurityException e) {
                h.post(() -> cb.onFailure(ctx.getString(R.string.import_permission_denied)));
            } catch (IOException e) {
                h.post(() -> cb.onFailure(ctx.getString(R.string.import_io_error)));
            } catch (Exception e) {
                h.post(() -> cb.onFailure(ctx.getString(R.string.import_format_error)));
            }
        });
    }

    public static void loadMissingListAsync(@NonNull Context ctx, @NonNull Uri uri, @NonNull ExecutorService ex,
            @NonNull Handler h, @NonNull MissingListCallback cb) {
        ex.execute(() -> {
            try {
                String displayName = queryDisplayName(ctx, uri);
                try (InputStream in = openStreamOrThrow(ctx, uri)) {
                    List<String> rfids = MasterTableParser.parseMissingList(in, displayName);
                    h.post(() -> cb.onSuccess(new ArrayList<>(rfids)));
                }
            } catch (MasterTableParser.ParseException e) {
                h.post(() -> cb.onFailure(e.getMessage() != null ? e.getMessage()
                        : ctx.getString(R.string.import_format_error)));
            } catch (SecurityException e) {
                h.post(() -> cb.onFailure(ctx.getString(R.string.import_permission_denied)));
            } catch (IOException e) {
                h.post(() -> cb.onFailure(ctx.getString(R.string.import_io_error)));
            } catch (Exception e) {
                h.post(() -> cb.onFailure(ctx.getString(R.string.import_format_error)));
            }
        });
    }

    @NonNull
    private static InputStream openStreamOrThrow(@NonNull Context ctx, @NonNull Uri uri) throws IOException {
        InputStream in = ctx.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("openInputStream null");
        }
        return in;
    }

    @Nullable
    private static String queryDisplayName(@NonNull Context ctx, @NonNull Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
