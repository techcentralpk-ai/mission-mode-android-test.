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
    public static synchronized boolean markRemindedIfExpired(Context c){
        JSONObject s=active(c);
        if(s==null||s.optLong("endAt")>System.currentTimeMillis()||s.optLong("remindedAt")>0)return false;
        try{s.put("remindedAt",System.currentTimeMillis());}catch(Exception ignored){}
        putActive(c,s);return true;
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
        AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am==null)return;
        long delay=Math.max(1000,endAtWallclock-System.currentTimeMillis());
        am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,SystemClock.elapsedRealtime()+delay,alarmPI(c));
    }
    private static void cancelAlarm(Context c){AlarmManager am=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);if(am!=null)am.cancel(alarmPI(c));}
}
