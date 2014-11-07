package com.artifex.mupdflib;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import com.artifex.mupdflib.CallbackApplication.MuPDFCallbackClass;
import com.artifex.mupdflib.TwoWayView.Orientation;

import java.io.InputStream;
import java.util.concurrent.Executor;

//import android.text.method.PasswordTransformationMethod;
//import android.widget.SeekBar;

class ThreadPerTaskExecutor implements Executor {
	public void execute(Runnable r) {
		new Thread(r).start();
	}
}

public class MuPDFActivity extends Activity implements FilePicker.FilePickerSupport {
	/* The core rendering instance */
	//enum TopBarMode {Main, Search, Annot, Delete, More, Accept};
	enum TopBarMode {Main, Search};
	//enum AcceptMode {Highlight, Underline, StrikeOut, Ink, CopyText};

	//private final int OUTLINE_REQUEST = 0;
	private final int PRINT_REQUEST = 1;
	private final int FILEPICK_REQUEST = 2;
	private final int PAGE_CHOICE_REQUEST = 3;
	private MuPDFCore core;
	private String mFileName;
	private String mDocName;
	private int mOrientation;
	private MuPDFReaderView mDocView;
	private View mButtonsView;
	private boolean mButtonsVisible;
	// private EditText mPasswordView;
	// private TextView mFilenameView;
	///private SeekBar mPageSlider;
	///private int mPageSliderRes;
	private TextView mPageNumberView;
	private TextView mInfoView;
	private ImageButton mSearchButton;
	//private ImageButton mReflowButton;
	//private ImageButton mOutlineButton;
	//private ImageButton	mMoreButton;
	//private TextView     mAnnotTypeText;
	//private ImageButton mAnnotButton;
	private ViewAnimator mTopBarSwitcher;
	//private ImageButton mLinkButton;
	private TopBarMode   mTopBarMode = TopBarMode.Main;
	//private AcceptMode   mAcceptMode;
	private ImageButton mSearchBack;
	private ImageButton mSearchFwd;
	private EditText mSearchText;
	private SearchTask mSearchTask;
	private AlertDialog.Builder mAlertBuilder;
	//private boolean mLinkHighlight = false;
	private final Handler mHandler = new Handler();
	private FrameLayout mPreviewBarHolder;
	private TwoWayView mPreview;
	private ToolbarPreviewAdapter pdfPreviewPagerAdapter;
	private boolean mAlertsActive = false;
	//private boolean mReflow = false;
	private AsyncTask<Void, Void, MuPDFAlert> mAlertTask;
	private AlertDialog mAlertDialog;
	private FilePicker mFilePicker;

	public void createAlertWaiter() {
		mAlertsActive = true;
		// All mupdf library calls are performed on asynchronous tasks to avoid stalling
		// the UI. Some calls can lead to javascript-invoked requests to display an
		// alert dialog and collect a reply from the user. The task has to be blocked
		// until the user's reply is received. This method creates an asynchronous task,
		// the purpose of which is to wait of these requests and produce the dialog
		// in response, while leaving the core blocked. When the dialog receives the
		// user's response, it is sent to the core via replyToAlert, unblocking it.
		// Another alert-waiting task is then created to pick up the next alert.
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		if (mAlertDialog != null) {
			mAlertDialog.cancel();
			mAlertDialog = null;
		}
		mAlertTask = new AsyncTask<Void,Void,MuPDFAlert>() {

			@Override
			protected MuPDFAlert doInBackground(Void... arg0) {
				if (!mAlertsActive)
					return null;

				return core.waitForAlert();
			}

			@Override
			protected void onPostExecute(final MuPDFAlert result) {
				// core.waitForAlert may return null when shutting down
				if (result == null)
					return;
				final MuPDFAlert.ButtonPressed pressed[] = new MuPDFAlert.ButtonPressed[3];
				for(int i = 0; i < 3; i++)
					pressed[i] = MuPDFAlert.ButtonPressed.None;
				DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mAlertDialog = null;
						if (mAlertsActive) {
							int index = 0;
							switch (which) {
							case AlertDialog.BUTTON1: index=0; break;
							case AlertDialog.BUTTON2: index=1; break;
							case AlertDialog.BUTTON3: index=2; break;
							}
							result.buttonPressed = pressed[index];
							// Send the user's response to the core, so that it can
							// continue processing.
							core.replyToAlert(result);
							// Create another alert-waiter to pick up the next alert.
							createAlertWaiter();
						}
					}
				};
				mAlertDialog = mAlertBuilder.create();
				mAlertDialog.setTitle(result.title);
				mAlertDialog.setMessage(result.message);
				switch (result.iconType)
				{
				case Error:
					break;
				case Warning:
					break;
				case Question:
					break;
				case Status:
					break;
				}
				switch (result.buttonGroupType)
				{
				case OkCancel:
					mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.cancel), listener);
					pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
				case Ok:
					mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.okay), listener);
					pressed[0] = MuPDFAlert.ButtonPressed.Ok;
					break;
				case YesNoCancel:
					mAlertDialog.setButton(AlertDialog.BUTTON3, getString(R.string.cancel), listener);
					pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
				case YesNo:
					mAlertDialog.setButton(AlertDialog.BUTTON1, getString(R.string.yes), listener);
					pressed[0] = MuPDFAlert.ButtonPressed.Yes;
					mAlertDialog.setButton(AlertDialog.BUTTON2, getString(R.string.no), listener);
					pressed[1] = MuPDFAlert.ButtonPressed.No;
					break;
				}
				mAlertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
					public void onCancel(DialogInterface dialog) {
						mAlertDialog = null;
						if (mAlertsActive) {
							result.buttonPressed = MuPDFAlert.ButtonPressed.None;
							core.replyToAlert(result);
							createAlertWaiter();
						}
					}
				});

				mAlertDialog.show();
			}
		};

		mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
	}

	public void destroyAlertWaiter() {
		mAlertsActive = false;
		if (mAlertDialog != null) {
			mAlertDialog.cancel();
			mAlertDialog = null;
		}
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
	}

	private MuPDFCore openFile(String path) {
		int lastSlashPos = path.lastIndexOf('/');
		mFileName = new String(lastSlashPos == -1 ? path
				: path.substring(lastSlashPos + 1));
		System.out.println("Trying to open " + path);
		try {
			core = new MuPDFCore(this, path);
			// New file: drop the old outline data
			//OutlineActivityData.set(null);
			PDFPreviewGridActivityData.set(null);
		} catch (Exception e) {
			System.out.println(e);
			return null;
		}
		return core;
	}

	//private MuPDFCore openBuffer(byte buffer[]) 
	private MuPDFCore openBuffer(byte buffer[], String magic) {
		System.out.println("Trying to open byte buffer");
		try {
			//core = new MuPDFCore(this, buffer);
			core = new MuPDFCore(this, buffer, magic);

			// New file: drop the old outline data
			//OutlineActivityData.set(null);
			PDFPreviewGridActivityData.set(null);
		} catch (Exception e) {
			System.out.println(e);
			return null;
		}
		return core;
	}

	
	@SuppressWarnings("unused")
	private Context getContext() {
		return this;
	}

	private void setCurrentlyViewedPreview() {
		int i = mDocView.getDisplayedViewIndex();
		if (core.getDisplayPages() == 2) {
			i = (i * 2) - 1;
		}
		pdfPreviewPagerAdapter.setCurrentlyViewing(i);
		centerPreviewAtPosition(i);
	}

	public void centerPreviewAtPosition(int position) {
		if (mPreview.getChildCount() > 0) {
			View child = mPreview.getChildAt(0);
			// assume all children the same width
			int childmeasuredwidth = child.getMeasuredWidth();

			if (childmeasuredwidth > 0) {
				if (core.getDisplayPages() == 2) {
					mPreview.setSelectionFromOffset(position,
							(mPreview.getWidth() / 2) - (childmeasuredwidth));
				} else {
					mPreview.setSelectionFromOffset(position,
							(mPreview.getWidth() / 2)
									- (childmeasuredwidth / 2));
				}
			} else {
				Log.e("centerOnPosition", "childmeasuredwidth = 0");
			}
		} else {
			Log.e("centerOnPosition", "childcount = 0");
		}
	}
	
	
	/** Called when the activity is first created. */
	@SuppressLint("StringFormatMatches")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		LibraryUtils.reloadLocale(getApplicationContext());
		
		mAlertBuilder = new AlertDialog.Builder(this);

		if (core == null) {
			core = (MuPDFCore) getLastNonConfigurationInstance();

			if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
				mFileName = savedInstanceState.getString("FileName");
			}
		}
		if (core == null) {
			Intent intent = getIntent();
			byte buffer[] = null;
			if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				Uri uri = intent.getData();
				System.out.println("URI to open is: " + uri);
				if (uri.toString().startsWith("content://")) {
					/*
					// Handle view requests from the Transformer Prime's file
					// manager
					// Hopefully other file managers will use this same scheme,
					// if not
					// using explicit paths.
					Cursor cursor = getContentResolver().query(uri,
							new String[] { "_data" }, null, null, null);
					if (cursor.moveToFirst()) {
						String str = cursor.getString(0);
						String reason = null;
						if (str == null) {
							try {
								InputStream is = getContentResolver()
										.openInputStream(uri);
								int len = is.available();
								buffer = new byte[len];
								is.read(buffer, 0, len);
								is.close();
							} catch (java.lang.OutOfMemoryError e) {
								System.out
										.println("Out of memory during buffer reading");
								reason = e.toString();
							} catch (Exception e) {
								reason = e.toString();
							}
							if (reason != null) {
								buffer = null;
								Resources res = getResources();
								AlertDialog alert = mAlertBuilder.create();
								setTitle(String.format(res.getString(R.string.cannot_open_document_Reason), reason));
								alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss), new DialogInterface.OnClickListener() {
									public void onClick(
											DialogInterface dialog,
											int which) {
										finish();
									}
								});
								alert.setOnCancelListener(new OnCancelListener() {
									@Override
									public void onCancel(DialogInterface dialog) {
										finish();
									}
								});

								alert.show();
								return;
						*/

					String reason = null;
					try {
						InputStream is = getContentResolver().openInputStream(uri);
						int len = is.available();
						buffer = new byte[len];
						is.read(buffer, 0, len);
						is.close();
					}
					catch (java.lang.OutOfMemoryError e) {
						System.out.println("Out of memory during buffer reading");
						reason = e.toString();
					}
					catch (Exception e) {
						System.out.println("Exception reading from stream: " + e);

						// Handle view requests from the Transformer Prime's file manager
						// Hopefully other file managers will use this same scheme, if not
						// using explicit paths.
						// I'm hoping that this case below is no longer needed...but it's
						// hard to test as the file manager seems to have changed in 4.x.
						try {
							Cursor cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null);
							if (cursor.moveToFirst()) {
								String str = cursor.getString(0);
								if (str == null) {
									reason = "Couldn't parse data in intent";
								}
								else {
									uri = Uri.parse(str);
								}

							}
						//} else {
						//	uri = Uri.parse(str);
						}
						catch (Exception e2) {
							System.out.println("Exception in Transformer Prime file manager code: " + e2);
							reason = e2.toString();
						}
					}
					if (reason != null) {
						buffer = null;
						Resources res = getResources();
						AlertDialog alert = mAlertBuilder.create();
						setTitle(String.format(res.getString(R.string.cannot_open_document_Reason), reason));
						alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog, int which) {
										finish();
									}
								});
						alert.show();
						return;

					}
				}
				if (buffer != null) {
					//core = openBuffer(buffer);
					core = openBuffer(buffer, intent.getType());

				} else {
					core = openFile(Uri.decode(uri.getEncodedPath()));
				}
				SearchTaskResult.set(null);
				if (core.countPages() == 0)
					core = null;
			}
			if (core != null && core.needsPassword()) {
				// requestPassword(savedInstanceState);
				// required password getteris from data
				String password = intent.getStringExtra("password");
				core.authenticatePassword(password);
				// return;
			}
			if (core != null && core.countPages() == 0)
			{
				core = null;
			}

		}
		if (core == null) {
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle(R.string.cannot_open_document);
			alert.setButton(AlertDialog.BUTTON_POSITIVE,
					getString(R.string.dismiss),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
			alert.show();
			return;
		}

		mOrientation = getResources().getConfiguration().orientation;

		if(mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
			core.setDisplayPages(2);
		} else {
			core.setDisplayPages(1);
		}
		
		createUI(savedInstanceState);
	}

	/*
	 * public void requestPassword(final Bundle savedInstanceState) {
	 * mPasswordView = new EditText(this);
	 * mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
	 * mPasswordView.setTransformationMethod(new
	 * PasswordTransformationMethod());
	 * 
	 * AlertDialog alert = mAlertBuilder.create();
	 * alert.setTitle(R.string.enter_password); alert.setView(mPasswordView);
	 * alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), new
	 * DialogInterface.OnClickListener() { public void onClick(DialogInterface
	 * dialog, int which) { if
	 * (core.authenticatePassword(mPasswordView.getText().toString())) {
	 * createUI(savedInstanceState); } else {
	 * requestPassword(savedInstanceState); } } });
	 * alert.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel),
	 * new DialogInterface.OnClickListener() {
	 * 
	 * public void onClick(DialogInterface dialog, int which) { finish(); } });
	 * alert.show(); }
	 */

	public void createUI(Bundle savedInstanceState) {
		if (core == null)
			return;

		// Now create the UI.
		// First create the document view
		mDocView = new MuPDFReaderView(this) {
			@Override
			protected void onMoveToChild(int i) {
				updatePageNumView(i);
				//if (core == null)
				//	return;
				//mPageNumberView.setText(String.format("%d / %d", i + 1,
				//		core.countPages()));
				///mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
				///mPageSlider.setProgress(i * mPageSliderRes);
				super.onMoveToChild(i);
				setCurrentlyViewedPreview();
				
				
			}

			@Override
			protected void onTapMainDocArea() {
				if (!mButtonsVisible) {
					showButtons();
				} else {
					if (mTopBarMode == TopBarMode.Main)
						hideButtons();
				}
			}

			@Override
			protected void onDocMotion() {
				hideButtons();
			}

			@Override
			protected void onHit(Hit item) {
				/*
				switch (mTopBarMode) {
				case Annot:
					if (item == Hit.Annotation) {
						showButtons();
						mTopBarMode = TopBarMode.Delete;
						mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
					}
					break;
				case Delete:
					mTopBarMode = TopBarMode.Annot;
					mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
				// fall through
				default:
				*/
					// Not in annotation editing mode, but the pageview will
					// still select and highlight hit annotations, so
					// deselect just in case.
					MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
					if (pageView != null)
						pageView.deselectAnnotation();
					//break;
				//}
			}
		};
		mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));
		
		Intent intent = getIntent();
		boolean idleenabled = intent.getBooleanExtra("idleenabled", false);
		boolean highlight = intent.getBooleanExtra("linkhighlight", false);
		boolean horizontalscrolling = intent.getBooleanExtra("horizontalscrolling", true);
		mDocView.setKeepScreenOn(!idleenabled);
		mDocView.setLinksHighlighted(highlight);
		mDocView.setScrollingDirectionHorizontal(horizontalscrolling);
		mDocName = intent.getStringExtra("docname");
		
		mSearchTask = new SearchTask(this, core) {
			@Override
			protected void onTextFound(SearchTaskResult result) {
				SearchTaskResult.set(result);
				// Ask the ReaderView to move to the resulting page
				mDocView.setDisplayedViewIndex(result.pageNumber);
				// Make the ReaderView act on the change to SearchTaskResult
				// via overridden onChildSetup method.
				mDocView.resetupChildren();
			}
		};

		// Make the buttons overlay, and store all its
		// controls in variables
		makeButtonsView();

		// Set up the page slider
		///int smax = Math.max(core.countPages() - 1, 1);
		///mPageSliderRes = ((10 + smax - 1) / smax) * 2;

		// Set the file-name text
		// /////////mFilenameView.setText(mFileName);

		// Activate the seekbar
		/*
		mPageSlider
				.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					public void onStopTrackingTouch(SeekBar seekBar) {
						mDocView.setDisplayedViewIndex((seekBar.getProgress() + mPageSliderRes / 2)
								/ mPageSliderRes);
					}

					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						updatePageNumView((progress + mPageSliderRes / 2)
								/ mPageSliderRes);
					}
				});
		*/
		// Activate the search-preparing button
		mSearchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				searchModeOn();
			}
		});

		// Activate the reflow button
		/*
		mReflowButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				toggleReflow();
			}
		});
		*/
		/*
		if (core.fileFormat().startsWith("PDF")) {
			mAnnotButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					mTopBarMode = TopBarMode.Annot;
					mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
				}
			});
		} else {
			mAnnotButton.setVisibility(View.GONE);
		}
		*/
		// Search invoking buttons are disabled while there is no text specified
		mSearchBack.setEnabled(false);
		mSearchFwd.setEnabled(false);
		mSearchBack.setColorFilter(Color.argb(255, 128, 128, 128));
		mSearchFwd.setColorFilter(Color.argb(255, 128, 128, 128));

		// React to interaction with the text widget
		mSearchText.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged(Editable s) {
				boolean haveText = s.toString().length() > 0;
				setButtonEnabled(mSearchBack, haveText);
				setButtonEnabled(mSearchFwd, haveText);

				// Remove any previous search results
				if (SearchTaskResult.get() != null 
						&& !mSearchText.getText().toString().equals(SearchTaskResult.get().txt)) {
					SearchTaskResult.set(null);
					mDocView.resetupChildren();
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
			}
		});

		// React to Done button on keyboard
		mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			public boolean onEditorAction(TextView v, int actionId,
					KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE)
					search(1);
				return false;
			}
		});

		mSearchText.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN
						&& keyCode == KeyEvent.KEYCODE_ENTER)
					search(1);
				return false;
			}
		});

		// Activate search invoking buttons
		mSearchBack.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				search(-1);
			}
		});
		mSearchFwd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				search(1);
			}
		});
		/*
		mLinkButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setLinkHighlight(!mLinkHighlight);
			}
		});
		 */
		/*
		if (core.hasOutline()) {
			mOutlineButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					OutlineItem outline[] = core.getOutline();
					if (outline != null) {
						OutlineActivityData.get().items = outline;
						Intent intent = new Intent(MuPDFActivity.this,OutlineActivity.class);
						startActivityForResult(intent, OUTLINE_REQUEST);
					}
				}
			});
		} else {
			mOutlineButton.setVisibility(View.GONE);
		}
		*/
		// Reenstate last state if it was recorded
		///SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		///mDocView.setDisplayedViewIndex(prefs.getInt("page" + mFileName, 0));
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		int orientation = prefs.getInt("orientation", mOrientation);
		int pageNum = prefs.getInt("page"+mFileName, 0);
		if(orientation == mOrientation)
			mDocView.setDisplayedViewIndex(pageNum);
		else {
			if(orientation == Configuration.ORIENTATION_PORTRAIT) {
				mDocView.setDisplayedViewIndex((pageNum + 1) / 2);
			} else {
				mDocView.setDisplayedViewIndex((pageNum == 0) ? 0 : pageNum * 2 - 1);
			}
		}
		
		//if (savedInstanceState == null
		//		|| !savedInstanceState.getBoolean("ButtonsHidden", false))
		//	showButtons();

		if (savedInstanceState != null && savedInstanceState.getBoolean("SearchMode", false))
			searchModeOn();

		//if (savedInstanceState != null && savedInstanceState.getBoolean("ReflowMode", false))
			//reflowModeSet(true);

		// Give preview thumbnails time to appear before showing bottom bar
		if (savedInstanceState == null
				|| !savedInstanceState.getBoolean("ButtonsHidden", false)) {
			mPreview.postDelayed(new Runnable() {
				@Override
				public void run() {
					runOnUiThread(new Runnable() {
						@Override
						public void run() {
							showButtons();
						}
					});
				}
			}, 250);
		}
				
		// Stick the document view and the buttons overlay into a parent view
		RelativeLayout layout = new RelativeLayout(this);
		layout.addView(mDocView);
		layout.addView(mButtonsView);
		//layout.setBackgroundResource(R.drawable.tiled_background);
		//layout.setBackgroundResource(R.color.canvas);
		setContentView(layout);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			//case OUTLINE_REQUEST:
			//	if (resultCode >= 0)
			//		mDocView.setDisplayedViewIndex(resultCode);
			//	break;
			case PRINT_REQUEST:
				if (resultCode == RESULT_CANCELED)
					showInfo(getString(R.string.print_failed));
				break;
			case FILEPICK_REQUEST:
				if (mFilePicker != null && resultCode == RESULT_OK)
					mFilePicker.onPick(data.getData());
				break;
			case PAGE_CHOICE_REQUEST:
				if (resultCode >= 0) {
					int page = resultCode;
					if (core.getDisplayPages() == 2) {
						page = (page + 1) / 2;
					}
					mDocView.setDisplayedViewIndex(page);
					setCurrentlyViewedPreview();
				}
				break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public Object onRetainNonConfigurationInstance() {
		MuPDFCore mycore = core;
		core = null;
		return mycore;
	}
/*
	private void reflowModeSet(boolean reflow) {
		mReflow = reflow;
		mDocView.setAdapter(mReflow ? new MuPDFReflowAdapter(this, core) : new MuPDFPageAdapter(this, this, core));
		mReflowButton.setColorFilter(mReflow ? Color.argb(0xFF, 172, 114, 37) : Color.argb(0xFF, 255, 255, 255));
		setButtonEnabled(mAnnotButton, !reflow);
		setButtonEnabled(mSearchButton, !reflow);
		if (reflow)
			setLinkHighlight(false);
		setButtonEnabled(mLinkButton, !reflow);
		setButtonEnabled(mMoreButton, !reflow);
		mDocView.refresh(mReflow);
	}
*/
/*
	private void toggleReflow() {
		reflowModeSet(!mReflow);
		showInfo(mReflow ? getString(R.string.entering_reflow_mode)
				: getString(R.string.leaving_reflow_mode));
	}
*/
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mFileName != null && mDocView != null) {
			outState.putString("FileName", mFileName);

			// Store current page in the prefs against the file name,
			// so that we can pick it up each time the file is loaded
			// Other info is needed only for screen-orientation change,
			// so it can go in the bundle
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
			edit.putInt("orientation", mOrientation);
			edit.commit();
		}

		if (!mButtonsVisible)
			outState.putBoolean("ButtonsHidden", true);

		if (mTopBarMode == TopBarMode.Search)
			outState.putBoolean("SearchMode", true);

		//if (mReflow)
		//	outState.putBoolean("ReflowMode", true);
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mSearchTask != null)
			mSearchTask.stop();

		if (mFileName != null && mDocView != null) {
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
			edit.putInt("orientation", mOrientation);
			edit.commit();
		}
	}

	public void onDestroy() {
		if (mDocView != null) {
			mDocView.applyToChildren(new ReaderView.ViewMapper() {
				void applyToView(View view) {
					((MuPDFView)view).releaseBitmaps();
				}
			});
		}
		if (core != null)
			core.onDestroy();
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		core = null;
		super.onDestroy();
	}

	private void setButtonEnabled(ImageButton button, boolean enabled) {
		button.setEnabled(enabled);
		button.setColorFilter(enabled ? Color.argb(255, 255, 255, 255) : Color
				.argb(255, 128, 128, 128));
	}
	/*
	private void setLinkHighlight(boolean highlight) {
		mLinkHighlight = highlight;
		// LINK_COLOR tint
		mLinkButton.setColorFilter(highlight ? Color.argb(0xFF, 172, 114, 37)
				: Color.argb(0xFF, 255, 255, 255));
		// Inform pages of the change.
		mDocView.setLinksEnabled(highlight);
	}
	*/
	private void showButtons() {
		if (core == null)
			return;
		if (!mButtonsVisible) {
			mButtonsVisible = true;
			// Update page number text and slider
			int index = mDocView.getDisplayedViewIndex();
			updatePageNumView(index);
			///mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
			///mPageSlider.setProgress(index * mPageSliderRes);
			if (mTopBarMode == TopBarMode.Search) {
				mSearchText.requestFocus();
				showKeyboard();
			}

			Animation anim = new TranslateAnimation(0, 0,
					-mTopBarSwitcher.getHeight(), 0);
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mTopBarSwitcher.setVisibility(View.VISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
				}
			});
			mTopBarSwitcher.startAnimation(anim);

			// Update listView position
			setCurrentlyViewedPreview();
			///anim = new TranslateAnimation(0, 0, mPageSlider.getHeight(), 0);
			anim = new TranslateAnimation(0, 0, mPreviewBarHolder.getHeight(), 0);
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					///mPageSlider.setVisibility(View.VISIBLE);
					mPreviewBarHolder.setVisibility(View.VISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					mPageNumberView.setVisibility(View.VISIBLE);
				}
			});
			///mPageSlider.startAnimation(anim);
			mPreviewBarHolder.startAnimation(anim);
		}
	}

	private void hideButtons() {
		if (mButtonsVisible) {
			mButtonsVisible = false;
			hideKeyboard();

			Animation anim = new TranslateAnimation(0, 0, 0,
					-mTopBarSwitcher.getHeight());
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					mTopBarSwitcher.setVisibility(View.INVISIBLE);
				}
			});
			mTopBarSwitcher.startAnimation(anim);

			///anim = new TranslateAnimation(0, 0, 0, mPageSlider.getHeight());
			anim = new TranslateAnimation(0, 0, 0, mPreviewBarHolder.getHeight());
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mPageNumberView.setVisibility(View.INVISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					///mPageSlider.setVisibility(View.INVISIBLE);
					mPreviewBarHolder.setVisibility(View.INVISIBLE);
				}
			});
			///mPageSlider.startAnimation(anim);
			mPreviewBarHolder.startAnimation(anim);
		}
	}

	private void searchModeOn() {
		if (mTopBarMode != TopBarMode.Search) {
			mTopBarMode = TopBarMode.Search;
			// Focus on EditTextWidget
			mSearchText.requestFocus();
			showKeyboard();
			mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		}
	}

	private void searchModeOff() {
		if (mTopBarMode == TopBarMode.Search) {
			mTopBarMode = TopBarMode.Main;
			hideKeyboard();
			mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
			SearchTaskResult.set(null);
			// Make the ReaderView act on the change to mSearchTaskResult
			// via overridden onChildSetup method.
			mDocView.resetupChildren();
		}
	}

	@SuppressLint("DefaultLocale")
	private void updatePageNumView(int index) {
		if (core == null)
			return;
		String pageStr = "";
		if (core.getDisplayPages() == 2 && index!=0 && index!=core.countPages()-1) {
			pageStr = String.format("%1$d-%2$d", (index*2)-2, (index*2)-1);
			mPageNumberView.setText(String.format(getString(R.string.two_pages_of_count), (index*2)-2, (index*2)-1, core.countSinglePages()));
		}
		else if (core.getDisplayPages() == 2 && (index==0 || index==core.countPages()-1)) {
			pageStr = String.format("%1$d", index+1);
			mPageNumberView.setText(String.format(getString(R.string.one_page_of_count), index+1, core.countSinglePages()));
		}
		else {
			pageStr = String.format("%1$d", index+1);
			mPageNumberView.setText(String.format(getString(R.string.one_page_of_count), index+1, core.countPages()));
		}

		MuPDFCallbackClass.sendGaiView(String.format("documentView (%1$s), page (%2$s)", mDocName, pageStr));
	}
	private void printDoc() {
		if (!core.fileFormat().startsWith("PDF")) {
			showInfo(getString(R.string.format_currently_not_supported));
			return;
		}

		Intent myIntent = getIntent();
		Uri docUri = myIntent != null ? myIntent.getData() : null;

		if (docUri == null) {
			showInfo(getString(R.string.print_failed));
		}

		if (docUri.getScheme() == null)
			docUri = Uri.parse("file://" + docUri.toString());

		Intent printIntent = new Intent(this, PrintDialogActivity.class);
		printIntent.setDataAndType(docUri, "aplication/pdf");
		printIntent.putExtra("title", mFileName);
		startActivityForResult(printIntent, PRINT_REQUEST);
	}

	private void showInfo(String message) {
		mInfoView.setText(message);

		int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		if (currentApiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {
			@SuppressWarnings("unused")
			SafeAnimatorInflater safe = new SafeAnimatorInflater((Activity)this, R.anim.info, (View)mInfoView);
		} else {
			mInfoView.setVisibility(View.VISIBLE);
			mHandler.postDelayed(new Runnable() {
				public void run() {
					mInfoView.setVisibility(View.INVISIBLE);
				}
			}, 500);
		}
	}

	private void makeButtonsView() {
		mButtonsView = getLayoutInflater().inflate(R.layout.buttons, null);
		// ///////mFilenameView =
		// (TextView)mButtonsView.findViewById(R.id.docNameText);
		///mPageSlider = (SeekBar) mButtonsView.findViewById(R.id.pageSlider);
		mPreviewBarHolder = (FrameLayout) mButtonsView.findViewById(R.id.PreviewBarHolder);
		mPreview = new TwoWayView(this);
		mPreview.setOrientation(Orientation.HORIZONTAL);
		FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
		mPreview.setLayoutParams(lp);
		pdfPreviewPagerAdapter = new ToolbarPreviewAdapter(this, core);
		mPreview.setAdapter(pdfPreviewPagerAdapter);
		mPreview.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> pArg0, View pArg1,
					int position, long id) {
				hideButtons();
				mDocView.setDisplayedViewIndex((int)id);
			}
		});
		mPreviewBarHolder.addView(mPreview);
		
		mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);
		mInfoView = (TextView) mButtonsView.findViewById(R.id.info);
		mSearchButton = (ImageButton) mButtonsView.findViewById(R.id.searchButton);
		//mReflowButton = (ImageButton) mButtonsView.findViewById(R.id.reflowButton);
		//mOutlineButton = (ImageButton) mButtonsView.findViewById(R.id.outlineButton);
		//mAnnotButton = (ImageButton)mButtonsView.findViewById(R.id.editAnnotButton);
		//mAnnotTypeText = (TextView)mButtonsView.findViewById(R.id.annotType);
		mTopBarSwitcher = (ViewAnimator) mButtonsView.findViewById(R.id.switcher);
		mSearchBack = (ImageButton) mButtonsView.findViewById(R.id.searchBack);
		mSearchFwd = (ImageButton) mButtonsView.findViewById(R.id.searchForward);
		mSearchText = (EditText) mButtonsView.findViewById(R.id.searchText);
		//mLinkButton = (ImageButton) mButtonsView.findViewById(R.id.linkButton);
		//mMoreButton = (ImageButton) mButtonsView.findViewById(R.id.moreButton);
		mTopBarSwitcher.setVisibility(View.INVISIBLE);
		mPageNumberView.setVisibility(View.INVISIBLE);
		mInfoView.setVisibility(View.INVISIBLE);
		///mPageSlider.setVisibility(View.INVISIBLE);
		mPreviewBarHolder.setVisibility(View.INVISIBLE);
	}

	public void OnMoreButtonClick(View v) {
		
		
		if (core != null) {
			int i = mDocView.getDisplayedViewIndex();
			if (core.getDisplayPages() == 2) {
				i = (i * 2) - 1;
			}
			PDFPreviewGridActivityData.get().core = core;
			PDFPreviewGridActivityData.get().position = i;
			//PDFPreviewGridActivity prevAct = new PDFPreviewGridActivity();
			//Intent intent = prevAct.getIntent();
			Intent intent = new Intent(MuPDFActivity.this, PDFPreviewGridActivity.class);
			startActivityForResult(intent, PAGE_CHOICE_REQUEST);
			
			MuPDFCallbackClass.sendGaiView(String.format("documentThumbView (%1$s)", mDocName));

		}
		
		/////////////mTopBarMode = TopBarMode.More;
		/////////////mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnCancelMoreButtonClick(View v) {
		mTopBarMode = TopBarMode.Main;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnPrintButtonClick(View v) {
		printDoc();
	}
	
	public void OnCloseReaderButtonClick(View v) {
		finish();
	}
/*
	public void OnCancelAcceptButtonClick(View v) {
		MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
		if (pageView != null) {
			pageView.deselectText();
			pageView.cancelDraw();
		}
		mDocView.setMode(MuPDFReaderView.Mode.Viewing);
		
		//switch (mAcceptMode) {
		//case CopyText:
		//	mTopBarMode = TopBarMode.More;
		//	break;
		//default:
			//mTopBarMode = TopBarMode.Annot;
		//	break;
		//}
		
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}
	*/

	public void OnCancelSearchButtonClick(View v) {
		searchModeOff();
	}

	private void showKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.showSoftInput(mSearchText, 0);
	}

	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
	}

	private void search(int direction) {
		hideKeyboard();
		int displayPage = mDocView.getDisplayedViewIndex();
		SearchTaskResult r = SearchTaskResult.get();
		int searchPage = r != null ? r.pageNumber : -1;
		mSearchTask.go(mSearchText.getText().toString(), direction, displayPage, searchPage);
	}

	@Override
	public boolean onSearchRequested() {
		if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
			hideButtons();
		} else {
			showButtons();
			searchModeOn();
		}
		return super.onSearchRequested();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
			hideButtons();
		} else {
			showButtons();
			searchModeOff();
		}
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	protected void onStart() {
		if (core != null) {
			core.startAlerts();
			createAlertWaiter();
		}

		super.onStart();
	}

	@Override
	protected void onStop() {
		if (core != null) {
			destroyAlertWaiter();
			core.stopAlerts();
		}

		super.onStop();
	}

	@Override
	public void onBackPressed() {
		//if (core.hasChanges()) {
		if (core != null && core.hasChanges()) {
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (which == AlertDialog.BUTTON_POSITIVE)
						core.save();

					finish();
				}
			};
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle("MuPDF");
			alert.setMessage(getString(R.string.document_has_changes_save_them_));
			alert.setButton(AlertDialog.BUTTON_POSITIVE,
					getString(R.string.yes), listener);
			alert.setButton(AlertDialog.BUTTON_NEGATIVE,
					getString(R.string.no), listener);
			alert.show();
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void performPickFor(FilePicker picker) {
		mFilePicker = picker;
		//Intent intent = new Intent(this, ChoosePDFActivity.class);
		//startActivityForResult(intent, FILEPICK_REQUEST);
	}
}
