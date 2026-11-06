package com.missionmode.android;

import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import org.json.JSONObject;

/** User-enabled foreground monitor; no Accessibility or other-app screen reading. */
public class MonitorService extends Service {
    public static volatile boolean running=false;
    private static volatile MonitorService instance;
    private Handler handler;
    private PromptOverlay overlay;
    private long lastEventTimestamp;
    private long lastPausedAt;
    private String lastPausedPackage="";
    private String foregroundPackage="";
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            if(handler==null)return;
            if(!MissionStore.wantsMonitoring(MonitorService.this)||!MissionStore.monitorReady(MonitorService.this)){
                stopSelf();return;
            }
            try{scanRecentEvents();}catch(Exception ignored){}
            if(MissionStore.markRemindedIfExpired(MonitorService.this)){
                MissionNotifications.expiry(MonitorService.this);
                showExpiryIfVisible();
            }
            JSONObject s=MissionStore.active(MonitorService.this);
            if(s!=null&&s.optLong("endAt")<=System.currentTimeMillis()&&s.optLong("remindedAt")>0
                    &&overlay!=null&&!overlay.isShowing())showExpiryIfVisible();
            PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
            handler.postDelayed(this,power!=null&&!power.isInteractive()?5000:1200);
        }
    };
    @Override public void onCreate(){
        super.onCreate();handler=new Handler(Looper.getMainLooper());
        overlay=new PromptOverlay(this);instance=this;
    }
    @Override public int onStartCommand(Intent i,int flags,int startId){
        if(!MissionStore.wantsMonitoring(this)||!MissionStore.monitorReady(this)){
            stopSelf();return START_NOT_STICKY;
        }
        MissionNotifications.channels(this);
        try{
            if(Build.VERSION.SDK_INT>=34)startForeground(MissionNotifications.MONITOR_ID,MissionNotifications.monitor(this),ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(MissionNotifications.MONITOR_ID,MissionNotifications.monitor(this));
        }catch(Exception ex){stopSelf();return START_NOT_STICKY;}
        running=true;
        if(lastEventTimestamp==0)lastEventTimestamp=System.currentTimeMillis()-4000;
        handler.removeCallbacks(tick);handler.post(tick);
        MissionNotifications.countdown(this);
        return START_STICKY;
    }
    private void scanRecentEvents(){
        long now=System.currentTimeMillis();
        UsageStatsManager usage=(UsageStatsManager)getSystemService(USAGE_STATS_SERVICE);
        if(usage==null)return;
        UsageEvents evts=usage.queryEvents(Math.max(now-20000,lastEventTimestamp+1),now);
        if(evts==null)return;
        UsageEvents.Event evt=new UsageEvents.Event();
        while(evts.hasNextEvent()){
            evts.getNextEvent(evt);
            long ts=evt.getTimeStamp();if(ts<=lastEventTimestamp)continue;
            int type=evt.getEventType();String pkg=evt.getPackageName();
            if(type==UsageEvents.Event.ACTIVITY_RESUMED){
                TrackedApps.Entry target=TrackedApps.byPackage(pkg);
                if(target!=null){
                    boolean newlyEntered=!pkg.equals(foregroundPackage)
                            ||(pkg.equals(lastPausedPackage)&&ts-lastPausedAt>1500);
                    foregroundPackage=pkg;
                    if(MissionStore.appEnabled(this,target.name) && newlyEntered)onAppOpened(target);
                    else if(!MissionStore.appEnabled(this,target.name)&&overlay!=null)overlay.hide();
                }else if(pkg!=null){
                    foregroundPackage=pkg;
                    if(overlay!=null)overlay.hide();
                }
            }else if(type==UsageEvents.Event.ACTIVITY_PAUSED && TrackedApps.byPackage(pkg)!=null){
                lastPausedPackage=pkg;lastPausedAt=ts;
            }
            lastEventTimestamp=Math.max(lastEventTimestamp,ts);
        }
    }
    private void onAppOpened(TrackedApps.Entry target){
        long now=System.currentTimeMillis();MissionStore.setLastDetection(this,now);
        JSONObject s=MissionStore.active(this);
        if(s!=null){
            if(s.optLong("endAt")<=now && s.optLong("remindedAt")>0)showExpiryIfVisible();
            return;
        }
        // Avoid double prompts during rapid internal activity changes.
        if(now-MissionStore.lastPrompted(this,target.name)<1800)return;
        MissionStore.setLastPrompted(this,target.name,now);
        MissionNotifications.prompt(this,target.name);
        if(overlay!=null&&MissionStore.wantsOverlay(this))overlay.show("open",target.name);
    }
    private void showExpiryIfVisible(){
        JSONObject active=MissionStore.active(this);
        if(active==null)return;
        TrackedApps.Entry target=TrackedApps.byPackage(foregroundPackage);
        if(target!=null&&target.name.equals(active.optString("app"))&&overlay!=null&&MissionStore.wantsOverlay(this)){
            overlay.show("expiry",target.name);
        }
    }
    public static void showExpiryOverlayIfVisible(){MonitorService s=instance;if(s!=null&&s.handler!=null)s.handler.post(s::showExpiryIfVisible);}
    public static void dismissOverlay(){MonitorService s=instance;if(s!=null&&s.handler!=null)s.handler.post(()->{if(s.overlay!=null)s.overlay.hide();});}
    @Override public void onDestroy(){
        running=false;instance=null;
        if(handler!=null){handler.removeCallbacks(tick);handler=null;}
        if(overlay!=null){overlay.hide();overlay=null;}
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
