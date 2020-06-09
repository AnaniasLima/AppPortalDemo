package com.example.appportaldemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ScreenReceiver  extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println(intent.getAction());
        if (intent.getAction().equals(Intent.ACTION_USER_PRESENT))
        {
            Log.i("StartMyActivityAtBootReceiver", "============> ACTION_USER_PRESENT received");
            Intent intent1 = new Intent(context,MainActivity.class);
            intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Log.i("StartMyActivityAtBootReceiver", "============> Starting  MainActivity");
            context.startActivity(intent1);
            Log.i("StartMyActivityAtBootReceiver", "============> onReceive ACTION_USER_PRESENT finished");

        }
    }

}
