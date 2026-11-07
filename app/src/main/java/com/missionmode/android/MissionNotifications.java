package com.missionmode.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import org.json.JSONObject;

/** Local notifications, a user-visible monitoring indicator, and an OS-managed timer. */
public final class MissionNotifications {
    public static final int MONITOR_ID=100,OPEN_ID=101,EXPIRY_ID=102,TIMER_ID=103;
    private static final String MONITOR_CHANNEL="mission_monitor_v1";
    private static final String ALERT_CHANNEL="mission_alerts_v2";
    private static final String TIMER_CHANNEL="mission_timer_v2";
    private static final String DEADLINE_CHANNEL="mission_deadline_v3";
    private MissionNotifications(){}
    public static void channels(Context c){
        NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(n==null)return;
        NotificationChannel monitor=new NotificationChannel(MONITOR_CHANNEL,"Monitoring is enabled",NotificationManager.IMPORTANCE_LOW);
        monitor.setDescription("Visible indicator for app-opening detection");n.createNotificationChannel(monitor);
        NotificationChannel alert=new NotificationChannel(ALERT_CHANNEL,"Mission prompts and time limits",NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Purpose reminders and mission expiry notifications");alert.enableVibration(true);
        n.createNotificationChannel(alert);
        NotificationChannel deadline=new NotificationChannel(DEADLINE_CHANNEL,"Time is up reminders",NotificationManager.IMPORTANCE_HIGH);
        deadline.setDescription("High importance reminders when a selected mission ends");
        deadline.enableVibration(true);
        deadline.setVibrationPattern(new long[]{0,220,150,220});
        n.createNotificationChannel(deadline);
        NotificationChannel timer=new NotificationChannel(TIMER_CHANNEL,"Live mission countdown",NotificationManager.IMPORTANCE_LOW);
        timer.setDescription("Time remaining in an active session");n.createNotificationChannel(timer);
    }
    private static PendingIntent openPI(Context c){
        Intent i=new Intent(c,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,100,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static PendingIntent action(Context c,String type,int id,String app,int minutes){
        Intent i=new Intent(c,MissionReceiver.class).setAction(type);
        if(app!=null)i.putExtra(MissionReceiver.EXTRA_APP,app);
        if(type.equals(MissionReceiver.START)){
            i.putExtra(MissionReceiver.EXTRA_MINUTES,minutes);
            i.putExtra(MissionReceiver.EXTRA_PURPOSE,MissionStore.defaultPurposeFor(c,app==null?MissionStore.selectedApp(c):app));
        }
        if(type.equals(MissionReceiver.EXTEND))i.putExtra(MissionReceiver.EXTRA_MINUTES,2);
        return PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static Notification monitor(Context c){
        channels(c);int count=MissionStore.enabledCount(c);
        return new Notification.Builder(c,MONITOR_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("Mission Mode monitoring enabled")
            .setContentText("Watching "+count+" selected app"+(count==1?"":"s")+". Tap to manage.")
            .setContentIntent(openPI(c)).setOnlyAlertOnce(true).setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE).build();
    }
    public static void prompt(Context c,String app){
        channels(c);int minutes=MissionStore.defaultMinutes(c);
        String purpose=MissionStore.defaultPurposeFor(c,app);
        Notification n=new Notification.Builder(c,ALERT_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setColor(Color.rgb(241,106,85))
            .setContentTitle(app+" opened. What's your purpose?")
            .setContentText(purpose+" · "+minutes+" minutes")
            .setStyle(new Notification.BigTextStyle().bigText("Start a "+minutes+"-minute mission, or tap Mission Mode to choose a different purpose and duration."))
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_play,"Start "+minutes+" min",action(c,MissionReceiver.START,201,app,minutes)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Not now",action(c,MissionReceiver.DISMISS,202,null,0)).build())
            .setContentIntent(openPI(c)).setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_VIBRATE)
            .setCategory(Notification.CATEGORY_REMINDER).setAutoCancel(true).build();
        notify(c,OPEN_ID,n);
    }
    public static void countdown(Context c){
        channels(c);JSONObject s=MissionStore.active(c);
        if(s==null){cancel(c,TIMER_ID);return;}
        long end=s.optLong("endAt");if(end<=0){cancel(c,TIMER_ID);return;}
        boolean expired=end<=System.currentTimeMillis();
        Notification.Builder b=new Notification.Builder(c,TIMER_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setColor(Color.rgb(47,120,102))
            .setContentTitle(expired?"Mission time is up":s.optString("app")+" mission running")
            .setContentText(s.optString("purpose")+" · "+s.optInt("manualVideos",0)+" videos manually counted")
            .setContentIntent(openPI(c)).setOnlyAlertOnce(true).setOngoing(!expired)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_save,"Finished",action(c,MissionReceiver.FINISH,203,null,0)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_add,"+2 min",action(c,MissionReceiver.EXTEND,204,null,0)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_add,"Video +1",action(c,MissionReceiver.COUNT_VIDEO,206,null,0)).build());
        if(!expired){
            b.setWhen(end).setShowWhen(true).setUsesChronometer(true);
            if(Build.VERSION.SDK_INT>=24)b.setChronometerCountDown(true);
        }else b.setShowWhen(false);
        notify(c,TIMER_ID,b.build());
    }
    public static void expiry(Context c){
        channels(c);JSONObject s=MissionStore.active(c);
        if(s==null)return;cancel(c,TIMER_ID);
        Notification n=new Notification.Builder(c,DEADLINE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setColor(Color.rgb(241,106,85))
            .setContentTitle("Your time is up. Leave or extend?")
            .setContentText(s.optString("app")+" · "+s.optString("purpose"))
            .setStyle(new Notification.BigTextStyle().bigText("Your "+s.optInt("plannedMinutes",5)+"-minute "+s.optString("app")+" session has ended. Leave the social app, extend your time, or mark the goal as completed."))
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_save,"Finished",action(c,MissionReceiver.FINISH,203,null,0)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_add,"+2 min",action(c,MissionReceiver.EXTEND,204,null,0)).build())
            .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel,"Distracted",action(c,MissionReceiver.DISTRACTED,205,null,0)).build())
            .setContentIntent(openPI(c)).setPriority(Notification.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_ALL).setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(false).build();
        notify(c,EXPIRY_ID,n);
    }
    public static void notify(Context c,int id,Notification n){
        if(!MissionStore.notificationPermission(c))return;
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(m!=null)try{m.notify(id,n);}catch(SecurityException ignored){}
    }
    public static void cancel(Context c,int id){
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(m!=null)m.cancel(id);
    }
}