package com.dail8859.githubreleasehistory;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d("TAG", "onReceive: bootup");

        //AlarmManager mgr = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        //Intent i = new Intent(context, TestService.class);
        //PendingIntent pi = PendingIntent.getService(context, 0, i, 0);
        //mgr.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 30000, pi);
    }
}
