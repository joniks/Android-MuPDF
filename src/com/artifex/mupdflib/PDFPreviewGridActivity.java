package com.artifex.mupdflib;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.GridView;

public class PDFPreviewGridActivity extends Activity {
	private MuPDFCore mCore;
	private GridView mGrid;
	private PDFPreviewGridAdapter mAdapter;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		mCore = PDFPreviewGridActivityData.get().core;
		
		setContentView(R.layout.preview_grid_fragment);
		
		mGrid = (GridView)findViewById(R.id.preview_grid);

		mAdapter = new PDFPreviewGridAdapter(this, mCore);
		mGrid.setAdapter(mAdapter);
	}

	public void OnCancelPreviewButtonClick(View v) {
		finish();
	}

}
