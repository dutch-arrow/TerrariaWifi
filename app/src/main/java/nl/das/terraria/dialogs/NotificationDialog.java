package nl.das.terraria.dialogs;


import android.content.Context;
import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

public class NotificationDialog implements DialogInterface.OnClickListener {

    private AlertDialog dialog;
    public boolean done = false;

    public NotificationDialog(Context context, String title, String message) {
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setMessage(message);
        b.setCancelable(false);
        b.setNeutralButton("Ok", this);
        b.setTitle(title);
        dialog = b.create();
    }

    public void show() {
        dialog.show();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        done = true;
        dialog.dismiss();
    }
}
