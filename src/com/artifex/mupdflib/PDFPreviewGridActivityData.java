package com.artifex.mupdflib;

public class PDFPreviewGridActivityData {
	public MuPDFCore core;
	public int position;
	static private PDFPreviewGridActivityData singleton;

	static public void set(PDFPreviewGridActivityData d) {
		singleton = d;
	}

	static public PDFPreviewGridActivityData get() {
		if (singleton == null)
			singleton = new PDFPreviewGridActivityData();
		return singleton;
	}
}
