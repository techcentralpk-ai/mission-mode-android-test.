package com.missionmode.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Explicit on-device controls for reminders and active missions. */
public class MissionReceiver extends BroadcastReceiver {
    public static final String START="com.missionmode.START_MISSION";
    public static final String EXTRA_MINUTES="mission_minutes";
    public static final String EXTRA_APP="mission_app";
    public static final String EXTRA_PURPOSE="mission_purpose";
    public static final String DISMISS="com.missionmode.DISMISS";
    public static final String EXPIRE="com.missionmode.EXPIRE";
    public static final String EXTEND="com.missionmode.EXTEND";
    public static final String FINISH="com.missionmode.FINISH";
    public static final String DISTRACTED="com.missionmode.DISTRACTED";
    public static final String COUNT_VIDEO="com.missionmode.COUNT_VIDEO";
    @Override public void onReceive(Context c,Intent intent){
        if(intent==null)return;String action=intent.getAction();
        if(START.equals(action)){
            int minutes=MissionStore.validatedMinutes(intent.getIntExtra(EXTRA_MINUTES,MissionStore.defaultMinutes(c)));
            String app=intent.getStringExtra(EXTRA_APP);
            if(app==null)app=MissionStore.selectedApp(c);
            String purpose=intent.getStringExtra(EXTRA_PURPOSE);
            if(purpose==null)purpose=MissionStore.defaultPurposeFor(c,app);
            MissionStore.startMission(c,"app_open_prompt",minutes,app,purpose);
            MissionNotifications.cancel(c,MissionNotifications.OPEN_ID);
            MissionNotifications.countdown(c);
            MonitorService.dismissOverlay();
        }else if(COUNT_VIDEO.equals(action)){
            if(MissionStore.changeManualVideoCount(c,1))MissionNotifications.countdown(c);
        }else if(DISMISS.equals(action)){
            MissionNotifications.cancel(c,MissionNotifications.OPEN_ID);MonitorService.dismissOverlay();
        }else if(EXPIRE.equals(action)){
            if(MissionStore.markRemindedIfExpired(c)){
                MissionNotifications.expiry(c);MonitorService.showExpiryOverlayIfVisible();
            }
        }else if(EXTEND.equals(action)){
            int min=MissionStore.validatedMinutes(intent.getIntExtra(EXTRA_MINUTES,2));
            if(MissionStore.extendMission(c,min)){
                MissionNotifications.cancel(c,MissionNotifications.EXPIRY_ID);
                MissionNotifications.countdown(c);
            }
            MonitorService.dismissOverlay();
        }else if(FINISH.equals(action)||DISTRACTED.equals(action)){
            if(MissionStore.finishMission(c,FINISH.equals(action)?"achieved":"distracted")){
                MissionNotifications.cancel(c,MissionNotifications.EXPIRY_ID);
                MissionNotifications.cancel(c,MissionNotifications.TIMER_ID);
            }
            MonitorService.dismissOverlay();
        }
    }
}