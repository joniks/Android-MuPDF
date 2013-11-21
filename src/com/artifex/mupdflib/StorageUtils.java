package com.artifex.mupdflib;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

/**
 * Provides application storage paths
 * 
 * @author Janis Kepulis
 * @since 1.0.0
 */
public final class StorageUtils {

	private StorageUtils() {
	}

	/**
	 * Returns application cache directory. Cache directory will be created on SD card
	 * <i>("/Android/data/[app_package_name]/cache")</i> if card is mounted. Else - Android defines cache directory on
	 * device's file system.
	 * 
	 * @param context Application context
	 * @return Cache {@link File directory}
	 */
	public static File getCacheDirectory(Context context) {
		File appCacheDir = null;
		if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
			appCacheDir = getExternalCacheDir(context);
		}
		if (appCacheDir == null) {
			appCacheDir = context.getCacheDir();
		}
		return appCacheDir;
	}

	/**
	 * Returns application files directory. Files directory will be created on SD card
	 * <i>("/Android/data/[app_package_name]/files")</i> if card is mounted. Else - Android defines files directory on
	 * device's file system.
	 * 
	 * @param context Application context
	 * @return Files {@link File directory}
	 */
	public static File getFilesDirectory(Context context) {
		File appFilesDir = null;
		if (Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
			appFilesDir = getExternalFilesDir(context);
		}
		if (appFilesDir == null) {
			appFilesDir = context.getFilesDir();
		}
		return appFilesDir;
	}
	
	public static File getCacheSubDirectory(Context context, String cacheSubDir) {
		File appCacheSubDir = new File(getCacheDirectory(context),cacheSubDir);
		
		if (!appCacheSubDir.exists())
			appCacheSubDir.mkdirs();
		
		return appCacheSubDir;
	}
	
	public static File getFilesSubDirectory(Context context, String filesSubDir) {
		File appFilesSubDir = new File(getFilesDirectory(context),filesSubDir);
		
		if (!appFilesSubDir.exists())
			appFilesSubDir.mkdirs();
		//String filename = String.valueOf(url.hashCode());
		// Another possible solution (thanks to grantland)
		// String filename;
		/*
		 * try { filename = URLEncoder.encode(url, "UTF-8"); } catch
		 * (UnsupportedEncodingException e) {
		 * e.printStackTrace(); }
		 */
		return appFilesSubDir;
	}
	
	private static File getExternalCacheDir(Context context) {
		File dataDir = new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data");
		File appCacheDir = new File(new File(dataDir, context.getPackageName()), "cache");
		if (!appCacheDir.exists()) {
			if (!appCacheDir.mkdirs()) {
				//L.w("Unable to create external cache directory");
				return null;
			}
			try {
				new File(appCacheDir, ".nomedia").createNewFile();
			} catch (IOException e) {
				//L.i("Can't create \".nomedia\" file in application external cache directory");
				return null;
			}
		}
		return appCacheDir;
	}
	
	private static File getExternalFilesDir(Context context) {
		File dataDir = new File(new File(Environment.getExternalStorageDirectory(), "Android"), "data");
		File appFilesDir = new File(new File(dataDir, context.getPackageName()), "files");
		if (!appFilesDir.exists()) {
			if (!appFilesDir.mkdirs()) {
				//L.w("Unable to create external cache directory");
				return null;
			}
			try {
				new File(appFilesDir, ".nomedia").createNewFile();
			} catch (IOException e) {
				//L.i("Can't create \".nomedia\" file in application external cache directory");
				return null;
			}
		}
		return appFilesDir;
	}
}
