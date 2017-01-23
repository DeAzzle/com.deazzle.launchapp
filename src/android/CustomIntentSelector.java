package com.deazzle.launchapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class CustomIntentSelector {
    public static final String strTAG = "CustomIntentSelector";
    public static Intent create(PackageManager pm, Intent target, String title,
                                List<String> banksWhiteList) {
        Intent localIntent = new Intent(target.getAction());
        localIntent.setData(target.getData());
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(localIntent, 0);
        List<HashMap<String, String>> packageInfo = new ArrayList<HashMap<String, String>>();
        for (ResolveInfo resolveInfo : resolveInfos) {
            if (resolveInfo.activityInfo == null || !banksWhiteList.contains(resolveInfo.activityInfo.packageName)) {
                continue;
            }

            HashMap<String, String> localPackInfo = new HashMap<String, String>();
            localPackInfo.put("packageName", resolveInfo.activityInfo.packageName);
            localPackInfo.put("className", resolveInfo.activityInfo.name);
            localPackInfo.put("simpleName", String.valueOf(resolveInfo.activityInfo.loadLabel(pm)));
            Log.d(strTAG, "The local package is " + localPackInfo.toString());
            packageInfo.add(localPackInfo);
        }
        if (packageInfo.isEmpty()) {
            Intent rawEmptyIntent = (Intent) target.clone();
            rawEmptyIntent.setPackage("test.packagename.com");
            rawEmptyIntent.setClassName("test.packagename.com", "NonExistingActivity");
            return Intent.createChooser(rawEmptyIntent, title);
        }
        //Names of the apps to be displayed
        Collections.sort(packageInfo, new Comparator<HashMap<String, String>>() {
            @Override
            public int compare(HashMap<String, String> orig, HashMap<String, String> compare) {
                return orig.get("simpleName").compareTo(compare.get("simpleName"));
            }
        });

        List<Intent> intentsForBanks = new ArrayList<Intent>();
        for (HashMap<String, String> informationMeta : packageInfo) {
            Intent bankAppsIntent = (Intent) target.clone();
            bankAppsIntent.setPackage(informationMeta.get("packageName"));
            bankAppsIntent.setClassName(informationMeta.get("packageName"), informationMeta.get("className"));
            intentsForBanks.add(bankAppsIntent);
        }
        Intent chooserIntent = Intent.createChooser(intentsForBanks.get(0), title);
        intentsForBanks.remove(0);
        Parcelable[] intentsForAuthenticBanks =
                intentsForBanks.toArray(new Parcelable[intentsForBanks.size()]);

        //Needs to be used modifying the intents
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentsForAuthenticBanks);
        return chooserIntent;
    }

}
