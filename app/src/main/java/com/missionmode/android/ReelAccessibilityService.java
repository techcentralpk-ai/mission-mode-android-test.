package com.missionmode.android;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.ArrayDeque;
import java.util.Locale;

/**
 * EXPERIMENTAL, opt-in Instagram-only Reel scroll-transition estimator.
 *
 * No screenshots, network calls, text transcription, swipe injection, or event log.
 * The Android OS permission is broader than the event filter in this code.
 * Counter is NOT a count of unique videos watched or completed.
 */
public final class ReelAccessibilityService extends AccessibilityService {
    private static final String INSTAGRAM="com.instagram.android";
    private static final long DEBOUNCE_MS=1100;
    private boolean reelsTabOpen=false;
    private boolean commentsPanelOpen=false;
    private long lastTransition=0;
    private long lastTabCheck=0;

    @Override protected void onServiceConnected(){
        super.onServiceConnected();
        reelsTabOpen=false;
        commentsPanelOpen=false;
        lastTransition=0;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null || event.getPackageName()==null || !INSTAGRAM.contentEquals(event.getPackageName()))return;
        if(!MissionStore.reelDetectionConsent(this))return;
        JSONObject session=MissionStore.active(this);
        if(session==null || !"Instagram".equals(session.optString("app"))
                || session.optLong("endAt")<=System.currentTimeMillis())return;

        int type=event.getEventType();
        if(type==AccessibilityEvent.TYPE_VIEW_CLICKED){
            AccessibilityNodeInfo source=event.getSource();
            if(source!=null){
                String label=shortControlLabel(source);
                if(isReelsTab(label)){reelsTabOpen=true;commentsPanelOpen=false;}
                else if(isOtherTab(label)){reelsTabOpen=false;commentsPanelOpen=false;}
                else if(isCommentsControl(label))commentsPanelOpen=true;
            }
            return;
        }
        if(type==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
            // Do not infer Reels merely because the app is foreground; require tab evidence.
            updateTabFromSelectedNode();
            return;
        }
        if(type==AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED){
            // Rate limit reading the selected navigation control to avoid heavy UI traversal.
            if(SystemClock.elapsedRealtime()-lastTabCheck>2200)updateTabFromSelectedNode();
            return;
        }
        if(type!=AccessibilityEvent.TYPE_VIEW_SCROLLED)return;
        if(!reelsTabOpen || commentsPanelOpen)return;
        int delta=event.getScrollDeltaY();
        int minDistance=Math.round(70*getResources().getDisplayMetrics().density);
        if(delta<minDistance)return;
        AccessibilityNodeInfo source=event.getSource();
        if(source!=null){
            String viewId=source.getViewIdResourceName();
            String label=shortControlLabel(source);
            if((viewId!=null&&viewId.toLowerCase(Locale.US).contains("comment"))
                    || isCommentsControl(label))return;
        }
        long now=SystemClock.elapsedRealtime();
        if(now-lastTransition<DEBOUNCE_MS)return;
        lastTransition=now;
        if(MissionStore.recordEstimatedReelTransition(this)){
            MissionNotifications.countdown(this);
        }
    }

    private void updateTabFromSelectedNode(){
        lastTabCheck=SystemClock.elapsedRealtime();
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null)return;
        // Only examine short labels on SELECTED navigation nodes. Never store them.
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        int seen=0;
        boolean foundReels=false,foundOther=false;
        while(!queue.isEmpty()&&seen++<65){
            AccessibilityNodeInfo n=queue.removeFirst();
            if(n.isSelected()||n.isChecked()){
                String label=shortControlLabel(n);
                if(isReelsTab(label))foundReels=true;
                else if(isOtherTab(label))foundOther=true;
            }
            int count=Math.min(n.getChildCount(),12);
            for(int i=0;i<count;i++){
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)queue.add(child);
            }
        }
        if(foundReels){reelsTabOpen=true;commentsPanelOpen=false;}
        else if(foundOther){reelsTabOpen=false;commentsPanelOpen=false;}
    }
    private static String shortControlLabel(AccessibilityNodeInfo n){
        CharSequence description=n.getContentDescription();
        CharSequence text=description==null?n.getText():description;
        if(text==null||text.length()>65)return "";
        return text.toString().trim().toLowerCase(Locale.US);
    }
    private static boolean isReelsTab(String s){
        return s.equals("reels")||s.equals("reels tab")||s.equals("reels, selected")||
            s.equals("reels tab, selected");
    }
    private static boolean isOtherTab(String s){
        return s.equals("home")||s.equals("home tab")||
            s.equals("search")||s.equals("search tab")||
            s.equals("profile")||s.equals("profile tab")||
            s.equals("messages")||s.equals("messages tab");
    }
    private static boolean isCommentsControl(String s){
        return s.equals("comments")||s.equals("comment")||s.startsWith("view comments");
    }
    @Override public void onInterrupt(){reelsTabOpen=false;commentsPanelOpen=false;}
}