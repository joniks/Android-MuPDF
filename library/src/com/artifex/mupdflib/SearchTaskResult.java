package com.artifex.mupdflib;

import android.graphics.RectF;

public class SearchTaskResult {
	public final String txt;
	public final int pageNumber;
	public final RectF searchBoxes[];
	public final RectF searchBoxesPrim[];
	static private SearchTaskResult singleton;

	SearchTaskResult(String _txt, int _pageNumber, RectF _searchBoxes[], RectF _searchBoxesPrim[]) {
		txt = _txt;
		pageNumber = _pageNumber;
		searchBoxes = _searchBoxes;
		searchBoxesPrim = _searchBoxesPrim;
	}

	static public SearchTaskResult get() {
		return singleton;
	}

	static public void set(SearchTaskResult r) {
		singleton = r;
	}
}
