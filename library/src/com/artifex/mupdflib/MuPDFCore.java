package com.artifex.mupdflib;

import android.content.Context;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

public class MuPDFCore {
	/* load our native library */
	static {
		System.loadLibrary("mupdf");
	}

	/* Readable members */
	private int numPages = -1;
	private int displayPages = 1;
	private float pageWidth;
	private float pageHeight;
	private String mFileName;
	private long globals;
	private byte fileBuffer[];
	private String file_format;
	private boolean isUnencryptedPDF;

	/* The native functions */
	private native long openFile(String filename);
	//private native long openBuffer();
	private native long openBuffer(String magic);
	private native String fileFormatInternal();
	private native boolean isUnencryptedPDFInternal();
	private native int countPagesInternal();
	private native void gotoPageInternal(int localActionPageNum);
	private native float getPageWidth();
	private native float getPageHeight();
	private native void drawPage(Bitmap bitmap, int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, long cookiePtr);
	private native void updatePageInternal(Bitmap bitmap, int page, int pageW, int pageH, int patchX, int patchY, int patchW, int patchH, long cookiePtr);
	private native RectF[] searchPage(String text);
	private native TextChar[][][][] text();
	private native byte[] textAsHtml();
	private native void addMarkupAnnotationInternal(PointF[] quadPoints, int type);
	private native void addInkAnnotationInternal(PointF[][] arcs);
	private native void deleteAnnotationInternal(int annot_index);
	private native int passClickEventInternal(int page, float x, float y);
	private native void setFocusedWidgetChoiceSelectedInternal(String[] selected);
	private native String[] getFocusedWidgetChoiceSelected();
	private native String[] getFocusedWidgetChoiceOptions();
	private native int getFocusedWidgetSignatureState();
	private native String checkFocusedSignatureInternal();
	private native boolean signFocusedSignatureInternal(String keyFile, String password);
	private native int setFocusedWidgetTextInternal(String text);
	private native String getFocusedWidgetTextInternal();
	private native int getFocusedWidgetTypeInternal();
	private native LinkInfo[] getPageLinksInternal(int page);
	private native RectF[] getWidgetAreasInternal(int page);
	private native Annotation[] getAnnotationsInternal(int page);
	private native OutlineItem[] getOutlineInternal();
	private native boolean hasOutlineInternal();
	private native boolean needsPasswordInternal();
	private native boolean authenticatePasswordInternal(String password);
	private native MuPDFAlertInternal waitForAlertInternal();
	private native void replyToAlertInternal(MuPDFAlertInternal alert);
	private native void startAlertsInternal();
	private native void stopAlertsInternal();
	private native void destroying();
	private native boolean hasChangesInternal();
	private native void saveInternal();
	private native long createCookie();
	private native void destroyCookie(long cookie);
	private native void abortCookie(long cookie);

	
	public native boolean javascriptSupported();
	
	public class Cookie
	{
		private final long cookiePtr;

		public Cookie()
		{
			cookiePtr = createCookie();
			if (cookiePtr == 0)
				throw new OutOfMemoryError();
		}

		public void abort()
		{
			abortCookie(cookiePtr);
		}

		public void destroy()
		{
			// We could do this in finalize, but there's no guarantee that
			// a finalize will occur before the muPDF context occurs.
			destroyCookie(cookiePtr);
		}
	}


	public enum WidgetType {
		NONE, TEXT, LISTBOX, COMBOBOX, SIGNATURE
	}
	
	public MuPDFCore(Context context, String filename) throws Exception {
		mFileName = filename;
		globals = openFile(filename);
		if (globals == 0) {
			throw new Exception(String.format(context.getString(R.string.cannot_open_file_Path), filename));
		}
		file_format = fileFormatInternal();
		isUnencryptedPDF = isUnencryptedPDFInternal();
	}

	public String getFileName() {
		return mFileName;
	}

	public String getFileDirectory() {
		return (new File(getFileName())).getParent();
	}
	
	//public MuPDFCore(Context context, byte buffer[]) throws Exception {
	public MuPDFCore(Context context, byte buffer[], String magic) throws Exception {

		fileBuffer = buffer;
		//globals = openBuffer();
		globals = openBuffer(magic != null ? magic : "");

		if (globals == 0) {
			throw new Exception(context.getString(R.string.cannot_open_buffer));
		}
		file_format = fileFormatInternal();
		isUnencryptedPDF = isUnencryptedPDFInternal();
	}

	public int countPages() {
		if (numPages < 0)
			numPages = countPagesSynchronized();

		if(displayPages == 1)
			return numPages;
		if(numPages % 2 == 0) {
			return numPages / 2 + 1;
		}
		int toReturn = numPages / 2;
		return toReturn + 1;
	}

	public String fileFormat() {
		return file_format;
	}
	
	public boolean isUnencryptedPDF()
	{
		return isUnencryptedPDF;
	}

	private synchronized int countPagesSynchronized() {
		return countPagesInternal();
	}

	/* Shim function */
	private void gotoPage(int page) {
		if (page > numPages - 1)
			page = numPages - 1;
		else if (page < 0)
			page = 0;
		gotoPageInternal(page);
		this.pageWidth = getPageWidth();
		this.pageHeight = getPageHeight();
	}

	public synchronized PointF getPageSize(int page) {
		if (displayPages == 1) {
			gotoPage(page);
			return new PointF(pageWidth, pageHeight);
		} else {
			gotoPage(page);
			if (page == numPages - 1 || page == 0) {
				// last page
				return new PointF(pageWidth * 2, pageHeight);
			}
			float leftWidth = pageWidth;
			float leftHeight = pageHeight;
			gotoPage(page + 1);
			float screenWidth = leftWidth + pageWidth;
			float screenHeight = Math.max(leftHeight, pageHeight);
			return new PointF(screenWidth, screenHeight);
		}
	}

	public MuPDFAlert waitForAlert() {
		MuPDFAlertInternal alert = waitForAlertInternal();
		return alert != null ? alert.toAlert() : null;
	}

	public void replyToAlert(MuPDFAlert alert) {
		replyToAlertInternal(new MuPDFAlertInternal(alert));
	}

	public void stopAlerts() {
		stopAlertsInternal();
	}

	public void startAlerts() {
		startAlertsInternal();
	}

	public synchronized void onDestroy() {
		destroying();
		globals = 0;
	}
	
	public synchronized PointF getSinglePageSize(int page) {
		gotoPage(page);
		return new PointF(pageWidth, pageHeight);
	}

	public synchronized void drawPageSynchrinized(int page, Bitmap bitmap, int pageW,
			int pageH, int patchX, int patchY, int patchW, int patchH) {
		gotoPage(page);
		///Log.d(TAG,"drawPageSynchrinized page:"+page);
		drawPage(bitmap, pageW, pageH, patchX, patchY, patchW, patchH, 0);
	}
	
	public synchronized void drawSinglePage(int page, Bitmap bitmap, int pageW, int pageH) {
		drawPageSynchrinized(page, bitmap, pageW, pageH, 0, 0, pageW, pageH);
	}
	
	public synchronized void drawPage(Bitmap bm, int page, int pageW, int pageH,
			int patchX, int patchY, int patchW, int patchH, MuPDFCore.Cookie cookie) {
		///gotoPage(page);
		///Bitmap bm = Bitmap.createBitmap(patchW, patchH, Config.ARGB_8888);
		///drawPage(bm, pageW, pageH, patchX, patchY, patchW, patchH);
		///return bm;
		Canvas canvas = null;
		//Bitmap bitmap = null;
		try {
			//bitmap = Bitmap.createBitmap(patchW, patchH, Config.ARGB_8888);
			canvas = new Canvas(bm);
			canvas.drawColor(Color.TRANSPARENT);
			///Log.d(TAG, "drawPage "+page);
			
			///Log.d(TAG,"canvas: "+canvas);
			if (displayPages == 1) {
				gotoPage(page);
				drawPage(bm, pageW, pageH, patchX, patchY, patchW, patchH, cookie.cookiePtr);
				//return bitmap;
			} else {
				final int drawPage = (page == 0) ? 0 : page * 2 - 1;
				int leftPageW = pageW / 2;
				int rightPageW = pageW - leftPageW;

				// If patch overlaps both bitmaps (left and right) - return the
				// width of overlapping left bitpam part of the patch
				// or return full patch width if it's fully inside left bitmap
				int leftBmWidth = Math.min(leftPageW, leftPageW - patchX);

				// set left Bitmap width to zero if patch is fully overlay right
				// Bitmap
				leftBmWidth = (leftBmWidth < 0) ? 0 : leftBmWidth;

				// set the right part of the patch width, as a rest of the patch
				int rightBmWidth = patchW - leftBmWidth;

				Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
				
				if (drawPage == numPages - 1) {
					// draw only left page
					canvas.drawColor(Color.BLACK);
					if (leftBmWidth > 0) {
						Bitmap leftBm = Bitmap.createBitmap(leftBmWidth, patchH, Config.ARGB_8888);
						gotoPage(drawPage);
						drawPage(leftBm, leftPageW, pageH, (leftBmWidth == 0) ? patchX - leftPageW : 0, patchY, leftBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap leftBmRGB = leftBm.copy(Bitmap.Config.RGB_565, true);
						//if (leftBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(leftBm, 0, 0, paint);
						leftBm.recycle();
					}
				} else if (drawPage == 0) {
					// draw only right page
					canvas.drawColor(Color.BLACK);
					if (rightBmWidth > 0) {
						Bitmap rightBm = Bitmap.createBitmap(rightBmWidth, patchH, Config.ARGB_8888);
						gotoPage(drawPage);
						drawPage(rightBm, rightPageW, pageH, (leftBmWidth == 0) ? patchX - leftPageW : 0, patchY, rightBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap rightBmRGB = rightBm.copy(Bitmap.Config.RGB_565, true);
						//if (rightBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(rightBm, (float) leftBmWidth, 0, paint);
						rightBm.recycle();
					}
				} else {
					// Need to draw two pages one by one: left and right
					Log.d("draw bitmap width", "" + bm.getWidth());
					//canvas.drawColor(Color.BLACK);
					if (leftBmWidth > 0) {
						Bitmap leftBm = Bitmap.createBitmap(leftBmWidth, patchH, Config.ARGB_8888);
						gotoPage(drawPage);
						drawPage(leftBm, leftPageW, pageH, patchX, patchY, leftBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap leftBmRGB = leftBm.copy(Bitmap.Config.RGB_565, true);
						//if (leftBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(leftBm, 0, 0, paint);
						leftBm.recycle();
					}
					if (rightBmWidth > 0) {
						Bitmap rightBm = Bitmap.createBitmap(rightBmWidth, patchH, Config.ARGB_8888);
						gotoPage(drawPage+1);
						drawPage(rightBm, rightPageW, pageH, (leftBmWidth == 0) ? patchX - leftPageW : 0, patchY, rightBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap rightBmRGB = rightBm.copy(Bitmap.Config.RGB_565, true);
						//if (rightBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(rightBm, (float) leftBmWidth, 0, paint);
						rightBm.recycle();
					}

				}
				//return bitmap;
			}
		} catch (OutOfMemoryError e) {
			///Log.e(TAG, "draw page " + page + "failed", e);
			if(canvas != null) canvas.drawColor(Color.TRANSPARENT);
			//return bitmap;
		}
	}

	public synchronized void updatePage(Bitmap bm, int page, int pageW,
			int pageH, int patchX, int patchY, int patchW, int patchH, MuPDFCore.Cookie cookie) {

		Canvas canvas = null;
		
		try {
			canvas = new Canvas(bm);
			canvas.drawColor(Color.TRANSPARENT);
			///Log.d(TAG,"canvas: "+canvas);
			if (displayPages == 1) {
				
				updatePageInternal(bm, page, pageW, pageH, patchX, patchY, patchW, patchH, cookie.cookiePtr);
				//return bitmap;
			} else {
				page = (page == 0) ? 0 : page * 2 - 1;
				int leftPageW = pageW / 2;
				int rightPageW = pageW - leftPageW;

				// If patch overlaps both bitmaps (left and right) - return the
				// width of overlapping left bitpam part of the patch
				// or return full patch width if it's fully inside left bitmap
				int leftBmWidth = Math.min(leftPageW, leftPageW - patchX);

				// set left Bitmap width to zero if patch is fully overlay right
				// Bitmap
				leftBmWidth = (leftBmWidth < 0) ? 0 : leftBmWidth;

				// set the right part of the patch width, as a rest of the patch
				int rightBmWidth = patchW - leftBmWidth;

				Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
				
				if (page == numPages - 1) {
					// draw only left page
					if (leftBmWidth > 0) {
						Bitmap leftBm = Bitmap.createBitmap(bm, 0, 0, leftBmWidth, patchH);
						updatePageInternal(leftBm, page, leftPageW, pageH, (leftBmWidth == 0) ? patchX - leftPageW : 0, patchY, leftBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap leftBmRGB = leftBm.copy(Bitmap.Config.RGB_565, true);
						//if (leftBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(leftBm, 0, 0, paint);
						leftBm.recycle();
					}
				} else if (page == 0) {
					// draw only right page
					if (rightBmWidth > 0) {
						Bitmap rightBm = Bitmap.createBitmap(bm, leftBmWidth, 0, rightBmWidth, patchH);
						updatePageInternal(rightBm, page, rightPageW, pageH, (leftBmWidth == 0) ? patchX - leftPageW : 0, patchY, rightBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap rightBmRGB = rightBm.copy(Bitmap.Config.RGB_565, true);
						//if (rightBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(rightBm, (float) leftBmWidth, 0, paint);
						rightBm.recycle();
					}
				} else {
					// Need to draw two pages one by one: left and right
					Log.d("update bitmap width", "" + bm.getWidth());
					if (leftBmWidth > 0) {
						Bitmap leftBm = Bitmap.createBitmap(bm, 0, 0, (leftBmWidth < bm.getWidth()) ? leftBmWidth : bm.getWidth(), patchH);
						updatePageInternal(leftBm, page, leftPageW, pageH, patchX, patchY, leftBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap leftBmRGB = leftBm.copy(Bitmap.Config.RGB_565, true);
						//if (leftBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(leftBm, 0, 0, paint);
						leftBm.recycle();
					}
					if (rightBmWidth > 0) {
						Bitmap rightBm = Bitmap.createBitmap(bm, leftBmWidth, 0, rightBmWidth, patchH);
						updatePageInternal(rightBm, page+1, rightPageW, pageH, (leftBmWidth == 0) ? patchX - leftPageW : 0, patchY, rightBmWidth, patchH, cookie.cookiePtr);
						
						//optimize bitmap format
						//Bitmap rightBmRGB = rightBm.copy(Bitmap.Config.RGB_565, true);
						//if (rightBmRGB == null) throw new RuntimeException("bitmap copy failed");
						
						canvas.drawBitmap(rightBm, (float) leftBmWidth, 0, paint);
						rightBm.recycle();
					}

				}
				//return bitmap;
			}
		} catch (OutOfMemoryError e) {
			///Log.e(TAG, "update page " + page + "failed", e);
			if(canvas != null) canvas.drawColor(Color.TRANSPARENT);
			//return bitmap;
		}
	}

	public synchronized PassClickResult passClickEvent(int page, float x, float y) {
		boolean changed = passClickEventInternal(page, x, y) != 0;

		switch (WidgetType.values()[getFocusedWidgetTypeInternal()]) {
		case TEXT:
			return new PassClickResultText(changed, getFocusedWidgetTextInternal());
		case LISTBOX:
		case COMBOBOX:
			return new PassClickResultChoice(changed, getFocusedWidgetChoiceOptions(), getFocusedWidgetChoiceSelected());
		case SIGNATURE:
			return new PassClickResultSignature(changed, getFocusedWidgetSignatureState());
		default:
			return new PassClickResult(changed);
		}

	}

	public synchronized boolean setFocusedWidgetText(int page, String text) {
		boolean success;
		gotoPage(page);
		success = setFocusedWidgetTextInternal(text) != 0 ? true : false;

		return success;
	}

	public synchronized void setFocusedWidgetChoiceSelected(String[] selected) {
		setFocusedWidgetChoiceSelectedInternal(selected);
	}
	
	public synchronized String checkFocusedSignature() {
		return checkFocusedSignatureInternal();
	}
	
	public synchronized boolean signFocusedSignature(String keyFile, String password) {
		return signFocusedSignatureInternal(keyFile, password);
	}

	public synchronized LinkInfo[] getPageLinks(int page) {
		///return getPageLinksInternal(page);
		if(displayPages == 1)
			return getPageLinksInternal(page);
		LinkInfo[] leftPageLinkInfo = new LinkInfo[0];
		LinkInfo[] rightPageLinkInfo = new LinkInfo[0];
		LinkInfo[] combinedLinkInfo;
		int combinedSize = 0;
		int rightPage = page * 2;
		int leftPage = rightPage - 1;
		int count = countPages() * 2;
		if( leftPage > 0 ) {
			LinkInfo[] leftPageLinkInfoInternal = getPageLinksInternal(leftPage);
			if (null != leftPageLinkInfoInternal) {
				leftPageLinkInfo = leftPageLinkInfoInternal;
				combinedSize += leftPageLinkInfo.length;
			}
		}
		if( rightPage < count ) {
			LinkInfo[] rightPageLinkInfoInternal = getPageLinksInternal(rightPage);
			if (null != rightPageLinkInfoInternal) {
				rightPageLinkInfo = rightPageLinkInfoInternal;
				combinedSize += rightPageLinkInfo.length;
			}
		}
		
		combinedLinkInfo = new LinkInfo[combinedSize];
		for(int i = 0; i < leftPageLinkInfo.length; i++) {
			combinedLinkInfo[i] = leftPageLinkInfo[i];
		}
		
		LinkInfo temp;
		for(int i = 0, j = leftPageLinkInfo.length; i < rightPageLinkInfo.length; i++, j++) {
			temp = rightPageLinkInfo[i];
			temp.rect.left += pageWidth;
			temp.rect.right += pageWidth;
			combinedLinkInfo[j] = temp;
		}

		return combinedLinkInfo;
	}

	public synchronized RectF[] getWidgetAreas(int page) {
		return getWidgetAreasInternal(page);
	}

	public synchronized Annotation[] getAnnoations(int page) {
		return getAnnotationsInternal(page);
	}

	public synchronized RectF[] searchPage(int page, String text) {
		gotoPage(page);
		return searchPage(text);
	}

	public synchronized byte[] html(int page) {
		gotoPage(page);
		return textAsHtml();
	}

	public synchronized TextWord[][] textLines(int page) {
		gotoPage(page);
		TextChar[][][][] chars = text();

		// The text of the page held in a hierarchy (blocks, lines, spans).
		// Currently we don't need to distinguish the blocks level or
		// the spans, and we need to collect the text into words.
		ArrayList<TextWord[]> lns = new ArrayList<TextWord[]>();

		for (TextChar[][][] bl : chars) {
			if (bl == null)
				continue;
			for (TextChar[][] ln : bl) {
				ArrayList<TextWord> wds = new ArrayList<TextWord>();
				TextWord wd = new TextWord();

				for (TextChar[] sp : ln) {
					for (TextChar tc : sp) {
						if (tc.c != ' ') {
							wd.Add(tc);
						} else if (wd.w.length() > 0) {
							wds.add(wd);
							wd = new TextWord();
						}
					}
				}

				if (wd.w.length() > 0)
					wds.add(wd);

				if (wds.size() > 0)
					lns.add(wds.toArray(new TextWord[wds.size()]));
			}
		}

		return lns.toArray(new TextWord[lns.size()][]);
	}

	public synchronized void addMarkupAnnotation(int page, PointF[] quadPoints, Annotation.Type type) {
		gotoPage(page);
		addMarkupAnnotationInternal(quadPoints, type.ordinal());
	}

	public synchronized void addInkAnnotation(int page, PointF[][] arcs) {
		gotoPage(page);
		addInkAnnotationInternal(arcs);
	}

	public synchronized void deleteAnnotation(int page, int annot_index) {
		gotoPage(page);
		deleteAnnotationInternal(annot_index);
	}

	public synchronized boolean hasOutline() {
		return hasOutlineInternal();
	}

	public synchronized OutlineItem[] getOutline() {
		return getOutlineInternal();
	}

	public synchronized boolean needsPassword() {
		return needsPasswordInternal();
	}

	public synchronized boolean authenticatePassword(String password) {
		return authenticatePasswordInternal(password);
	}

	public synchronized boolean hasChanges() {
		return hasChangesInternal();
	}

	public synchronized void save() {
		saveInternal();
	}
	
	public int getDisplayPages() {
		return displayPages;
	}
	
	public int countDisplays() {
		int pages = countPages();
		if(pages % 2 == 0) {
			return pages / 2 + 1;
		} else 
			return pages / 2;
	}
	
	public void setDisplayPages(int pages) throws IllegalStateException {
		if(pages <=0 || pages > 2) {
			throw new IllegalStateException("MuPDFCore can only handle 1 or 2 pages per screen!");
		}
		displayPages = pages;
	}
	/**
	 * @return
	 */
	public int countSinglePages() {
		return numPages;
	}
}
