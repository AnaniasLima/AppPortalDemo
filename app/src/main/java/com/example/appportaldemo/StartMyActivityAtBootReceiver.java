package com.example.appportaldemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class StartMyActivityAtBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if ( Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("StartMyActivityAtBootReceiver", "============> ACTION_BOOT_COMPLETED received");

            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.putExtra("CHAMANDO_DO_BOOT", 1);

            Log.i("StartMyActivityAtBootReceiver", "============> Starting  MainActivity");
            context.startActivity(i);

            Log.i("StartMyActivityAtBootReceiver", "============> onReceive ACTION_BOOT_COMPLETED finished");
        }
    }
}
