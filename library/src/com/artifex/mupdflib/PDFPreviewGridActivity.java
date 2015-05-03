package com.artifex.mupdflib;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;

public class PDFPreviewGridActivity extends Activity {
	private MuPDFCore mCore;
	private int mPosition;
	private GridView mGrid;
	private PDFPreviewGridAdapter mAdapter;
	
	@SuppressLint("NewApi")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
	        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
	        getWindow().setStatusBarColor(getResources().getColor(R.color.actionbar_background_dark));
	        getWindow().setNavigationBarColor(getResources().getColor(R.color.actionbar_background_dark));
	    }
		
		LibraryUtils.reloadLocale(getApplicationContext());
		
		mCore = PDFPreviewGridActivityData.get().core;
		mPosition = PDFPreviewGridActivityData.get().position;
		
		setContentView(R.layout.preview_grid_fragment);
		
		mGrid = (GridView)findViewById(R.id.preview_grid);

		mAdapter = new PDFPreviewGridAdapter(this, mCore, mPosition);
		mGrid.setAdapter(mAdapter);
		mGrid.smoothScrollToPosition(mPosition);
	}

	public void OnCancelPreviewButtonClick(View v) {
		setResult(mPosition);
		finish();
	}

}
