package com.missionmode.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Optional focusable overlay displayed only when the selected monitored app is foreground. */
final class PromptOverlay {
    private final Context c;
    private final WindowManager wm;
    private View attached;
    private String showing="";
    private final int INK=Color.rgb(23,45,52),CORAL=Color.rgb(241,106,85),WHITE=Color.WHITE;
    PromptOverlay(Context context){c=context.getApplicationContext();wm=(WindowManager)c.getSystemService(Context.WINDOW_SERVICE);}
    static boolean allowed(Context c){return Build.VERSION.SDK_INT>=23 && Settings.canDrawOverlays(c);}
    private int dp(float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));return t;
    }
    private LinearLayout col(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private void add(LinearLayout parent,View child,int top){
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(top);parent.addView(child,lp);
    }
    private TextView button(String title,boolean main,Runnable click){
        TextView t=label(title,14,main?WHITE:INK,true);
        t.setMinHeight(dp(45));t.setPadding(dp(10),dp(10),dp(10),dp(10));t.setGravity(Gravity.CENTER);
        t.setBackground(bg(main?CORAL:Color.rgb(239,246,241),11));t.setOnClickListener(v->click.run());return t;
    }
    private void broadcast(String action,String app,String purpose,int min){
        Intent i=new Intent(c,MissionReceiver.class).setAction(action);
        if(MissionReceiver.START.equals(action)){
            i.putExtra(MissionReceiver.EXTRA_APP,app);
            i.putExtra(MissionReceiver.EXTRA_PURPOSE,purpose);
            i.putExtra(MissionReceiver.EXTRA_MINUTES,MissionStore.validatedMinutes(min));
        }
        c.sendBroadcast(i);hide();
    }
    void show(String mode,String app){
        if(!allowed(c)||wm==null)return;
        String key=mode+":"+app;
        if(key.equals(showing)&&attached!=null)return;
        hide();
        boolean expired="expiry".equals(mode);
        LinearLayout card=col();card.setPadding(dp(18),dp(17),dp(18),dp(18));card.setBackground(bg(INK,24));
        add(card,label("MISSION MODE  /  "+app.toUpperCase(),10,Color.rgb(147,227,198),true),0);
        add(card,label(expired?"Time to check in.":"Why are you here?",24,WHITE,true),7);
        add(card,label(expired?"Choose your next step.":"Pick one purpose and make the time intentional.",12,Color.rgb(210,232,223),false),6);
        if(expired){
            JSONObject active=MissionStore.active(c);
            if(active!=null)add(card,label(active.optString("purpose")+" · "+active.optInt("plannedMinutes",5)+" min planned",15,WHITE,true),11);
            add(card,button("I finished",true,()->broadcast(MissionReceiver.FINISH,null,null,0)),16);
            add(card,button("Extend 2 minutes",false,()->broadcast(MissionReceiver.EXTEND,null,null,0)),8);
            add(card,button("Leave the social app",false,()->{
                broadcast(MissionReceiver.FINISH,null,null,0);
                try{
                    Intent home=new Intent(Intent.ACTION_MAIN);
                    home.addCategory(Intent.CATEGORY_HOME);
                    home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(home);
                }catch(Exception ignored){}
            }),8);
            add(card,button("I got distracted",false,()->broadcast(MissionReceiver.DISTRACTED,null,null,0)),8);
        }else{
            final String[] purpose={MissionStore.defaultPurposeFor(c,app)};
            final int[] mins={MissionStore.defaultMinutes(c)};
            add(card,label("WHAT WILL YOU DO?",11,Color.rgb(180,206,200),true),16);
            List<TextView> chips=new ArrayList<>();
            for(int i=0;i<TrackedApps.PURPOSES.length;i+=2){
                LinearLayout r=row();
                for(int j=i;j<Math.min(i+2,TrackedApps.PURPOSES.length);j++){
                    String choice=TrackedApps.PURPOSES[j];
                    TextView chip=button(choice,choice.equals(purpose[0]),()->{
                        purpose[0]=choice;
                        for(int k=0;k<chips.size();k++){
                            TextView b=chips.get(k);boolean active=TrackedApps.PURPOSES[k].equals(choice);
                            b.setBackground(bg(active?CORAL:Color.rgb(239,246,241),10));
                            b.setTextColor(active?WHITE:INK);
                        }
                    });
                    chip.setTextSize(12);chip.setMinHeight(dp(41));
                    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
                    p.setMargins(dp(3),0,dp(3),0);r.addView(chip,p);chips.add(chip);
                }
                add(card,r,7);
            }
            add(card,label("SET A TIMER",11,Color.rgb(180,206,200),true),14);
            int[] presets={3,5,10,15,20,30};
            List<TextView> times=new ArrayList<>();
            EditText custom=new EditText(c);
            Runnable update=()->{
                for(int k=0;k<times.size();k++){
                    boolean chosen=mins[0]==presets[k];TextView t=times.get(k);
                    t.setBackground(bg(chosen?CORAL:Color.rgb(239,246,241),10));
                    t.setTextColor(chosen?WHITE:INK);
                }
                if(!custom.getText().toString().equals(String.valueOf(mins[0]))){
                    custom.setText(String.valueOf(mins[0]));custom.setSelection(custom.length());
                }
            };
            for(int i=0;i<presets.length;i+=3){
                LinearLayout r=row();
                for(int j=i;j<i+3;j++){
                    int value=presets[j];
                    TextView t=button(value+"m",mins[0]==value,()->{mins[0]=value;update.run();});
                    t.setMinHeight(dp(40));
                    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(dp(3),0,dp(3),0);
                    r.addView(t,lp);times.add(t);
                }
                add(card,r,7);
            }
            LinearLayout control=row();
            TextView less=button("−",false,()->{try{mins[0]=MissionStore.validatedMinutes(Integer.parseInt(custom.getText().toString()));}catch(Exception ignored){}mins[0]=MissionStore.validatedMinutes(mins[0]-1);update.run();});
            less.setTextSize(21);control.addView(less,new LinearLayout.LayoutParams(dp(50),dp(46)));
            custom.setInputType(InputType.TYPE_CLASS_NUMBER);custom.setSingleLine(true);
            custom.setText(String.valueOf(mins[0]));custom.setSelectAllOnFocus(true);
            custom.setTextSize(20);custom.setTextColor(INK);custom.setGravity(Gravity.CENTER);
            custom.setBackground(bg(WHITE,10));
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(46),1);ep.setMargins(dp(9),0,dp(9),0);
            control.addView(custom,ep);
            TextView more=button("+",false,()->{
                try{mins[0]=MissionStore.validatedMinutes(Integer.parseInt(custom.getText().toString()));}catch(Exception ignored){}
                mins[0]=MissionStore.validatedMinutes(mins[0]+1);update.run();
            });
            more.setTextSize(21);control.addView(more,new LinearLayout.LayoutParams(dp(50),dp(46)));
            add(card,control,10);
            add(card,label("1–180 minutes. Type a number or use the controls.",10,Color.rgb(196,214,211),false),5);
            add(card,button("Start my mission",true,()->{
                try{mins[0]=MissionStore.validatedMinutes(Integer.parseInt(custom.getText().toString().trim()));}catch(Exception ignored){}
                broadcast(MissionReceiver.START,app,purpose[0],mins[0]);
            }),14);
            add(card,button("Not now",false,()->broadcast(MissionReceiver.DISMISS,null,null,0)),8);
        }
        add(card,label("This card disappears when you leave the selected app.",10,Color.rgb(180,201,195),false),10);
        ScrollView scroller=new ScrollView(c);scroller.setFillViewport(false);scroller.setVerticalScrollBarEnabled(false);scroller.addView(card);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(
            c.getResources().getDisplayMetrics().widthPixels-dp(26),
            Math.min(dp(expired?340:680),(int)(c.getResources().getDisplayMetrics().heightPixels*.78f)),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;lp.y=dp(40);
        lp.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try{wm.addView(scroller,lp);attached=scroller;showing=key;}catch(Exception ignored){attached=null;showing="";}
    }
    void hide(){if(attached!=null){try{wm.removeViewImmediate(attached);}catch(Exception ignored){}attached=null;}showing="";}
    boolean isShowing(){return attached!=null;}
}