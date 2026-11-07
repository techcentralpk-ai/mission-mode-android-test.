package com.missionmode.android;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.os.SystemClock;
import android.content.Intent;
import android.app.AppOpsManager;
import android.os.Process;
import android.app.NotificationManager;
import android.provider.Settings;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;

/** Session elapsed time is not the same as verified foreground usage. Everything stays on device. */
public final class MissionStore {
    private static final String PREFERENCES="mission_mode_native_v1";
    private MissionStore(){}
    static SharedPreferences prefs(Context c){return c.getApplicationContext().getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE);}
    public static boolean wantsMonitoring(Context c){return prefs(c).getBoolean("monitorWanted",false);}
    public static void setWantsMonitoring(Context c,boolean enabled){prefs(c).edit().putBoolean("monitorWanted",enabled).apply();}
    public static boolean usagePermission(Context c){
        try{
            AppOpsManager ops=(AppOpsManager)c.getSystemService(Context.APP_OPS_SERVICE);
            return ops!=null&&ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,Process.myUid(),c.getPackageName())==AppOpsManager.MODE_ALLOWED;
        }catch(Exception e){return false;}
    }
    public static boolean notificationPermission(Context c){
        NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(n==null||!n.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return false;
        return true;
    }
    public static boolean monitorReady(Context c){return usagePermission(c)&&notificationPermission(c);}
    /** Optional Android special access; user must approve in system settings. */
    public static boolean canUsePreciseAlarm(Context c){
        if(Build.VERSION.SDK_INT<31)return true;
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return am!=null&&am.canScheduleExactAlarms();
    }
    /** A saved status indicator helps distinguish system service stops from usage-detection errors. */
    public static void setHeartbeat(Context c,long now){prefs(c).edit().putLong("heartbeat",now).apply();}
    public static long lastHeartbeat(Context c){return prefs(c).getLong("heartbeat",0);}
    public static synchronized int manualVideoCount(Context c){JSONObject x=active(c);return x==null?0:x.optInt("manualVideos",0);}
    public static synchronized boolean changeManualVideoCount(Context c,int delta){
        JSONObject s=active(c);if(s==null)return false;
        int count=Math.max(0,Math.min(10000,s.optInt("manualVideos",0)+delta));
        try{s.put("manualVideos",count);s.put("videoCountSource","manually_tallied");putActive(c,s);return true;}
        catch(Exception ignored){return false;}
    }
    /** On-device diagnostics for user-visible deadline reminders. */
    public static void recordAlarmFired(Context c){
        prefs(c).edit().putLong("lastAlarmFired",System.currentTimeMillis()).apply();
    }
    public static long lastAlarmFired(Context c){return prefs(c).getLong("lastAlarmFired",0);}
    public static void recordNotificationResult(Context c,String result){
        prefs(c).edit().putString("lastNotificationResult",result)
            .putLong("lastNotificationAttempt",System.currentTimeMillis()).apply();
    }
    public static String lastNotificationResult(Context c){
        return prefs(c).getString("lastNotificationResult","No time-up alert attempted");
    }
    public static long lastNotificationAttempt(Context c){return prefs(c).getLong("lastNotificationAttempt",0);}
    public static long lastScheduledDeadline(Context c){return prefs(c).getLong("lastScheduledDeadline",0);}
    public static String lastAlarmScheduleStatus(Context c){return prefs(c).getString("alarmScheduleStatus","Not scheduled");}
    /** Explicit, opt-in experimental Instagram Reel transition estimate. */
    public static boolean reelDetectionConsent(Context c){
        return prefs(c).getBoolean("reelExperimentalOptIn",false);
    }
    public static void setReelDetectionConsent(Context c,boolean enable){
        prefs(c).edit().putBoolean("reelExperimentalOptIn",enable).commit();
    }
    public static synchronized boolean recordEstimatedReelTransition(Context c){
        if(!reelDetectionConsent(c))return false;
        JSONObject session=active(c);
        if(session==null||!"Instagram".equals(session.optString("app"))
                ||session.optLong("endAt")<=System.currentTimeMillis())return false;
        try{
            session.put("estimatedReelTransitions",Math.min(10000,session.optInt("estimatedReelTransitions",0)+1));
            session.put("reelEstimateSource","accessibility_scroll_heuristic");
            putActive(c,session);
            return true;
        }catch(Exception e){return false;}
    }
    public static boolean wantsOverlay(Context c){return prefs(c).getBoolean("overlayWanted",false)&&PromptOverlay.allowed(c);}
    public static void setWantsOverlay(Context c,boolean yes){prefs(c).edit().putBoolean("overlayWanted",yes).apply();}
    public static boolean appEnabled(Context c,String app){
        return prefs(c).getBoolean("track_"+app,"Instagram".equals(app));
    }
    public static void setAppEnabled(Context c,String app,boolean enabled){
        prefs(c).edit().putBoolean("track_"+TrackedApps.byName(app).name,enabled).apply();
    }
    public static int enabledCount(Context c){int count=0;for(TrackedApps.Entry a:TrackedApps.ALL)if(appEnabled(c,a.name))count++;return count;}
    public static int validatedMinutes(int n){return Math.max(1,Math.min(180,n));}
    public static int defaultMinutes(Context c){return validatedMinutes(prefs(c).getInt("defaultMinutes",5));}
    public static void setDefaultMinutes(Context c,int minutes){prefs(c).edit().putInt("defaultMinutes",validatedMinutes(minutes)).apply();}
    public static String selectedApp(Context c){return TrackedApps.byName(prefs(c).getString("selectedApp","Instagram")).name;}
    public static void setSelectedApp(Context c,String app){prefs(c).edit().putString("selectedApp",TrackedApps.byName(app).name).apply();}
    public static String selectedPurpose(Context c){return TrackedApps.cleanPurpose(prefs(c).getString("selectedPurpose",TrackedApps.PURPOSES[0]));}
    public static void setSelectedPurpose(Context c,String purpose){prefs(c).edit().putString("selectedPurpose",TrackedApps.cleanPurpose(purpose)).apply();}
    public static String defaultPurposeFor(Context c,String app){return TrackedApps.cleanPurpose(prefs(c).getString("purpose_"+TrackedApps.byName(app).name,TrackedApps.PURPOSES[0]));}
    public static void setDefaultPurposeFor(Context c,String app,String purpose){prefs(c).edit().putString("purpose_"+TrackedApps.byName(app).name,TrackedApps.cleanPurpose(purpose)).apply();}
    public static synchronized JSONObject active(Context c){
        try{String s=prefs(c).getString("active","");return s.isEmpty()?null:new JSONObject(s);}catch(Exception e){return null;}
    }
    private static void putActive(Context c,JSONObject s){prefs(c).edit().putString("active",s==null?"":s.toString()).commit();}
    public static synchronized boolean startMission(Context c,String source){
        return startMission(c,source,defaultMinutes(c),selectedApp(c),selectedPurpose(c));
    }
    public static synchronized boolean startMission(Context c,String source,int minutes){
        return startMission(c,source,minutes,selectedApp(c),selectedPurpose(c));
    }
    public static synchronized boolean startMission(Context c,String source,int duration,String appName,String purpose){
        if(active(c)!=null)return false;
        int minutes=validatedMinutes(duration);
        String app=TrackedApps.byName(appName).name;
        String intent=TrackedApps.cleanPurpose(purpose);
        long now=System.currentTimeMillis();
        JSONObject session=new JSONObject();
        try{
            session.put("id",String.valueOf(now));session.put("purpose",intent);session.put("app",app);
            session.put("startAt",now);session.put("endAt",now+(long)minutes*60000L);
            session.put("plannedMinutes",minutes);session.put("extensions",0);
            session.put("manualVideos",0);session.put("videoCountSource","manually_tallied");
            session.put("estimatedReelTransitions",0);
            session.put("reelEstimateSource","accessibility_scroll_heuristic");
            session.put("source",source);session.put("remindedAt",0);
        }catch(Exception e){return false;}
        putActive(c,session);
        setSelectedApp(c,app);setSelectedPurpose(c,intent);setDefaultPurposeFor(c,app,intent);setDefaultMinutes(c,minutes);
        scheduleAlarm(c,session.optLong("endAt"));return true;
    }
    public static synchronized boolean extendMission(Context c,int value){
        JSONObject s=active(c);if(s==null)return false;
        int minutes=validatedMinutes(value);
        try{
            long newEnd=Math.max(System.currentTimeMillis(),s.optLong("endAt"))+(long)minutes*60000L;
            s.put("endAt",newEnd);s.put("extensions",s.optInt("extensions")+1);s.put("remindedAt",0);
            putActive(c,s);scheduleAlarm(c,newEnd);return true;
        }catch(Exception e){return false;}
    }
    /**
     * Only store remindedAt once Android accepted the deadline notification.
     * This is shared by the alarm receiver, monitor service and foreground UI.
     * Posting is not proof of a visible heads-up banner; Android owns presentation.
     */
    public static synchronized boolean markRemindedIfExpired(Context c){
        JSONObject session=active(c);
        if(session==null||session.optLong("endAt")>System.currentTimeMillis()
                ||session.optLong("remindedAt")>0)return false;
        if(!MissionNotifications.canPostDeadline(c)){
            recordNotificationResult(c,"Blocked: notifications or time-up channel disabled");
            return false;
        }
        boolean posted=MissionNotifications.expiry(c);
        if(!posted)return false;
        try{session.put("remindedAt",System.currentTimeMillis());}
        catch(Exception ignored){return false;}
        putActive(c,session);
        return true;
    }
    public static synchronized boolean finishMission(Context c,String outcome){
        JSONObject s=active(c);if(s==null)return false;
        try{
            long now=System.currentTimeMillis();
            s.put("finishedAt",now);s.put("outcome",outcome);
            s.put("missionElapsedSeconds",Math.max(0,(now-s.optLong("startAt"))/1000));
            JSONArray old=history(c);JSONArray updated=new JSONArray();updated.put(s);
            for(int i=0;i<Math.min(499,old.length());i++){JSONObject row=old.optJSONObject(i);if(row!=null)updated.put(row);}
            prefs(c).edit().putString("history",updated.toString()).putString("active","").commit();
            cancelAlarm(c);return true;
        }catch(Exception e){return false;}
    }
    public static synchronized JSONArray history(Context c){try{return new JSONArray(prefs(c).getString("history","[]"));}catch(Exception e){return new JSONArray();}}
    public static synchronized void clearHistory(Context c){prefs(c).edit().remove("history").apply();}
    public static synchronized void cancelActive(Context c){putActive(c,null);cancelAlarm(c);}
    public static long lastPrompted(Context c,String app){return prefs(c).getLong("prompt_"+TrackedApps.byName(app).name,0);}
    public static void setLastPrompted(Context c,String app,long now){prefs(c).edit().putLong("prompt_"+TrackedApps.byName(app).name,now).apply();}
    public static long lastDetection(Context c){return prefs(c).getLong("lastDetection",0);}
    public static void setLastDetection(Context c,long now){prefs(c).edit().putLong("lastDetection",now).apply();}
    private static PendingIntent alarmPI(Context c){
        Intent i=new Intent(c,MissionReceiver.class).setAction(MissionReceiver.EXPIRE);
        return PendingIntent.getBroadcast(c,321,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void scheduleAlarm(Context c,long endAtWallclock){
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(am==null){
            prefs(c).edit().putString("alarmScheduleStatus","Alarm service unavailable").apply();
            return;
        }
        prefs(c).edit().putLong("lastScheduledDeadline",endAtWallclock).apply();
        long delay=Math.max(1000,endAtWallclock-System.currentTimeMillis());
        long when=SystemClock.elapsedRealtime()+delay;
        // Exact delivery is optional and requires user-approved Alarms & reminders access on API 31+.
        // If declined, use Android's inexact idle-safe scheduler and communicate that it can be late.
        try{
            boolean precise=canUsePreciseAlarm(c);
            if(precise)am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,when,alarmPI(c));
            else am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,when,alarmPI(c));
            prefs(c).edit().putString("alarmScheduleStatus",precise?"Precise alarm scheduled":"Inexact alarm scheduled; may be late").apply();
        }catch(SecurityException denied){
            try{
                am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,when,alarmPI(c));
                prefs(c).edit().putString("alarmScheduleStatus","Precise permission refused; inexact fallback").apply();
            }catch(Exception e){
                prefs(c).edit().putString("alarmScheduleStatus","Alarm rejected: "+e.getClass().getSimpleName()).apply();
            }
        }catch(Exception e){
            prefs(c).edit().putString("alarmScheduleStatus","Alarm failed: "+e.getClass().getSimpleName()).apply();
        }
    }
    private static void cancelAlarm(Context c){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)am.cancel(alarmPI(c));}
}