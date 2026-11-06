package com.missionmode.android;

import android.content.Context;
import android.content.Intent;

/** Supported native applications only. Websites opened in browsers are not detected. */
public final class TrackedApps {
    public static final class Entry {
        public final String name;
        public final String[] packages;
        Entry(String n,String... pkgs){name=n;packages=pkgs;}
    }
    public static final Entry[] ALL={
        new Entry("Instagram","com.instagram.android"),
        new Entry("TikTok","com.zhiliaoapp.musically","com.ss.android.ugc.trill"),
        new Entry("Snapchat","com.snapchat.android"),
        new Entry("YouTube","com.google.android.youtube")
    };
    public static final String[] PURPOSES={
        "Reply to messages","Post content","Research","Learn something","Take a break","Other"
    };
    private TrackedApps(){}
    public static Entry byName(String s){for(Entry e:ALL)if(e.name.equals(s))return e;return ALL[0];}
    public static Entry byPackage(String pkg){
        if(pkg==null)return null;
        for(Entry e:ALL)for(String p:e.packages)if(p.equals(pkg))return e;
        return null;
    }
    public static String cleanPurpose(String s){
        if(s==null)return PURPOSES[0];
        for(String p:PURPOSES)if(p.equals(s))return p;
        return PURPOSES[0];
    }
    public static Intent launch(Context c,String name){
        for(String pkg:byName(name).packages){
            Intent i=c.getPackageManager().getLaunchIntentForPackage(pkg);
            if(i!=null)return i;
        }
        return null;
    }
}
