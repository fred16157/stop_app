package com.stop.stop_app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopServiceBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        System.out.println("Stop broadcast called");
        context.stopService(new Intent(context, MainService.class));
    }
}
