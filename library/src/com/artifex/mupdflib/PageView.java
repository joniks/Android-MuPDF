package com.artifex.mupdflib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.*;
import android.graphics.Bitmap.Config;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Iterator;
/*
class PatchInfo {
	//public BitmapHolder bmh;
	//public Bitmap bm;
	public Point patchViewSize;
	public Rect patchArea;
	public boolean completeRedraw;

	public PatchInfo(Point aPatchViewSize, Rect aPatchArea, boolean aCompleteRedraw) {
		//bmh = aBmh;
		//bm = null;
		patchViewSize = aPatchViewSize;
		patchArea = aPatchArea;
		completeRedraw = aCompleteRedraw;
	}
}
*/
// Make our ImageViews opaque to optimize redraw
class OpaqueImageView extends ImageView {

	public OpaqueImageView(Context context) {
		super(context);
	}

	@Override
	public boolean isOpaque() {
		return true;
	}
}

interface TextProcessor {
	void onStartLine();

	void onWord(TextWord word);

	void onEndLine();
}

class TextSelector {
	final private TextWord[][] mText;
	final private RectF mSelectBox;

	public TextSelector(TextWord[][] text, RectF selectBox) {
		mText = text;
		mSelectBox = selectBox;
	}

	public void select(TextProcessor tp) {
		if (mText == null || mSelectBox == null)
			return;

		ArrayList<TextWord[]> lines = new ArrayList<TextWord[]>();
		for (TextWord[] line : mText)
			if (line[0].bottom > mSelectBox.top && line[0].top < mSelectBox.bottom)
				lines.add(line);

		Iterator<TextWord[]> it = lines.iterator();
		while (it.hasNext()) {
			TextWord[] line = it.next();
			boolean firstLine = line[0].top < mSelectBox.top;
			boolean lastLine = line[0].bottom > mSelectBox.bottom;
			float start = Float.NEGATIVE_INFINITY;
			float end = Float.POSITIVE_INFINITY;

			if (firstLine && lastLine) {
				start = Math.min(mSelectBox.left, mSelectBox.right);
				end = Math.max(mSelectBox.left, mSelectBox.right);
			} else if (firstLine) {
				start = mSelectBox.left;
			} else if (lastLine) {
				end = mSelectBox.right;
			}

			tp.onStartLine();

			for (TextWord word : line)
				if (word.right > start && word.left < end)
					tp.onWord(word);

			tp.onEndLine();
		}
	}
}

public abstract class PageView extends ViewGroup {
	private static final int HIGHLIGHT_COLOR = 0x802572AC;
	private static final int LINK_COLOR = 0x20AC7225;
	private static final int BOX_COLOR = 0xFF4444FF;
	private static final int INK_COLOR = 0xFFFF0000;
	private static final float INK_THICKNESS = 10.0f;
	private static final int BACKGROUND_COLOR = 0xFFFFFFFF;
	private static final int PROGRESS_DIALOG_DELAY = 200;
	protected final Context mContext;
	protected int mPageNumber;
	private Point mParentSize;
	protected Point mSize; // Size of page at minimum zoom
	protected float mSourceScale;

	private ImageView mEntire; // Image rendered at minimum zoom
	//private BitmapHolder mEntireBmh;
	private       Bitmap    mEntireBm;
	private       Matrix    mEntireMat;
	private AsyncTask<Void, Void, TextWord[][]> mGetText;
	private AsyncTask<Void, Void, LinkInfo[]> mGetLinkInfo;
	//private AsyncTask<Void, Void, Bitmap> mDrawEntire;
	//private       AsyncTask<Void,Void,Void> mDrawEntire;
	private       CancellableAsyncTask<Void, Void> mDrawEntire;



	private Point mPatchViewSize; // View size on the basis of which the patch
									// was created
	private Rect mPatchArea;
	private ImageView mPatch;
	//private BitmapHolder mPatchBmh;
	private       Bitmap    mPatchBm;
	//private AsyncTask<PatchInfo, Void, PatchInfo> mDrawPatch;
	private       CancellableAsyncTask<Void,Void> mDrawPatch;

	private RectF mSearchBoxes[];
	private RectF mSearchBoxesPrim[];
	protected LinkInfo mLinks[];
	private RectF mSelectBox;
	private TextWord mText[][];
	private RectF mItemSelectBox;
	protected ArrayList<ArrayList<PointF>> mDrawing;
	private View mSearchView;
	private boolean mIsBlank;
	private boolean mHighlightLinks;

	private ProgressBar mBusyIndicator;
	private final Handler mHandler = new Handler();

	//public PageView(Context c, Point parentSize) {
	public PageView(Context c, Point parentSize, Bitmap sharedHqBm) {

		super(c);
		mContext = c;
		mParentSize = parentSize;
		setBackgroundColor(BACKGROUND_COLOR);
		//mEntireBmh = new BitmapHolder();
		//mPatchBmh = new BitmapHolder();
		try {
			mEntireBm = Bitmap.createBitmap(parentSize.x, parentSize.y, Config.ARGB_8888);
		}
		catch ( OutOfMemoryError e) {
			Log.d("MY_OOM_ERROR", "error");
		}
		
		mPatchBm = sharedHqBm;
		mEntireMat = new Matrix();

	}

	//protected abstract Bitmap drawPage(int sizeX, int sizeY, int patchX,
	//		int patchY, int patchWidth, int patchHeight);

	//protected abstract Bitmap updatePage(BitmapHolder h, int sizeX, int sizeY,
	//		int patchX, int patchY, int patchWidth, int patchHeight);
	//protected abstract void drawPage(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
	//protected abstract void updatePage(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
	protected abstract CancellableTaskDefinition<Void, Void> getDrawPageTask(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);
	protected abstract CancellableTaskDefinition<Void, Void> getUpdatePageTask(Bitmap bm, int sizeX, int sizeY, int patchX, int patchY, int patchWidth, int patchHeight);

	protected abstract LinkInfo[] getLinkInfo();

	protected abstract TextWord[][] getText();

	protected abstract void addMarkup(PointF[] quadPoints, Annotation.Type type);

	private void reinit() {
		// Cancel pending render task
		if (mDrawEntire != null) {
			//mDrawEntire.cancel(true);
			mDrawEntire.cancelAndWait();
			mDrawEntire = null;
		}

		if (mDrawPatch != null) {
			//mDrawPatch.cancel(true);
			mDrawPatch.cancelAndWait();
			mDrawPatch = null;
		}

		if (mGetLinkInfo != null) {
			mGetLinkInfo.cancel(true);
			mGetLinkInfo = null;
		}

		if (mGetText != null) {
			mGetText.cancel(true);
			mGetText = null;
		}

		mIsBlank = true;
		mPageNumber = 0;

		if (mSize == null)
			mSize = mParentSize;

		if (mEntire != null) {
			mEntire.setImageBitmap(null);
			//mEntireBmh.setBm(null);
			mEntire.invalidate();
		}

		if (mPatch != null) {
			mPatch.setImageBitmap(null);
			//mPatchBmh.setBm(null);
			mPatch.invalidate();
		}

		mPatchViewSize = null;
		mPatchArea = null;

		mSearchBoxes = null;
		mSearchBoxesPrim = null;
		mLinks = null;
		mSelectBox = null;
		mText = null;
		mItemSelectBox = null;
	}

	public void releaseResources() {
		reinit();

		if (mBusyIndicator != null) {
			removeView(mBusyIndicator);
			mBusyIndicator = null;
		}
	}
	
	public void releaseBitmaps() {
		reinit();
		mEntireBm = null;
		mPatchBm = null;
	}

	public void blank(int page) {
		reinit();
		mPageNumber = page;

		if (mBusyIndicator == null) {
			mBusyIndicator = new ProgressBar(mContext);
			mBusyIndicator.setIndeterminate(true);
			mBusyIndicator.setBackgroundResource(R.drawable.busy);
			addView(mBusyIndicator);
		}
		setBackgroundColor(BACKGROUND_COLOR);
	}

	public void setPage(int page, PointF size) {
		// Cancel pending render task
		if (mDrawEntire != null) {
			//mDrawEntire.cancel(true);
			mDrawEntire.cancelAndWait();
			mDrawEntire = null;
		}

		mIsBlank = false;
		// Highlights may be missing because mIsBlank was true on last draw
		if (mSearchView != null)
			mSearchView.invalidate();

		mPageNumber = page;
		if (mEntire == null) {
			mEntire = new OpaqueImageView(mContext);
			//mEntire.setScaleType(ImageView.ScaleType.FIT_CENTER);
			mEntire.setScaleType(ImageView.ScaleType.MATRIX);
			addView(mEntire);
		}
 
		// Calculate scaled size that fits within the screen limits
		// This is the size at minimum zoom
		mSourceScale = Math.min(mParentSize.x / size.x, mParentSize.y / size.y);
		Point newSize = new Point((int) (size.x * mSourceScale),
				(int) (size.y * mSourceScale));
		mSize = newSize;

		mEntire.setImageBitmap(null);
		//mEntireBmh.setBm(null);
		mEntire.invalidate();

		// Get the link info in the background
		mGetLinkInfo = new AsyncTask<Void, Void, LinkInfo[]>() {
			protected LinkInfo[] doInBackground(Void... v) {
				return getLinkInfo();
			}

			protected void onPostExecute(LinkInfo[] v) {
				mLinks = v;
				//invalidate();
				if (mSearchView != null)
					mSearchView.invalidate();

			}
		};

		mGetLinkInfo.execute();

		// Render the page in the background
		//mDrawEntire = new AsyncTask<Void, Void, Void>() {
		//	protected Void doInBackground(Void... v) {
		//		drawPage(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
		//		return null;
		//	}
		mDrawEntire = new CancellableAsyncTask<Void, Void>(getDrawPageTask(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y)) {
			
			//protected void onPreExecute() {
			@Override
			public void onPreExecute() {
				setBackgroundColor(BACKGROUND_COLOR);
				mEntire.setImageBitmap(null);
				//mEntireBmh.setBm(null);
				mEntire.invalidate();

				if (mBusyIndicator == null) {
					mBusyIndicator = new ProgressBar(mContext);
					mBusyIndicator.setIndeterminate(true);
					mBusyIndicator.setBackgroundResource(R.drawable.busy);
					addView(mBusyIndicator);
					mBusyIndicator.setVisibility(INVISIBLE);
					mHandler.postDelayed(new Runnable() {
						public void run() {
							if (mBusyIndicator != null)
								mBusyIndicator.setVisibility(VISIBLE);
						}
					}, PROGRESS_DIALOG_DELAY);
				}
			}

			//protected void onPostExecute(Void v) {
			@Override
			public void onPostExecute(Void result) {
				removeView(mBusyIndicator);
				mBusyIndicator = null;
				//mEntire.setImageBitmap(bm);
				//mEntireBmh.setBm(bm);
				mEntire.setImageBitmap(mEntireBm);
				mEntire.invalidate();
				//invalidate();
				setBackgroundColor(Color.TRANSPARENT);
			}
		};

		mDrawEntire.execute();

		if (mSearchView == null) {
			mSearchView = new View(mContext) {
				@SuppressLint("DrawAllocation")
				@Override
				protected void onDraw(final Canvas canvas) {
					super.onDraw(canvas);
					// Work out current total scale factor
					// from source to view
					final float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
					final Paint paint = new Paint();

					if (!mIsBlank && mSearchBoxes != null) {
						paint.setColor(HIGHLIGHT_COLOR);
						for (RectF rect : mSearchBoxes)
							canvas.drawRect(rect.left * scale,
									rect.top * scale, rect.right * scale,
									rect.bottom * scale, paint);
					}

					if (!mIsBlank && mSearchBoxesPrim != null) {
						paint.setColor(HIGHLIGHT_COLOR);
						for (RectF rect : mSearchBoxesPrim)
							canvas.drawRect(rect.left * scale + getWidth() / 2, rect.top * scale, 
									rect.right * scale + getWidth() / 2, rect.bottom * scale, paint);
					}
					
					if (!mIsBlank && mLinks != null && mHighlightLinks) {
						paint.setStrokeWidth(2);
						for (LinkInfo link : mLinks)
						{
							//canvas.drawRect(link.rect.left * scale, link.rect.top * scale, 
							//		link.rect.right * scale, link.rect.bottom * scale, paint);
							RectF rectfa = new RectF((link.rect.left - 2) * scale, (link.rect.top - 2) * scale, 
									(link.rect.right + 2) * scale, (link.rect.bottom + 2) * scale);
							paint.setStyle(Paint.Style.FILL);
							paint.setColor(LINK_COLOR);
							canvas.drawRoundRect(rectfa, 3 * scale, 3 * scale, paint);

							paint.setStyle(Paint.Style.STROKE);
							paint.setColor(HIGHLIGHT_COLOR);
							canvas.drawRoundRect(rectfa, 3 * scale, 3 * scale, paint);
						}
					}

					if (mSelectBox != null && mText != null) {
						paint.setColor(HIGHLIGHT_COLOR);
						processSelectedText(new TextProcessor() {
							RectF rect;

							public void onStartLine() {
								rect = new RectF();
							}

							public void onWord(TextWord word) {
								rect.union(word);
							}

							public void onEndLine() {
								if (!rect.isEmpty())
									canvas.drawRect(rect.left * scale, rect.top
											* scale, rect.right * scale,
											rect.bottom * scale, paint);
							}
						});
					}

					if (mItemSelectBox != null) {
						paint.setStyle(Paint.Style.STROKE);
						paint.setColor(BOX_COLOR);
						canvas.drawRect(mItemSelectBox.left * scale,
								mItemSelectBox.top * scale,
								mItemSelectBox.right * scale,
								mItemSelectBox.bottom * scale, paint);
					}

					if (mDrawing != null) {
						Path path = new Path();
						PointF p;

						paint.setAntiAlias(true);
						paint.setDither(true);
						paint.setStrokeJoin(Paint.Join.ROUND);
						paint.setStrokeCap(Paint.Cap.ROUND);

						paint.setStyle(Paint.Style.FILL);
						paint.setStrokeWidth(INK_THICKNESS * scale);
						paint.setColor(INK_COLOR);

						Iterator<ArrayList<PointF>> it = mDrawing.iterator();
						while (it.hasNext()) {
							ArrayList<PointF> arc = it.next();
							if (arc.size() >= 2) {
								Iterator<PointF> iit = arc.iterator();
								p = iit.next();
								float mX = p.x * scale;
								float mY = p.y * scale;
								path.moveTo(mX, mY);
								while (iit.hasNext()) {
									p = iit.next();
									float x = p.x * scale;
									float y = p.y * scale;
									path.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
									mX = x;
									mY = y;
								}
								path.lineTo(mX, mY);
							} else {
								p = arc.get(0);
								canvas.drawCircle(p.x * scale, p.y * scale, INK_THICKNESS * scale / 2, paint);
							}
						}

						paint.setStyle(Paint.Style.STROKE);
						canvas.drawPath(path, paint);
					}
				}
			};

			addView(mSearchView);
		}
		requestLayout();
	}

	public void setSearchBoxes(RectF searchBoxes[]) {
		mSearchBoxes = searchBoxes;
		if (mSearchView != null)
			mSearchView.invalidate();
	}

	public void setSearchBoxesPrim(RectF searchBoxes[]) {
		mSearchBoxesPrim = searchBoxes;
		if (mSearchView != null)
			mSearchView.invalidate();
	}
	
	public void setLinkHighlighting(boolean f) {
		mHighlightLinks = f;
		if (mSearchView != null)
			mSearchView.invalidate();
	}

	public void deselectText() {
		mSelectBox = null;
		mSearchView.invalidate();
	}

	public void selectText(float x0, float y0, float x1, float y1) {
		float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
		float docRelX0 = (x0 - getLeft()) / scale;
		float docRelY0 = (y0 - getTop()) / scale;
		float docRelX1 = (x1 - getLeft()) / scale;
		float docRelY1 = (y1 - getTop()) / scale;
		// Order on Y but maintain the point grouping
		if (docRelY0 <= docRelY1)
			mSelectBox = new RectF(docRelX0, docRelY0, docRelX1, docRelY1);
		else
			mSelectBox = new RectF(docRelX1, docRelY1, docRelX0, docRelY0);

		mSearchView.invalidate();

		if (mGetText == null) {
			mGetText = new AsyncTask<Void, Void, TextWord[][]>() {
				@Override
				protected TextWord[][] doInBackground(Void... params) {
					return getText();
				}

				@Override
				protected void onPostExecute(TextWord[][] result) {
					mText = result;
					mSearchView.invalidate();
				}
			};

			mGetText.execute();
		}
	}

	public void startDraw(float x, float y) {
		float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
		float docRelX = (x - getLeft()) / scale;
		float docRelY = (y - getTop()) / scale;
		if (mDrawing == null)
			mDrawing = new ArrayList<ArrayList<PointF>>();

		ArrayList<PointF> arc = new ArrayList<PointF>();
		arc.add(new PointF(docRelX, docRelY));
		mDrawing.add(arc);
		mSearchView.invalidate();
	}

	public void continueDraw(float x, float y) {
		float scale = mSourceScale * (float) getWidth() / (float) mSize.x;
		float docRelX = (x - getLeft()) / scale;
		float docRelY = (y - getTop()) / scale;

		if (mDrawing != null && mDrawing.size() > 0) {
			ArrayList<PointF> arc = mDrawing.get(mDrawing.size() - 1);
			arc.add(new PointF(docRelX, docRelY));
			mSearchView.invalidate();
		}
	}

	public void cancelDraw() {
		mDrawing = null;
		mSearchView.invalidate();
	}

	protected PointF[][] getDraw() {
		if (mDrawing == null)
			return null;

		PointF[][] path = new PointF[mDrawing.size()][];

		for (int i = 0; i < mDrawing.size(); i++) {
			ArrayList<PointF> arc = mDrawing.get(i);
			path[i] = arc.toArray(new PointF[arc.size()]);
		}

		return path;
	}

	protected void processSelectedText(TextProcessor tp) {
		(new TextSelector(mText, mSelectBox)).select(tp);
	}

	public void setItemSelectBox(RectF rect) {
		mItemSelectBox = rect;
		if (mSearchView != null)
			mSearchView.invalidate();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int x, y;
		switch (View.MeasureSpec.getMode(widthMeasureSpec)) {
		case View.MeasureSpec.UNSPECIFIED:
			x = mSize.x;
			break;
		default:
			x = View.MeasureSpec.getSize(widthMeasureSpec);
		}
		switch (View.MeasureSpec.getMode(heightMeasureSpec)) {
		case View.MeasureSpec.UNSPECIFIED:
			y = mSize.y;
			break;
		default:
			y = View.MeasureSpec.getSize(heightMeasureSpec);
		}

		setMeasuredDimension(x, y);

		if (mBusyIndicator != null) {
			int limit = Math.min(mParentSize.x, mParentSize.y) / 2;
			mBusyIndicator.measure(View.MeasureSpec.AT_MOST | limit,
					View.MeasureSpec.AT_MOST | limit);
		}
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		int w = right - left;
		int h = bottom - top;

		if (mEntire != null) {
			//mEntireMat.setScale(w/(float)mSize.x, h/(float)mSize.y);
			//mEntire.setImageMatrix(mEntireMat);
			//mEntire.invalidate();
			if (mEntire.getWidth() != w || mEntire.getHeight() != h) {
				mEntireMat.setScale(w/(float)mSize.x, h/(float)mSize.y);
				mEntire.setImageMatrix(mEntireMat);
				mEntire.invalidate();
			}
			mEntire.layout(0, 0, w, h);
		}

		if (mSearchView != null) {
			mSearchView.layout(0, 0, w, h);
		}

		if (mPatchViewSize != null) {
			if (mPatchViewSize.x != w || mPatchViewSize.y != h) {
				// Zoomed since patch was created
				mPatchViewSize = null;
				mPatchArea = null;
				if (mPatch != null) {
					mPatch.setImageBitmap(null);
					//mPatchBmh.setBm(null);
					mPatch.invalidate();
				}
			} else {
				mPatch.layout(mPatchArea.left, mPatchArea.top,
						mPatchArea.right, mPatchArea.bottom);
			}
		}

		if (mBusyIndicator != null) {
			int bw = mBusyIndicator.getMeasuredWidth();
			int bh = mBusyIndicator.getMeasuredHeight();

			mBusyIndicator.layout((w - bw) / 2, (h - bh) / 2, (w + bw) / 2,
					(h + bh) / 2);
		}
	}

	public void updateHq(boolean update) {
		Rect viewArea = new Rect(getLeft(), getTop(), getRight(), getBottom());
		// If the viewArea's size matches the unzoomed size, there is no need
		// for an hq patch
		if (viewArea.width() == mSize.x || viewArea.height() == mSize.y) {
			// If the viewArea's size matches the unzoomed size, there is no need for an hq patch
			if (mPatch != null) {
				mPatch.setImageBitmap(null);
				mPatch.invalidate();
			}
		} else {
			//Point patchViewSize = new Point(viewArea.width(), viewArea.height());
			//Rect patchArea = new Rect(0, 0, mParentSize.x, mParentSize.y);
			final Point patchViewSize = new Point(viewArea.width(), viewArea.height());
			final Rect patchArea = new Rect(0, 0, mParentSize.x, mParentSize.y);

			// Intersect and test that there is an intersection
			if (!patchArea.intersect(viewArea))
				return;

			// Offset patch area to be relative to the view top left
			patchArea.offset(-viewArea.left, -viewArea.top);

			boolean area_unchanged = patchArea.equals(mPatchArea)
					&& patchViewSize.equals(mPatchViewSize);

			// If being asked for the same area as last time and not because of
			// an update then nothing to do
			if (area_unchanged && !update)
				return;

			boolean completeRedraw = !(area_unchanged && update);

			// Stop the drawing of previous patch if still going
			if (mDrawPatch != null) {
				//mDrawPatch.cancel(true);
				mDrawPatch.cancelAndWait();
				mDrawPatch = null;
			}

			//if (completeRedraw) {
				// The bitmap holder mPatchBm may still be rendered to by a
				// previously invoked task, and possibly for a different
				// area, so we cannot risk the bitmap generated by this task
				// being passed to it
			//	mPatchBmh.drop();
			//	mPatchBmh = new BitmapHolder();
			//}

			// Create and add the image view if not already done
			if (mPatch == null) {
				mPatch = new OpaqueImageView(mContext);
				//mPatch.setScaleType(ImageView.ScaleType.FIT_CENTER);
				mPatch.setScaleType(ImageView.ScaleType.MATRIX);
				addView(mPatch);
				if (mSearchView != null) 
					mSearchView.bringToFront();
			}
			CancellableTaskDefinition<Void, Void> task;
			if (completeRedraw)
				task = getDrawPageTask(mPatchBm, patchViewSize.x, patchViewSize.y,
								patchArea.left, patchArea.top,
								patchArea.width(), patchArea.height());
			else
				task = getUpdatePageTask(mPatchBm, patchViewSize.x, patchViewSize.y,
						patchArea.left, patchArea.top,
						patchArea.width(), patchArea.height());

			mDrawPatch = new CancellableAsyncTask<Void,Void>(task) {
			/*
			mDrawPatch = new AsyncTask<PatchInfo, Void, PatchInfo>() {
				protected PatchInfo doInBackground(PatchInfo... v) {
					if (v[0].completeRedraw) {
						//v[0].bm = drawPage(v[0].patchViewSize.x,
						drawPage(mPatchBm, v[0].patchViewSize.x,
								v[0].patchViewSize.y, v[0].patchArea.left,
								v[0].patchArea.top, v[0].patchArea.width(),
								v[0].patchArea.height());
					} else {
						//v[0].bm = updatePage(v[0].bmh, v[0].patchViewSize.x,
						updatePage(mPatchBm, v[0].patchViewSize.x,
								v[0].patchViewSize.y, v[0].patchArea.left,
								v[0].patchArea.top, v[0].patchArea.width(),
								v[0].patchArea.height());
					}

					return v[0];
				}
			*/
				public void onPostExecute(Void result) {
					mPatchViewSize = patchViewSize;
					mPatchArea     = patchArea;

				//protected void onPostExecute(PatchInfo v) {
					//if (mPatchBmh == v.bmh) {
					//	mPatchViewSize = v.patchViewSize;
					//	mPatchArea = v.patchArea;
					//	if (v.bm != null) {
					//		mPatch.setImageBitmap(v.bm);
					//		v.bmh.setBm(v.bm);
					//		v.bm = null;
					//	}
						// requestLayout();
						// Calling requestLayout here doesn't lead to a later
						// call to layout. No idea
						// why, but apparently others have run into the problem.
					//	mPatch.layout(mPatchArea.left, mPatchArea.top,
					//			mPatchArea.right, mPatchArea.bottom);
					//	invalidate();
					//mPatchViewSize = v.patchViewSize;
					//mPatchArea     = v.patchArea;
					mPatch.setImageBitmap(mPatchBm);
					mPatch.invalidate();
					//requestLayout();
					// Calling requestLayout here doesn't lead to a later call to layout. No idea
					// why, but apparently others have run into the problem.
					mPatch.layout(mPatchArea.left, mPatchArea.top, mPatchArea.right, mPatchArea.bottom);
					//invalidate();
				}
			};

			//mDrawPatch.execute(new PatchInfo(patchViewSize, patchArea, mPatchBmh, completeRedraw));
			//mDrawPatch.execute(new PatchInfo(patchViewSize, patchArea, completeRedraw));
			mDrawPatch.execute();


		}
	}

	public void update() {
		// Cancel pending render task
		if (mDrawEntire != null) {
			//mDrawEntire.cancel(true);
			mDrawEntire.cancelAndWait();
			mDrawEntire = null;
		}

		if (mDrawPatch != null) {
			//mDrawPatch.cancel(true);
			mDrawPatch.cancelAndWait();
			mDrawPatch = null;
		}

		// Render the page in the background
		mDrawEntire = new CancellableAsyncTask<Void, Void>(getUpdatePageTask(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y)) {
		//mDrawEntire = new AsyncTask<Void, Void, Void>() {
		//	protected Void doInBackground(Void... v) {
				// Pass the current bitmap as a basis for the update, but use a
				// bitmap
				// holder so that the held bitmap will be nulled and not hold on
				// to
				// memory, should this view become redundant.
		//		updatePage(mEntireBm, mSize.x, mSize.y, 0, 0, mSize.x, mSize.y);
		//		return null;
		//	}

			//protected void onPostExecute(Bitmap bm) {
			//	if (bm != null) {
			//		mEntire.setImageBitmap(bm);
			//		mEntireBmh.setBm(bm);
			//	}
			//	invalidate();
			//protected void onPostExecute(Void v) {
			public void onPostExecute(Void result) {
				mEntire.setImageBitmap(mEntireBm);
				mEntire.invalidate();

			}
		};

		mDrawEntire.execute();

		updateHq(true);
	}

	public void removeHq() {
		// Stop the drawing of the patch if still going
		if (mDrawPatch != null) {
			//mDrawPatch.cancel(true);
			mDrawPatch.cancelAndWait();
			mDrawPatch = null;
		}

		// And get rid of it
		mPatchViewSize = null;
		mPatchArea = null;
		if (mPatch != null) {
			mPatch.setImageBitmap(null);
			//mPatchBmh.setBm(null);
			mPatch.invalidate();
		}
	}

	public int getPage() {
		return mPageNumber;
	}

	@Override
	public boolean isOpaque() {
		return true;
	}
}
