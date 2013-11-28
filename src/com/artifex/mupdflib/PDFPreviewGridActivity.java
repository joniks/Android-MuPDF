package com.artifex.mupdflib;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;

public class PDFPreviewGridActivity extends Activity {
	private MuPDFCore mCore;
	private int mPosition;
	private GridView mGrid;
	private PDFPreviewGridAdapter mAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
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
