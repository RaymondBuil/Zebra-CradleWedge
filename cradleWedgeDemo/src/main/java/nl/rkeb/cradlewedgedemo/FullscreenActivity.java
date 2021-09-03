package nl.rkeb.cradlewedgedemo;

import static android.content.ContentValues.TAG;

import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import java.util.Iterator;
import java.util.Set;

import nl.rkeb.cradlewedgedemo.databinding.ActivityFullscreenBinding;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {

    private static final String ACTION_CRADLEWEDGE_API_FLASHLED =
            "nl.rkeb.cradlewedge.api.FLASHLED";
    private static final String ACTION_CRADLEWEDGE_API_UNLOCK =
            "nl.rkeb.cradlewedge.api.UNLOCK";
    private static final String ACTION_CRADLEWEDGE_API_INFO =
            "nl.rkeb.cradlewedge.api.INFO";
    private static final String ACTION_CRADLEWEDGE_API_DIAGNOSTICS =
            "nl.rkeb.cradlewedge.api.DIAGNOSTICS";
    private static final String ACTION_CRADLEWEDGE_API_FASTCHARGE =
            "nl.rkeb.cradlewedge.api.FASTCHARGE";
    private static final String ACTION_CRADLEWEDGE_API_LOCATION =
            "nl.rkeb.cradlewedge.api.LOCATION";
    private static final String ACTION_CRADLEWEDGE_API_RESULT =
            "nl.rkeb.cradlewedge.api.RESULT";
    private static final String ACTION_CRADLEWEDGE_API_STOPSERVICES =
            "nl.rkeb.cradlewedge.api.STOPSERVICES";

    private TextView textViewStatus = null;
    private Button btnCrdInfo = null;
    private Button btnCrdDiagnostics = null;
    private Button btnCrdFlashLed = null;
    private Button btnCrdUnlock = null;
    private Button btnCrdGetLocation = null;
    private Button btnCrdSetLocation = null;
    private Button btnCrdStartServices = null;
    private Button btnCrdStopServices = null;
    private CheckBox chkBoxCrdFastCharge = null;
    private EditText txtCrdLocationRow = null;
    private EditText txtCrdLocationColumn = null;
    private EditText txtCrdLocationWall = null;


    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };
    private ActivityFullscreenBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mVisible = true;
        mControlsView = binding.fullscreenContentControls;
        mContentView = binding.fullscreenContent;

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        binding.dummyButton.setOnTouchListener(mDelayHideTouchListener);

        registerButtons();

        startServices();

        registerCradleWedgeIntentReceiver();

    }

    protected void startServices() {
        //start CradleWedgeAPIService
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage("nl.rkeb.cradlewedge");
        if (launchIntent != null) {
            startActivity(launchIntent);//null pointer check in case package name was not found
        }
        btnCrdStartServices.setVisibility(View.INVISIBLE);
        btnCrdStopServices.setVisibility(View.VISIBLE);
    }

    protected void stopServices() {
        //stop CradleWedgeAPIService
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_STOPSERVICES);
        sendBroadcast(requestIntent);
        btnCrdStartServices.setVisibility(View.VISIBLE);
        btnCrdStopServices.setVisibility(View.INVISIBLE);

    }

    protected void registerButtons() {

        textViewStatus = (TextView)findViewById(R.id.textViewResult);
        btnCrdInfo = (Button)this.findViewById(R.id.info_button);
        btnCrdDiagnostics = (Button)this.findViewById(R.id.diagnostics_button);
        btnCrdFlashLed = (Button)this.findViewById(R.id.flashled_button);
        btnCrdUnlock = (Button)this.findViewById(R.id.unlock_button);
        btnCrdSetLocation = (Button)this.findViewById(R.id.setlocation_button);
        btnCrdGetLocation = (Button)this.findViewById(R.id.getlocation_button);
        btnCrdStartServices = (Button)this.findViewById(R.id.start_button);
        btnCrdStopServices = (Button)this.findViewById(R.id.stop_button);
        chkBoxCrdFastCharge = (CheckBox)this.findViewById(R.id.fastChargecheckBox);
        txtCrdLocationRow = (EditText)this.findViewById(R.id.editTextRow);
        txtCrdLocationColumn = (EditText)this.findViewById(R.id.editTextColumn);
        txtCrdLocationWall = (EditText)this.findViewById(R.id.editTextWall);

        btnCrdInfo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                getCradleInfo();
                btnCrdInfo.setVisibility(View.INVISIBLE);
                btnCrdDiagnostics.setVisibility(View.VISIBLE);
            }
        });

        btnCrdDiagnostics.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                getCradleDiagnostics();
                btnCrdInfo.setVisibility(View.VISIBLE);
                btnCrdDiagnostics.setVisibility(View.INVISIBLE);
            }
        });

        btnCrdFlashLed.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                getCradleFlashLed();
            }
        });
        btnCrdUnlock.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                getCradleUnlock();
            }
        });

        btnCrdGetLocation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                getCradleLocation();
                //TODO fill in row column wall
            }
        });

        btnCrdSetLocation.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                // TODO get row column wall
                int row = getIntegerFromEditText(txtCrdLocationRow);
                int column = getIntegerFromEditText(txtCrdLocationColumn);
                int wall = getIntegerFromEditText(txtCrdLocationWall);

                setCradleLocation(row,column,wall);
            }
        });

        btnCrdStartServices.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                startServices();
            }
        });

        btnCrdStopServices.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                stopServices();
            }
        });

        chkBoxCrdFastCharge.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCradleFastCharge(chkBoxCrdFastCharge.isChecked());
            }
        });

    }

    protected int getIntegerFromEditText(EditText editText) {
        int returnInt = -1;
        if ((editText != null) && (editText.getText() != null) && !(editText.getText().toString().equals(""))) {
            returnInt = Integer.parseInt(editText.getText().toString());
        }
        return returnInt;
    }



    protected void getCradleInfo() {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_INFO +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_INFO);
        sendBroadcast(requestIntent);
    }
    protected void getCradleDiagnostics() {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_DIAGNOSTICS +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_DIAGNOSTICS);
        sendBroadcast(requestIntent);
    }
    protected void getCradleFlashLed() {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_FLASHLED +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_FLASHLED);
        requestIntent.putExtra("onDuration",50);
        requestIntent.putExtra("offDuration",50);
        requestIntent.putExtra("flashCount",15);
        requestIntent.putExtra("ledSmooth",false);
        sendBroadcast(requestIntent);
    }
    protected void getCradleUnlock() {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_UNLOCK +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_UNLOCK);
        requestIntent.putExtra("onDuration",500);
        requestIntent.putExtra("offDuration",500);
        requestIntent.putExtra("unlockDuration",10);
        requestIntent.putExtra("ledSmooth",true);
        sendBroadcast(requestIntent);
    }

    protected void getCradleFastCharge() {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_FASTCHARGE +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_FASTCHARGE);
        requestIntent.putExtra("action", "get");
        sendBroadcast(requestIntent);
    }

    protected void setCradleFastCharge(boolean status) {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_FASTCHARGE +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_FASTCHARGE);
        requestIntent.putExtra("action", "set");
        requestIntent.putExtra("status", status);
        sendBroadcast(requestIntent);
    }

    protected void getCradleLocation() {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_LOCATION +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_LOCATION);
        requestIntent.putExtra("action", "get");
        sendBroadcast(requestIntent);
    }

    protected void setCradleLocation(int row, int column, int wall) {
        textViewStatus.setText("\n\n"+ ACTION_CRADLEWEDGE_API_LOCATION +" has been send..");
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_LOCATION);
        requestIntent.putExtra("action", "set");
        requestIntent.putExtra("row", row);
        requestIntent.putExtra("column", column);
        requestIntent.putExtra("wall", wall);
        sendBroadcast(requestIntent);
    }

    protected void registerCradleWedgeIntentReceiver() {
        IntentFilter cradleWedgeIntentFilter = new IntentFilter();
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_RESULT);
        registerReceiver(mCradleWedgeBroadcastReceiver, cradleWedgeIntentFilter);

    }

    /************************
     * Monitor CradleWedge  *
     ************************/

    private final BroadcastReceiver mCradleWedgeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String intentAction = intent.getAction();
            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_RESULT)) {
                Bundle intentExtras = intent.getExtras();
                if (intentExtras != null) {
                    String resultOnWhichIntent = intentExtras.getString("Intent");
                    textViewStatus.setText("\n\n" +
                            "Intent: " + resultOnWhichIntent + "\n" +
                            "PersonalShopper: " + intentExtras.getBoolean("PersonalShopper") + "\n" +
                            "DockStatus: " + intentExtras.getBoolean("DockStatus") + "\n" +
                            "ChargingStatus: " + intentExtras.getBoolean("ChargingStatus") + "\n" +
                            "Result: " + intentExtras.getBoolean("Result") + "\n");

                    if (!intentExtras.getBoolean("Result")) {
                        // result is false so we have an errormessage
                        textViewStatus.setText(textViewStatus.getText() + "\n" +
                                "ErrorMsg: \n" + intentExtras.getString("ErrorMsg"));
                    }

                    switch (resultOnWhichIntent) {
                        case ACTION_CRADLEWEDGE_API_LOCATION:
                            Bundle resultBundle = intentExtras.getBundle(resultOnWhichIntent);
                            if (resultBundle != null) {
                                txtCrdLocationRow.setText(String.valueOf(resultBundle.getInt("row", -1)));
                                txtCrdLocationColumn.setText(String.valueOf(resultBundle.getInt("column", -1)));
                                txtCrdLocationWall.setText(String.valueOf(resultBundle.getInt("wall", -1)));
                            }
                            // no break as we want to see the additonal information
                        case ACTION_CRADLEWEDGE_API_INFO:
                        case ACTION_CRADLEWEDGE_API_DIAGNOSTICS:
                        case ACTION_CRADLEWEDGE_API_FASTCHARGE:
                             textViewStatus.setText(textViewStatus.getText() + "\n" +
                                    "Data: \n" + getBundleItems(intentExtras.getBundle(resultOnWhichIntent)));
                            break;
                    }
                }

                Log.i(TAG,ACTION_CRADLEWEDGE_API_RESULT);
            }
        }
    };


    protected String getBundleItems(Bundle bundle) {
        String bundleStr = "";

        if (bundle != null) {
            Set<String> keys = bundle.keySet();
            Iterator<String> it = keys.iterator();
            while (it.hasNext()) {
                String key = it.next();

                bundleStr = bundleStr + key + "=" + bundle.get(key) + "\n";
            }
        }
        return bundleStr;

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 30) {
            mContentView.getWindowInsetsController().show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    private void addCrdInfoButtonListener() {
        btnCrdInfo.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {

            }
        });
    }
}