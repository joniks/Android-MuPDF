package com.example.mupdfsample;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {
	private static final String TEST_FILE_NAME = "sample.pdf";
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
				
		File fo = getFilesDirectory(getApplicationContext());
		File fi = new File(fo, TEST_FILE_NAME);
		

		if (!fi.exists()) {
			copyTestDocToSdCard(fi);
		}
		
		Button bt = (Button)findViewById(R.id.button1);
        bt.setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		if (v == findViewById(R.id.button1)) {
			SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			sharedPrefs.edit().putString("prefKeyLanguage", "en").commit();

			File fo = getFilesDirectory(getApplicationContext());
			File fi = new File(fo, TEST_FILE_NAME);
			Uri uri = Uri.parse(fi.getAbsolutePath());
			
			//Uri uri = Uri.parse("file:///android_asset/" + TEST_FILE_NAME);
			Intent intent = new Intent(this, com.artifex.mupdflib.MuPDFActivity.class);
			intent.setAction(Intent.ACTION_VIEW);
			intent.setData(uri);
			
			//if document protected with password
			intent.putExtra("password", "PDF document password");

			//if you need highlight link boxes
			intent.putExtra("linkhighlight", true);

			//if you don't need device sleep on reading document
			intent.putExtra("idleenabled", false);

			//document name
			intent.putExtra("docname", "PDF document file name");
			
			startActivity(intent);
		}
	}
	
	private void copyTestDocToSdCard(final File testImageOnSdCard) {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					InputStream is = getAssets().open(TEST_FILE_NAME);
					FileOutputStream fos = new FileOutputStream(testImageOnSdCard);
					byte[] buffer = new byte[8192];
					int read;
					try {
						while ((read = is.read(buffer)) != -1) {
							fos.write(buffer, 0, read);
						}
					} finally {
						fos.flush();
						fos.close();
						is.close();
					}
				} catch (IOException e) {
					
				}
			}
		}).start();
	}
	
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
