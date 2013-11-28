package com.artifex.mupdflib;

import java.util.Locale;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;

public class LibraryUtils {
	private static String PREF_APP_LANGUAGE = "prefKeyLanguage";
    private static Locale locale = null;
    public static void reloadLocale(Context context)
    {    	
    	SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String lang = sharedPrefs.getString(PREF_APP_LANGUAGE, "lv");
        if (sharedPrefs.getString(PREF_APP_LANGUAGE, "").equalsIgnoreCase("")) {
        	sharedPrefs.edit().putString(PREF_APP_LANGUAGE, lang).commit();
        }
        Configuration newConfig = context.getResources().getConfiguration();
        
        if (! newConfig.locale.getLanguage().equals(lang))
        {
            locale = new Locale(lang);
        }
        
        if (locale != null)
        {
            Locale.setDefault(locale);
            newConfig.locale = locale;
            context.getResources().updateConfiguration(newConfig, context.getResources().getDisplayMetrics());
        }
    }
}
