package com.missionmode.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;
import java.util.Map;
import java.util.LinkedHashMap;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** A native, on-device dashboard. No account, web view, analytics or network calls. */
public class MainActivity extends Activity {
    private static final int BG=Color.rgb(247,248,245), INK=Color.rgb(24,44,50), MUTED=Color.rgb(106,122,125),
        CORAL=Color.rgb(239,111,89), GREEN=Color.rgb(41,124,102), WHITE=Color.WHITE,
        BORDER=Color.rgb(228,233,231), NAVY=Color.rgb(28,54,62), SOFT=Color.rgb(235,245,240);
    private static final int HOME=0,INSIGHTS=1,HISTORY=2,SETTINGS=3;
    private int tab=HOME;
    private LinearLayout content;
    private ProgressRing ring;
    private int selectedMinutes=5;
    private String selectedApp="Instagram",selectedPurpose="Reply to messages";
    private int insightsDays=7;
    private TextView manualStartButton;
    private EditText manualMinutesEntry;
    private final List<TextView> presetChips=new ArrayList<>();
    private final List<Integer> presetValues=new ArrayList<>();
    private boolean renderedExpired=false;
    private final Handler tickHandler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){
        @Override public void run(){
            JSONObject s=MissionStore.active(MainActivity.this);
            if(ring!=null){ring.invalidate();}
            if(s!=null && s.optLong("endAt")<=System.currentTimeMillis()){
                if(MissionStore.markRemindedIfExpired(MainActivity.this)){
                    MissionNotifications.expiry(MainActivity.this);
                    MonitorService.showExpiryOverlayIfVisible();
                }
                if(!renderedExpired && tab==HOME){show();}
            }
            tickHandler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        MissionNotifications.channels(this);
        selectedMinutes=MissionStore.defaultMinutes(this);
        selectedApp=MissionStore.selectedApp(this);selectedPurpose=MissionStore.selectedPurpose(this);
        show();getWindow().getDecorView().post(this::maybeWelcome);
    }
    @Override protected void onResume(){
        super.onResume();maybeStartMonitor();
        JSONObject pending=MissionStore.active(this);
        if(pending!=null){
            if(pending.optLong("endAt")>System.currentTimeMillis())MissionStore.scheduleAlarm(this,pending.optLong("endAt"));
            if(MissionStore.markRemindedIfExpired(this))MissionNotifications.expiry(this);
        }
        show();tickHandler.removeCallbacks(tick);tickHandler.post(tick);
        if(MissionStore.wantsMonitoring(this)&&MissionStore.monitorReady(this))
            getWindow().getDecorView().post(this::offerOptionalPopup);
    }
    @Override protected void onPause(){tickHandler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){tickHandler.removeCallbacks(tick);super.onDestroy();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);show();}
    @Override public void onRequestPermissionsResult(int requestCode,String[] perms,int[] result){
        super.onRequestPermissionsResult(requestCode,perms,result);
        if(requestCode==90&&MissionStore.wantsMonitoring(this)){
            if(MissionStore.notificationPermission(this)){
                if(!MissionStore.usagePermission(this))requestUsage();else maybeStartMonitor();
            }else{toast("Notifications are required for reliable mission reminders.");}
        }
        maybeStartMonitor();show();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable border(int color,int radius,int stroke){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),stroke);return d;}
    private GradientDrawable grad(int radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{NAVY,Color.rgb(37,93,83)});d.setCornerRadius(dp(radius));return d;}
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private TextView spaced(String s,int size,int color){TextView t=text(s,size,color,true);t.setLetterSpacing(0.09f);return t;}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void add(LinearLayout parent,View v,int top){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);
    }
    private void addWeight(LinearLayout parent,View v,float weight){parent.addView(v,new LinearLayout.LayoutParams(0,-2,weight));}
    private LinearLayout card(){LinearLayout v=column();v.setPadding(dp(19),dp(19),dp(19),dp(19));v.setBackground(border(WHITE,22,BORDER));return v;}
    private TextView pill(String s,int bgColor,int textColor){
        TextView t=text(s,11,textColor,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(7),dp(12),dp(7));t.setBackground(bg(bgColor,40));return t;
    }
    private TextView button(String s,boolean primary,Runnable callback){
        TextView b=text(s,15,primary?WHITE:INK,true);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(53));
        b.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable shape=primary?bg(CORAL,15):border(WHITE,15,BORDER);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x44FFFFFF:0x2299AAAA),shape,null));
        b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->callback.run());return b;
    }
    private void note(LinearLayout c,String s){add(c,text(s,12,MUTED,false),8);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void title(String overline,String headline){
        add(content,spaced(overline,11,GREEN),24);
        add(content,text(headline,25,INK,true),5);
    }
    private void show(){
        ring=null;renderedExpired=false;
        manualStartButton=null;manualMinutesEntry=null;presetChips.clear();presetValues.clear();
        selectedMinutes=MissionStore.defaultMinutes(this);
        selectedApp=MissionStore.selectedApp(this);
        selectedPurpose=MissionStore.selectedPurpose(this);
        LinearLayout root=column();root.setBackgroundColor(BG);
        if(Build.VERSION.SDK_INT>=35){root.setOnApplyWindowInsetsListener((v,insets)->{
            Insets bars=insets.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(0,bars.top,0,bars.bottom);return insets;
        });}
        LinearLayout header=row();header.setPadding(dp(20),dp(16),dp(20),dp(10));
        TextView icon=text("M",20,WHITE,true);icon.setGravity(Gravity.CENTER);icon.setBackground(bg(CORAL,15));
        header.addView(icon,new LinearLayout.LayoutParams(dp(46),dp(46)));
        LinearLayout brand=column();brand.setPadding(dp(12),0,0,0);
        add(brand,text("Mission Mode",19,INK,true),0);
        add(brand,text("Open with purpose.",12,MUTED,false),1);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        header.addView(pill("BETA 0.5",SOFT,GREEN));
        root.addView(header,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        content=column();content.setPadding(dp(20),dp(8),dp(20),dp(32));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(tab==HOME)home();else if(tab==INSIGHTS)insights();else if(tab==HISTORY)history();else settings();
        root.addView(nav(),new LinearLayout.LayoutParams(-1,dp(76)));
        setContentView(root);
    }
    private View nav(){
        LinearLayout bar=row();bar.setPadding(dp(12),dp(5),dp(12),dp(6));bar.setBackground(border(WHITE,0,BORDER));
        String[] labels={"Home","Insights","History","Settings"};
        String[] symbols={"◎","▥","▤","⚙"};
        for(int i=0;i<labels.length;i++){
            final int which=i;
            LinearLayout item=column();item.setGravity(Gravity.CENTER);
            item.setPadding(dp(8),dp(6),dp(8),dp(6));
            if(tab==i)item.setBackground(bg(SOFT,14));
            TextView glyph=text(symbols[i],20,tab==i?GREEN:MUTED,true);glyph.setGravity(Gravity.CENTER);item.addView(glyph);
            TextView name=text(labels[i],11,tab==i?GREEN:MUTED,tab==i);name.setGravity(Gravity.CENTER);item.addView(name);
            item.setOnClickListener(v->{tab=which;show();});item.setClickable(true);
            bar.addView(item,new LinearLayout.LayoutParams(0,-1,1));
        }
        return bar;
    }
    private void home(){
        JSONObject s=MissionStore.active(this);
        LinearLayout hero=column();hero.setPadding(dp(21),dp(24),dp(21),dp(24));hero.setBackground(grad(25));
        hero.setElevation(dp(4));add(content,hero,14);
        if(s==null){
            add(hero,pill("YOUR DAILY RESET",Color.rgb(60,110,98),WHITE),0);
            add(hero,text("Less scrolling.\nMore doing.",32,WHITE,true),16);
            add(hero,text("Choose one app, one reason and one time limit.",15,Color.rgb(214,234,227),false),10);
            add(hero,missionChoices(),16);
            add(hero,durationChooser(),12);
            manualStartButton=button("Start "+selectedMinutes+"-minute mission  →",true,this::startManualMission);
            add(hero,manualStartButton,15);
            add(hero,text("Starts a mission and opens "+selectedApp+". You can also open your apps normally while detection is enabled.",11,Color.rgb(214,234,227),false),12);
        }else{
            long remaining=s.optLong("endAt")-System.currentTimeMillis();
            boolean expired=remaining<=0;renderedExpired=expired;
            add(hero,pill(expired?"TIME TO CHECK IN":"MISSION IN PROGRESS",Color.rgb(60,110,98),WHITE),0);
            add(hero,text(expired?"Did you finish?":"Stay on your mission.",27,WHITE,true),12);
            add(hero,text(s.optString("app","Instagram")+" · "+s.optString("purpose","Reply to messages"),14,Color.rgb(214,234,227),false),5);
            ring=new ProgressRing(s);hero.addView(ring,new LinearLayout.LayoutParams(-1,dp(214)));
            LinearLayout count=column();count.setPadding(dp(13),dp(12),dp(13),dp(12));
            count.setBackground(bg(Color.rgb(47,88,80),15));
            add(count,text("SHORT VIDEOS MANUALLY COUNTED",10,Color.rgb(191,235,216),true),0);
            LinearLayout countActions=row();
            TextView countNow=text(String.valueOf(s.optInt("manualVideos",0)),28,WHITE,true);
            countActions.addView(countNow,new LinearLayout.LayoutParams(0,-2,1));
            countActions.addView(button("−1",false,()->{MissionStore.changeManualVideoCount(this,-1);MissionNotifications.countdown(this);show();}),new LinearLayout.LayoutParams(dp(78),dp(48)));
            countActions.addView(button("+1",false,()->{MissionStore.changeManualVideoCount(this,1);MissionNotifications.countdown(this);show();}),new LinearLayout.LayoutParams(dp(78),dp(48)));
            add(count,countActions,7);
            add(count,text("Manual tally only. We cannot detect individual Reels or Shorts without invasive screen monitoring.",11,Color.rgb(214,234,227),false),7);
            add(hero,count,8);
            add(hero,button("I finished my mission",true,()->finish("achieved")),8);
            add(hero,button("Extend by 2 minutes",false,()->extend(2)),9);
            add(hero,button("Extend by 5 minutes",false,()->extend(5)),9);
            add(hero,button("I got distracted",false,()->finish("distracted")),9);
            add(hero,button("Open "+s.optString("app","Instagram"),false,()->launchSelectedApp(s.optString("app","Instagram"))),9);
            add(hero,text("Time is measured from when the mission started, not verified Instagram usage.",11,Color.rgb(214,234,227),false),10);
        }
        title("AUTOMATIC DETECTION","Your focus assistant");
        LinearLayout c=card();add(content,c,11);
        boolean usage=MissionStore.usagePermission(this),notifications=MissionStore.notificationPermission(this);
        boolean running=MonitorService.running && MissionStore.wantsMonitoring(this);
        LinearLayout status=row();
        TextView indicator=pill(running?"ACTIVE":"NOT ACTIVE",running?SOFT:Color.rgb(255,240,231),running?GREEN:CORAL);
        addWeight(status,text("Social app reminders",17,INK,true),1);status.addView(indicator);
        add(c,status,0);
        add(c,text(running?"Monitoring "+MissionStore.enabledCount(this)+" selected apps while this screen is closed.":"Turn on detection to receive a reminder when a selected app is opened normally, including from Recents.",13,MUTED,false),12);
        if(!running)add(c,button("Enable automatic detection",true,this::enableMonitor),15);
        else add(c,button("Manage reminders",false,()->{tab=SETTINGS;show();}),15);
        LinearLayout stats=row();stats.setPadding(0,dp(6),0,0);
        int completed=MissionStore.history(this).length();
        LinearLayout stat1=column();add(stat1,text(String.valueOf(completed),26,INK,true),0);add(stat1,text("sessions recorded",12,MUTED,false),2);
        addWeight(stats,stat1,1);
        LinearLayout stat2=column();add(stat2,text(s==null?"Ready":"Running",20,GREEN,true),0);add(stat2,text("current mission",12,MUTED,false),2);
        addWeight(stats,stat2,1);
        add(content,stats,23);
        add(content,text("Private by design. No account, Accessibility permission, or internet access. Usage Access is optional until you enable automatic monitoring.",12,MUTED,false),25);
    }
    private void history(){
        title("YOUR PROGRESS","Small wins add up.");
        JSONArray rows=MissionStore.history(this);int achieved=0;
        for(int i=0;i<rows.length();i++){JSONObject x=rows.optJSONObject(i);if(x!=null&&"achieved".equals(x.optString("outcome")))achieved++;}
        LinearLayout summary=card();add(content,summary,16);
        add(summary,text(rows.length()+" missions logged",25,INK,true),0);
        add(summary,text(achieved+" goals achieved",14,GREEN,true),9);
        note(summary,"Times are elapsed mission periods, not verified social-app screen time.");
        if(rows.length()==0){
            LinearLayout empty=card();add(content,empty,15);
            add(empty,text("Your first win starts today.",20,INK,true),0);
            note(empty,"Complete a mission to see your records here.");
            add(empty,button("Start a mission",true,()->{tab=HOME;show();}),16);
            return;
        }
        title("SESSION HISTORY","Most recent sessions");
        for(int i=0;i<Math.min(30,rows.length());i++){
            JSONObject x=rows.optJSONObject(i);if(x==null)continue;
            LinearLayout item=card();add(content,item,10);
            LinearLayout line=row();addWeight(line,text(x.optString("app","Instagram")+" · "+x.optString("purpose","Reply to messages"),15,INK,true),1);
            line.addView(pill("achieved".equals(x.optString("outcome"))?"ACHIEVED":"DISTRACTED",SOFT,GREEN));
            add(item,line,0);
            String date=new SimpleDateFormat("MMM d · h:mm a",Locale.getDefault()).format(new Date(x.optLong("finishedAt")));
            add(item,text(date+"    "+String.format(Locale.US,"%.1f",x.optLong("missionElapsedSeconds")/60.0)+" min",12,MUTED,false),8);
            add(item,text("Short videos manually counted: "+x.optInt("manualVideos",0),12,MUTED,false),5);
        }
        add(content,button("Clear history",false,()->new AlertDialog.Builder(this)
            .setTitle("Delete all mission history?").setMessage("Records are stored only on this device. This cannot be undone.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{MissionStore.clearHistory(this);show();}).show()),20);
    }
    private void settings(){
        title("YOUR CONTROLS","Make it your own.");
        LinearLayout c=card();add(content,c,15);
        add(c,text("Automatic social app detection",20,INK,true),0);
        note(c,"Detect selected app openings without Accessibility. Choose the apps below. Monitoring is optional and has a persistent notification.");
        boolean usage=MissionStore.usagePermission(this),notify=MissionStore.notificationPermission(this);
        add(c,text("Usage Access: "+(usage?"Allowed":"Not allowed"),13,usage?GREEN:CORAL,true),14);
        add(c,text("Notifications: "+(notify?"Allowed":"Not allowed"),13,notify?GREEN:CORAL,true),6);
        add(c,text("Monitoring: "+(MonitorService.running?"Running":"Stopped"),13,MonitorService.running?GREEN:MUTED,true),6);
        long beat=MissionStore.lastHeartbeat(this);
        add(c,text(beat==0?"No background heartbeat yet":"Last background heartbeat: "+new SimpleDateFormat("MMM d, h:mm:ss a",Locale.getDefault()).format(new Date(beat)),12,MUTED,false),6);
        note(c,"If monitoring is stopped, Android cannot detect app openings. Some Vivo phones require background autostart and unrestricted battery use, even with a foreground notification.");
        add(c,text("Precise time reminders: "+(MissionStore.canUsePreciseAlarm(this)?"Available":"Permission needed"),13,MissionStore.canUsePreciseAlarm(this)?GREEN:CORAL,true),8);
        if(!MissionStore.canUsePreciseAlarm(this))add(c,button("Allow precise 5-minute reminders",false,this::requestPreciseAlarms),10);
        note(c,"Monitoring can continue after the screen is closed and restart after ordinary process kills or reboot if Android allows it. Force Stop disables background monitoring until you manually reopen Mission Mode.");
        add(c,button("Open background battery settings",false,this::openBatterySettings),12);
        add(c,button("Open Mission Mode app permissions",false,this::openAppSettings),8);
        if(MissionStore.wantsMonitoring(this)){
            add(c,button("Stop automatic detection",false,this::disableMonitor),17);
            if(!usage)add(c,button("Grant Usage Access",true,this::requestUsage),8);
            if(!notify)add(c,button("Allow notifications",true,this::requestNotification),8);
            if(usage&&notify&&!MonitorService.running)add(c,button("Restart detection",true,()->{maybeStartMonitor();show();}),8);
        }else add(c,button("Enable automatic detection",true,this::enableMonitor),17);
        long last=MissionStore.lastDetection(this);
        note(c,last>0?"Last monitored app opening: "+new SimpleDateFormat("MMM d, h:mm:ss a",Locale.getDefault()).format(new Date(last)):"No monitored app opening recorded yet.");
        LinearLayout appSettings=card();add(content,appSettings,15);
        add(appSettings,text("Apps to monitor",20,INK,true),0);
        note(appSettings,"Instagram is enabled initially. Enable TikTok, Snapchat and YouTube individually. Only installed native apps can be detected.");
        for(TrackedApps.Entry entry:TrackedApps.ALL){
            final String app=entry.name;
            boolean on=MissionStore.appEnabled(this,app);
            add(appSettings,button((on?"●  ":"○  ")+app+(on?"  ·  ON":"  ·  OFF"),false,()->{
                MissionStore.setAppEnabled(this,app,!MissionStore.appEnabled(this,app));
                if(MonitorService.running){stopService(new Intent(this,MonitorService.class));
                    getWindow().getDecorView().postDelayed(this::maybeStartMonitor,300);}
                show();
            }),10);
        }
        LinearLayout overlayCard=card();add(content,overlayCard,15);
        add(overlayCard,text("On-screen pop-ups",20,INK,true),0);
        note(overlayCard,"Optional. Shows the purpose and timer selector over whichever monitored app is foreground. The card hides when you leave it.");
        add(overlayCard,pill(MissionStore.wantsOverlay(this)?"POP-UPS ENABLED":"NOT ENABLED",MissionStore.wantsOverlay(this)?SOFT:Color.rgb(255,240,231),MissionStore.wantsOverlay(this)?GREEN:CORAL),12);
        if(MissionStore.wantsOverlay(this)){
            add(overlayCard,button("Turn off pop-ups",false,()->{MissionStore.setWantsOverlay(this,false);MonitorService.dismissOverlay();show();}),13);
        }else{
            add(overlayCard,button("Enable optional pop-ups",true,this::requestOverlay),13);
        }
        note(overlayCard,"Android's Display over other apps permission is separate from Accessibility. Some banking apps may flag overlays. You can keep pop-ups off and use notifications instead.");
        LinearLayout test=card();add(content,test,15);
        add(test,text("Test & troubleshoot",19,INK,true),0);
        add(test,button("Send a test notification",false,()->{MissionNotifications.prompt(this,MissionStore.selectedApp(this));toast("Test reminder sent. This does not prove opening detection.");}),13);
        add(test,button("Open Android notification settings",false,this::openAppSettings),9);
        note(test,"On Vivo phones, enable Autostart and allow background activity for Mission Mode. Force Stop always prevents monitoring until the app is opened again.");
        add(content,text("Privacy: Missions remain on this device. Usage Access exposes app-usage events at Android level, but Mission Mode keeps no histories of other apps and reads no messages or banking content.",12,MUTED,false),18);
        add(content,text("Private test build 0.5 · No account · No internet",11,MUTED,false),12);
    }
    private void maybeStartMonitor(){
        if(MissionStore.wantsMonitoring(this)&&MissionStore.monitorReady(this)&&!MonitorService.running){
            try{startForegroundService(new Intent(this,MonitorService.class));}
            catch(Exception e){toast("Monitoring could not start. Open the app again to retry.");}
        }
    }
    private void enableMonitor(){
        MissionStore.setWantsMonitoring(this,true);
        if(!MissionStore.notificationPermission(this)){requestNotification();return;}
        if(!MissionStore.usagePermission(this)){requestUsage();return;}
        maybeStartMonitor();show();
    }
    private void disableMonitor(){
        MissionStore.setWantsMonitoring(this,false);stopService(new Intent(this,MonitorService.class));
        MonitorService.dismissOverlay();MissionNotifications.cancel(this,MissionNotifications.OPEN_ID);show();
    }
    private void requestUsage(){
        new AlertDialog.Builder(this).setTitle("Allow Usage Access")
            .setMessage("Android Usage Access can reveal which apps are active. Mission Mode only reacts to your enabled social apps and does not read messages, banking details or screen contents. Android requires you to grant this permission in Settings.")
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Open Settings",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception ex){openAppSettings();}
            }).show();
    }
    private void requestNotification(){
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},90);
        }else{toast("Allow Mission Mode notifications in Android Settings.");openAppSettings();}
    }
    private void requestOverlay(){
        new AlertDialog.Builder(this).setTitle("Optional floating reminders")
            .setMessage("Android's Display over other apps permission shows the purpose selector above the social apps you enable. This is not Accessibility. We remove our card when leaving a selected app. Some banking apps may hide or restrict overlays.")
            .setNegativeButton("Keep notifications only",null)
            .setPositiveButton("Enable pop-ups",(d,w)->{
                MissionStore.setWantsOverlay(this,true);
                if(PromptOverlay.allowed(this)){show();return;}
                try{startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())));}
                catch(Exception ex){openAppSettings();}
            }).show();
    }
    private void openAppSettings(){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private void openBatterySettings(){
        try{startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
        catch(Exception ignored){openAppSettings();}
    }
    private void requestPreciseAlarms(){
        if(Build.VERSION.SDK_INT<31||MissionStore.canUsePreciseAlarm(this)){toast("Precise alarms are available.");return;}
        new AlertDialog.Builder(this).setTitle("Remind me when time is up")
            .setMessage("Android may delay inexact alarms when your phone saves battery. You can choose Alarms & reminders access for more accurate mission deadlines. This permission is optional, and the monitoring service will still check the timer while running.")
            .setNegativeButton("Later",null)
            .setPositiveButton("Open Settings",(d,w)->{
                try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}
                catch(Exception ignored){openAppSettings();}
            }).show();
    }
    private void launchSelectedApp(String app){
        try{Intent intent=TrackedApps.launch(this,app);
            if(intent==null){toast(app+" isn't installed. Install it before starting a mission.");return;}
            startActivity(intent);
        }catch(Exception e){toast("Could not open "+app+". Try launching it normally.");}
    }
    private void startManualMission(){
        int duration=selectedMinutes;
        if(manualMinutesEntry!=null){
            try{duration=MissionStore.validatedMinutes(Integer.parseInt(manualMinutesEntry.getText().toString().trim()));}
            catch(Exception ignored){}
        }
        MissionStore.setDefaultMinutes(this,duration);
        if(TrackedApps.launch(this,selectedApp)==null){toast("Install "+selectedApp+" to use this shortcut.");return;}
        if(!MissionStore.startMission(this,"manual_quick_start",duration,selectedApp,selectedPurpose)){
            toast("Finish your active mission first.");show();return;
        }
        String app=selectedApp;
        MissionNotifications.countdown(this);show();launchSelectedApp(app);
    }
    private void extend(int mins){
        if(MissionStore.extendMission(this,mins)){
            MissionNotifications.cancel(this,MissionNotifications.EXPIRY_ID);
            MissionNotifications.countdown(this);
            MonitorService.dismissOverlay();show();
        }
    }
    private void selectMinutes(int requested){
        selectedMinutes=MissionStore.validatedMinutes(requested);
        MissionStore.setDefaultMinutes(this,selectedMinutes);
        if(manualMinutesEntry!=null && !String.valueOf(selectedMinutes).equals(manualMinutesEntry.getText().toString())){
            manualMinutesEntry.setText(String.valueOf(selectedMinutes));
            manualMinutesEntry.setSelection(manualMinutesEntry.length());
        }
        updateSelectedMinutesUI();
    }
    private void updateSelectedMinutesUI(){
        if(manualStartButton!=null)manualStartButton.setText("Start "+selectedMinutes+"-minute mission  →");
        for(int i=0;i<presetChips.size();i++){
            TextView t=presetChips.get(i);
            boolean chosen=presetValues.get(i)==selectedMinutes;
            t.setBackground(chosen?bg(GREEN,12):bg(WHITE,12));
            t.setTextColor(chosen?WHITE:INK);
        }
    }
    private LinearLayout durationChooser(){
        LinearLayout panel=column();panel.setPadding(dp(14),dp(13),dp(14),dp(15));
        panel.setBackground(bg(Color.rgb(243,250,245),17));
        add(panel,spaced("SET YOUR TIME",11,GREEN),0);
        int[][] presets={{3,5,10},{15,20,30}};
        for(int[] group:presets){
            LinearLayout r=row();
            for(int duration:group){
                TextView chip=text(duration+" min",13,INK,true);
                chip.setGravity(Gravity.CENTER);chip.setMinHeight(dp(44));
                chip.setClickable(true);chip.setOnClickListener(v->selectMinutes(duration));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
                lp.setMargins(dp(3),0,dp(3),0);r.addView(chip,lp);
                presetChips.add(chip);presetValues.add(duration);
            }
            add(panel,r,9);
        }
        add(panel,text("CUSTOM TIME  ·  1 TO 180 MINUTES",10,MUTED,true),15);
        LinearLayout controls=row();
        TextView minus=button("−",false,()->selectMinutes(selectedMinutes-1));
        minus.setTextSize(25);controls.addView(minus,new LinearLayout.LayoutParams(dp(51),dp(49)));
        manualMinutesEntry=new EditText(this);
        manualMinutesEntry.setSingleLine(true);
        manualMinutesEntry.setInputType(InputType.TYPE_CLASS_NUMBER);
        manualMinutesEntry.setImeOptions(EditorInfo.IME_ACTION_DONE);
        manualMinutesEntry.setText(String.valueOf(selectedMinutes));
        manualMinutesEntry.setSelectAllOnFocus(true);
        manualMinutesEntry.setTextSize(21);manualMinutesEntry.setTextColor(INK);
        manualMinutesEntry.setGravity(Gravity.CENTER);manualMinutesEntry.setBackground(bg(WHITE,12));
        LinearLayout.LayoutParams editParams=new LinearLayout.LayoutParams(0,dp(49),1);
        editParams.setMargins(dp(8),0,dp(8),0);controls.addView(manualMinutesEntry,editParams);
        manualMinutesEntry.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            public void onTextChanged(CharSequence s,int st,int before,int count){}
            public void afterTextChanged(Editable e){
                try{
                    if(e.length()==0)return;
                    selectedMinutes=MissionStore.validatedMinutes(Integer.parseInt(e.toString()));
                    MissionStore.setDefaultMinutes(MainActivity.this,selectedMinutes);
                    updateSelectedMinutesUI();
                }catch(Exception ignored){}
            }
        });
        TextView plus=button("+",false,()->selectMinutes(selectedMinutes+1));
        plus.setTextSize(24);controls.addView(plus,new LinearLayout.LayoutParams(dp(51),dp(49)));
        add(panel,controls,8);
        updateSelectedMinutesUI();
        return panel;
    }
    private LinearLayout missionChoices(){
        LinearLayout panel=column();panel.setPadding(dp(13),dp(13),dp(13),dp(13));
        panel.setBackground(bg(Color.rgb(243,250,245),17));
        add(panel,spaced("CHOOSE YOUR APP",11,GREEN),0);
        for(int i=0;i<TrackedApps.ALL.length;i+=2){
            LinearLayout row=row();
            for(int j=i;j<Math.min(i+2,TrackedApps.ALL.length);j++){
                String app=TrackedApps.ALL[j].name;
                boolean chosen=selectedApp.equals(app);
                TextView v=button(app,false,()->{
                    MissionStore.setSelectedApp(this,app);
                    MissionStore.setSelectedPurpose(this,MissionStore.defaultPurposeFor(this,app));
                    show();
                });
                v.setMinHeight(dp(44));v.setTextColor(chosen?WHITE:INK);
                v.setBackground(bg(chosen?GREEN:WHITE,11));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
                lp.setMargins(dp(3),0,dp(3),0);row.addView(v,lp);
            }
            add(panel,row,7);
        }
        add(panel,spaced("WHY ARE YOU OPENING IT?",11,GREEN),16);
        for(int i=0;i<TrackedApps.PURPOSES.length;i+=2){
            LinearLayout row=row();
            for(int j=i;j<Math.min(i+2,TrackedApps.PURPOSES.length);j++){
                String purpose=TrackedApps.PURPOSES[j];boolean chosen=selectedPurpose.equals(purpose);
                TextView v=button(purpose,false,()->{
                    MissionStore.setSelectedPurpose(this,purpose);MissionStore.setDefaultPurposeFor(this,selectedApp,purpose);show();
                });
                v.setTextSize(12);v.setMinHeight(dp(43));v.setTextColor(chosen?WHITE:INK);
                v.setBackground(bg(chosen?GREEN:WHITE,11));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(43),1);
                lp.setMargins(dp(3),0,dp(3),0);row.addView(v,lp);
            }
            add(panel,row,7);
        }
        return panel;
    }
    private void maybeWelcome(){
        if(MissionStore.prefs(this).getBoolean("setupIntroduced",false))return;
        MissionStore.prefs(this).edit().putBoolean("setupIntroduced",true).apply();
        new AlertDialog.Builder(this)
            .setTitle("Welcome to Mission Mode")
            .setMessage("We'll help you choose a purpose and time limit before opening social apps. For automatic detection, Android will ask you to allow Notifications and Usage Access. Floating pop-ups are optional. No Accessibility or account is required.")
            .setNegativeButton("Explore first",(d,w)->{})
            .setPositiveButton("Set up reminders",(d,w)->enableMonitor()).show();
    }
    private void offerOptionalPopup(){
        if(isFinishing()||!MissionStore.wantsMonitoring(this)||!MissionStore.monitorReady(this))return;
        if(MissionStore.prefs(this).getBoolean("overlayOffered",false))return;
        MissionStore.prefs(this).edit().putBoolean("overlayOffered",true).apply();
        new AlertDialog.Builder(this).setTitle("Show reminders above social apps?")
            .setMessage("You can use notifications only, or enable optional floating cards with purpose and timer controls. Android requires a separate approval for displaying over other apps.")
            .setNegativeButton("Notifications only",(d,w)->{})
            .setPositiveButton("Enable floating cards",(d,w)->requestOverlay()).show();
    }
    private void addInsightBar(LinearLayout parent,String name,int count,int max){
        LinearLayout r=row();
        TextView title=text(name,12,INK,false);
        title.setMaxLines(2);
        r.addView(title,new LinearLayout.LayoutParams(dp(124),-2));
        LinearLayout track=row();track.setBackground(bg(Color.rgb(231,239,234),6));
        View fill=new View(this);fill.setBackground(bg(CORAL,6));
        int width=dp(count==0?0:Math.max(7,(int)(120.0*count/Math.max(1,max))));
        track.addView(fill,new LinearLayout.LayoutParams(width,dp(12)));
        r.addView(track,new LinearLayout.LayoutParams(0,dp(12),1));
        TextView number=text(String.valueOf(count),12,GREEN,true);number.setGravity(Gravity.END);
        r.addView(number,new LinearLayout.LayoutParams(dp(37),-2));
        add(parent,r,12);
    }
    private void insights(){
        title("YOUR INTENTIONS","Where does your time go?");
        LinearLayout switcher=card();add(content,switcher,15);
        LinearLayout periods=row();
        for(int days:new int[]{1,7}){
            TextView b=button(days==1?"Today":"Last 7 days",false,()->{insightsDays=days;show();});
            b.setBackground(bg(insightsDays==days?GREEN:SOFT,12));b.setTextColor(insightsDays==days?WHITE:INK);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);lp.setMargins(dp(4),0,dp(4),0);periods.addView(b,lp);
        }
        add(switcher,periods,0);
        Calendar cal=Calendar.getInstance();cal.set(Calendar.HOUR_OF_DAY,0);cal.set(Calendar.MINUTE,0);
        cal.set(Calendar.SECOND,0);cal.set(Calendar.MILLISECOND,0);
        if(insightsDays==7)cal.add(Calendar.DAY_OF_YEAR,-6);
        long start=cal.getTimeInMillis();
        JSONArray history=MissionStore.history(this);
        Map<String,Integer> purposes=new LinkedHashMap<>(),apps=new LinkedHashMap<>(),videos=new LinkedHashMap<>();
        for(String p:TrackedApps.PURPOSES)purposes.put(p,0);
        for(TrackedApps.Entry a:TrackedApps.ALL){apps.put(a.name,0);videos.put(a.name,0);}
        int count=0,achieved=0;long planned=0,elapsed=0;
        for(int i=0;i<history.length();i++){
            JSONObject s=history.optJSONObject(i);
            if(s==null||s.optLong("finishedAt")<start)continue;
            count++;if("achieved".equals(s.optString("outcome")))achieved++;
            planned+=s.optInt("plannedMinutes",0);elapsed+=s.optLong("missionElapsedSeconds",0);
            String p=TrackedApps.cleanPurpose(s.optString("purpose"));purposes.put(p,purposes.get(p)+1);
            String app=TrackedApps.byName(s.optString("app")).name;apps.put(app,apps.get(app)+1);
            videos.put(app,videos.get(app)+Math.max(0,s.optInt("manualVideos",0)));
        }
        LinearLayout summary=card();add(content,summary,13);
        add(summary,text(count+" recorded missions",25,INK,true),0);
        add(summary,text(achieved+" completed as intended  ·  "+planned+" planned minutes",14,GREEN,true),8);
        note(summary,"Elapsed mission periods: "+String.format(Locale.US,"%.1f",elapsed/60.0)+" minutes. This does not measure verified app screen time.");
        if(count==0){
            LinearLayout empty=card();add(content,empty,13);
            add(empty,text("Nothing logged for this period yet.",17,INK,true),0);
            note(empty,"Select your purpose and finish a mission to start building insights.");return;
        }
        LinearLayout intent=card();add(content,intent,14);
        add(intent,text("Why you opened social apps",19,INK,true),0);
        int max=1;for(int v:purposes.values())max=Math.max(max,v);
        for(Map.Entry<String,Integer> entry:purposes.entrySet())addInsightBar(intent,entry.getKey(),entry.getValue(),max);
        LinearLayout appChart=card();add(content,appChart,14);
        add(appChart,text("Missions by app",19,INK,true),0);
        max=1;for(int v:apps.values())max=Math.max(max,v);
        for(Map.Entry<String,Integer> entry:apps.entrySet())addInsightBar(appChart,entry.getKey(),entry.getValue(),max);
        note(appChart,"Charts count missions you started and finished. They do not include ignored prompts or all app launches.");
        LinearLayout videoChart=card();add(content,videoChart,14);
        add(videoChart,text("Videos manually counted by app",19,INK,true),0);
        int vMax=1;for(int v:videos.values())vMax=Math.max(vMax,v);
        for(Map.Entry<String,Integer> item:videos.entrySet())addInsightBar(videoChart,item.getKey(),item.getValue(),vMax);
        note(videoChart,"These are taps on the manual +1 counter, not automatically detected Reels, Stories, TikToks or Shorts.");
        if(insightsDays==7){
            LinearLayout activity=card();add(content,activity,14);
            add(activity,text("Daily mission activity",19,INK,true),0);
            Calendar day=Calendar.getInstance();day.setTimeInMillis(start);
            for(int i=0;i<7;i++){
                long from=day.getTimeInMillis();day.add(Calendar.DAY_OF_YEAR,1);long to=day.getTimeInMillis();
                int n=0;
                for(int j=0;j<history.length();j++){
                    JSONObject record=history.optJSONObject(j);
                    if(record!=null&&record.optLong("finishedAt")>=from&&record.optLong("finishedAt")<to)n++;
                }
                String label=new SimpleDateFormat("EEE MMM d",Locale.getDefault()).format(new Date(from));
                addInsightBar(activity,label,n,Math.max(1,count));
            }
        }
    }
    private void finish(String outcome){
        if(MissionStore.finishMission(this,outcome)){
            MissionNotifications.cancel(this,MissionNotifications.EXPIRY_ID);
            MissionNotifications.cancel(this,MissionNotifications.TIMER_ID);
            MonitorService.dismissOverlay();toast(outcome.equals("achieved")?"Mission complete. Well done.":"Mission recorded. Tomorrow is another chance.");
        }
        show();
    }
    private final class ProgressRing extends View {
        private final JSONObject s;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ProgressRing(JSONObject session){super(MainActivity.this);s=session;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            float radius=Math.min(dp(77),Math.min(getWidth(),getHeight())/2f-dp(6));
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(11));paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0x556DC7AA);canvas.drawCircle(cx,cy,radius,paint);
            long end=s.optLong("endAt"),start=s.optLong("startAt");
            long remaining=Math.max(0,end-System.currentTimeMillis());
            float fraction=Math.max(0f,Math.min(1f,remaining/(float)Math.max(1,end-start)));
            paint.setColor(CORAL);
            RectF r=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
            canvas.drawArc(r,-90,360*fraction,false,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            paint.setColor(WHITE);paint.setTextSize(dp(37));paint.setTextAlign(Paint.Align.CENTER);
            long secs=(remaining+999)/1000;String time=String.format(Locale.US,"%02d:%02d",secs/60,secs%60);
            canvas.drawText(time,cx,cy+dp(8),paint);
            paint.setTextSize(dp(12));paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
            paint.setColor(Color.rgb(208,230,222));canvas.drawText(remaining>0?"TIME LEFT":"TIME IS UP",cx,cy+dp(31),paint);
        }
    }
}