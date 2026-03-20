package com.confa.apprfid;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class MasterImportLoader {
    public interface MasterCallback {
        void onSuccess(MasterTableParser.ParseResult result);
        void onFailure(String message);
    }

    public interface MissingListCallback {
        void onSuccess(List<String> orderedRfidsRaw);
        void onFailure(String message);
    }

    public static void loadMasterAsync(Context ctx, Uri uri, ExecutorService ex, Handler h, MasterCallback cb) {
        // Aquí iría la lógica de lectura de Excel/CSV
    }

    public static void loadMissingListAsync(Context ctx, Uri uri, ExecutorService ex, Handler h, MissingListCallback cb) {
        // Aquí iría la lógica de lectura de faltantes
    }
}