package com.example.appportaldemo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;


public class StartMyActivityAtBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        // TODO: Aparentemente quando este broadcast é gerado ainda não se sabe qual o "Usuário"
        //       esta executando a ação, sendo assim sempre pede autorização para acessar a USB.
        //       ==> Precisamos descobrir uma forma de "Salvar" a permissão consedida.

        if ( Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.i("StartMyActivityAtBootReceiver", "============> ACTION_BOOT_COMPLETED received");
            Intent i = new Intent(context, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            Log.i("StartMyActivityAtBootReceiver", "============> Starting  MainActivity");
            context.startActivity(i);

            Log.i("StartMyActivityAtBootReceiver", "============> onReceive ACTION_BOOT_COMPLETED finished");
        }
    }
}
