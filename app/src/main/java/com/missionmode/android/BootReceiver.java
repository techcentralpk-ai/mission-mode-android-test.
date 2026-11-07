package com.missionmode.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONObject;

/** Only resumes monitoring if the user explicitly enabled it before restart/update. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(i==null)return;
        String action=i.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action))return;
        JSONObject active=MissionStore.active(c);
        if(active!=null)MissionStore.scheduleAlarm(c,active.optLong("endAt"));
        if(!MissionStore.wantsMonitoring(c)||!MissionStore.monitorReady(c))return;
        try{
            Intent service=new Intent(c,MonitorService.class);
            if(Build.VERSION.SDK_INT>=26)c.startForegroundService(service);
            else c.startService(service);
        }catch(Exception ignored){
            // Android device policy may block background startup. Open the app to re-enable.
        }
    }
}