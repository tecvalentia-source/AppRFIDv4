package com.confa.apprfid;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class UiDialogs {

    private UiDialogs() {
    }

    public static void showOk(@NonNull Context context, @NonNull CharSequence message) {
        showOk(context, message, null);
    }

    public static void showOk(@NonNull Context context, @NonNull CharSequence message,
            @Nullable Runnable onDismissOk) {
        new MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_AppRFID_MaterialAlertDialog)
                .setMessage(message)
                .setPositiveButton(R.string.dialog_ok, (d, w) -> {
                    d.dismiss();
                    if (onDismissOk != null) {
                        onDismissOk.run();
                    }
                })
                .show();
    }

    public static void showOk(@NonNull Context context, @StringRes int messageRes) {
        showOk(context, context.getString(messageRes));
    }
}
