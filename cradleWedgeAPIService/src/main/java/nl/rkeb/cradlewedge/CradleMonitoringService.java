package nl.rkeb.cradlewedge;

import static android.content.ContentValues.TAG;

import static nl.rkeb.cradlewedge.utilities.NotificationHelper.ACTION_STOP_SERVICE;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKResults;
import com.symbol.emdk.personalshopper.CradleConfig;
import com.symbol.emdk.personalshopper.CradleConfig.CradleLocation;
import com.symbol.emdk.personalshopper.CradleException;
import com.symbol.emdk.personalshopper.CradleInfo;
import com.symbol.emdk.personalshopper.CradleLedFlashInfo;
import com.symbol.emdk.personalshopper.CradleResults;
import com.symbol.emdk.personalshopper.DiagnosticConfig;
import com.symbol.emdk.personalshopper.DiagnosticData;
import com.symbol.emdk.personalshopper.DiagnosticException;
import com.symbol.emdk.personalshopper.DiagnosticParamId;
import com.symbol.emdk.personalshopper.PersonalShopper;


import java.util.Collection;

import nl.rkeb.cradlewedge.utilities.NotificationHelper;

public class CradleMonitoringService extends Service implements EMDKManager.EMDKListener {

    private PersonalShopper PsObject = null;
    public static EMDKManager emdkManager = null;


    private static final String ACTION_DEVICE_DOCKED =
            "com.symbol.intent.device.DOCKED";
    private static final String ACTION_DEVICE_UNDOCKED =
            "com.symbol.intent.device.UNDOCKED";

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

    private Boolean personalShopper = false;
    private Boolean deviceDocked = false;
    private Boolean deviceCharged = false;

    public Boolean getPersonalShopper() {
        return personalShopper;
    }

    public void setPersonalShopper(Boolean personalShopper) {
        this.personalShopper = personalShopper;
    }

    public Boolean getDeviceDocked() {
        return deviceDocked;
    }

    public void setDeviceDocked(Boolean deviceDocked) {
        this.deviceDocked = deviceDocked;
    }

    public Boolean getDeviceCharged() {
        return deviceCharged;
    }

    public void setDeviceCharged(Boolean deviceCharged) {
        this.deviceCharged = deviceCharged;
    }

    public Boolean checkIfCharging() {
        // Intent to check the actions on battery
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = this.registerReceiver((BroadcastReceiver) null, intentFilter);
        int status = batteryStatus.getIntExtra("status", -1);
        return((status == BatteryManager.BATTERY_STATUS_CHARGING) || (status == BatteryManager.BATTERY_STATUS_FULL));
    }

    public Boolean checkIfDocked() {

        // as we cannot check the dock event for extra information, we assume, when the device is getting charged, it is docked into the cradle
        // TODO if better functionality is available, this function needs to be changed
        return (checkIfCharging());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        EMDKResults results = EMDKManager.getEMDKManager(getApplicationContext(), this);
        if (results.statusCode != EMDKResults.STATUS_CODE.SUCCESS) {
            Log.e(TAG,"Failed in getEMDKManager::"+results.statusCode);
        }
        else{
            Log.i(TAG,"getEMDKManager Success");
        }

        setDeviceCharged(checkIfCharging());
        setDeviceDocked(checkIfDocked());

        // Register Broadcast Receiver Docking / PowerConnected
        IntentFilter dockIntentFilter = new IntentFilter();
        dockIntentFilter.addAction(ACTION_DEVICE_DOCKED);
        dockIntentFilter.addAction(ACTION_DEVICE_UNDOCKED);
        dockIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        dockIntentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mDockStateBroadcastReceiver, dockIntentFilter);

        IntentFilter cradleWedgeIntentFilter = new IntentFilter();
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_FLASHLED);
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_UNLOCK);
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_INFO);
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_DIAGNOSTICS);
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_FASTCHARGE);
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_LOCATION);
        cradleWedgeIntentFilter.addAction(ACTION_CRADLEWEDGE_API_STOPSERVICES);

        registerReceiver(mCradleWedgeBroadcastReceiver, cradleWedgeIntentFilter);

        // StartUp
        startForeground(NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.createNotification(this));
    }

    protected void disable() {
        try {
            if (null != PsObject.cradle) {
                PsObject.cradle.disable();
            }
        } catch (CradleException e) {

            e.printStackTrace();
            Log.e(TAG,"Status: " + e.getMessage());
        }
    }

    protected void enable() {
        try {
            if(null!=PsObject.cradle) {
                PsObject.cradle.enable();
            }
        } catch (CradleException e) {

            e.printStackTrace();
            Log.e(TAG,"Status: " + e.getMessage());
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String intentAction = intent.getAction();
            if (intentAction == null) {
                return START_STICKY;
            } else {
                switch (intentAction) {
                    case ACTION_STOP_SERVICE: {
                        stopSelf(startId);
                        return START_NOT_STICKY;
                    }
                    default: {
                        return START_NOT_STICKY;
                    }
                }
            }
        } else {
            return START_NOT_STICKY;
        }
    }

    @Override
    public void onOpened(EMDKManager emdkManager) {

        this.emdkManager = emdkManager;

        try {
            PsObject = (PersonalShopper) this.emdkManager.getInstance(EMDKManager.FEATURE_TYPE.PERSONALSHOPPER);
        } catch(Exception e) {
            Log.e(TAG,e.getMessage());
        }

        setPersonalShopper((PsObject != null));
        if(getPersonalShopper()) {
            enable();
            Log.i(TAG,"PersonalShopper feature is supported");
        } else {
            Log.e(TAG,"PersonalShopper feature is NOT supported");
        }
    }

    @Override
    public void onClosed() {
        if (emdkManager != null) {
            emdkManager.release();
            emdkManager = null;
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mCradleWedgeBroadcastReceiver);
        unregisterReceiver(mDockStateBroadcastReceiver);
        disable();

        if (emdkManager != null) {
            emdkManager.release();
            emdkManager = null;
        }

    }

    /************************
     * Monitor CradleWedge  *
     ************************/

    private final BroadcastReceiver mCradleWedgeBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String intentAction = intent.getAction();
            Bundle intentExtras = intent.getExtras();

            Intent resultIntent = new Intent();
            resultIntent.setAction(ACTION_CRADLEWEDGE_API_RESULT);
            resultIntent.putExtra("Intent", intentAction);
            resultIntent.putExtra("PersonalShopper", getPersonalShopper());
            resultIntent.putExtra("DockStatus", getDeviceDocked());
            resultIntent.putExtra("ChargingStatus" , getDeviceCharged());


            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_INFO)) {
                try {
                    resultIntent.putExtra(intentAction, getCradleWedgeApiInfo());
                    resultIntent.putExtra("Result", true);
                } catch (CradleException e) {
                    resultIntent.putExtra("ErrorMsg", e.getMessage());
                    resultIntent.putExtra("Result", false);
                }
                Log.i(TAG,intentAction);
            }
            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_DIAGNOSTICS)) {
                try {
                    resultIntent.putExtra(intentAction, getCradleWedgeApiDiagnostics());
                    resultIntent.putExtra("Result", true);
                } catch (DiagnosticException e) {
                    resultIntent.putExtra("ErrorMsg", e.getMessage());
                    resultIntent.putExtra("Result", false);
                }
                Log.i(TAG,intentAction);
            }
            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_FLASHLED)) {
                try {
                    resultIntent.putExtra(intentAction, doFlashLeds(intentExtras.getInt("onDuration", 500),intentExtras.getInt("offDuration", 500),intentExtras.getInt("flashCount", 5),intentExtras.getBoolean("ledSmooth", false) ));
                    resultIntent.putExtra("Result", true);
                } catch (CradleException e) {
                    resultIntent.putExtra("ErrorMsg", e.getMessage());
                    resultIntent.putExtra("Result", false);
                }
                Log.i(TAG,intentAction);
            }
            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_UNLOCK)) {
                try {
                    resultIntent.putExtra(intentAction, doUnlockCradle(intentExtras.getInt("onDuration", 500),intentExtras.getInt("offDuration", 500),intentExtras.getInt("unlockDuration", 15),intentExtras.getBoolean("ledSmooth", true)));
                    resultIntent.putExtra("Result", true);
                } catch (CradleException e) {
                    resultIntent.putExtra("ErrorMsg", e.getMessage());
                    resultIntent.putExtra("Result", false);
                }
                Log.i(TAG,intentAction);
            }
            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_FASTCHARGE)) {
                try {
                    resultIntent.putExtra(intentAction, doFastChargeCradle(intentExtras.getString("action", "get"),intentExtras.getBoolean("status", true)));
                    resultIntent.putExtra("Result", true);
                } catch (CradleException e) {
                    resultIntent.putExtra("ErrorMsg", e.getMessage());
                    resultIntent.putExtra("Result", false);
                }
                Log.i(TAG,intentAction);
            }
            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_LOCATION)) {
                try {
                    resultIntent.putExtra(intentAction, doLocationCradle(intentExtras.getString("action", "get"),intentExtras.getInt("row", 0),intentExtras.getInt("column", 0),intentExtras.getInt("wall",0)));
                    resultIntent.putExtra("Result", true);
                } catch (CradleException e) {
                    resultIntent.putExtra("ErrorMsg", e.getMessage());
                    resultIntent.putExtra("Result", false);
                }
                Log.i(TAG,intentAction);
            }

            if (intentAction != null && intentAction.equals(ACTION_CRADLEWEDGE_API_STOPSERVICES)) {
                resultIntent.putExtra(intentAction, true);
                resultIntent.putExtra("Result", true);
                Log.i(TAG,intentAction);
                stopSelf();
            }
            // send result
            sendBroadcast(resultIntent);
        }
    };

    protected Bundle getCradleWedgeApiInfo() throws CradleException {

        CradleInfo cradleInfo = null;
        Bundle bCradleWedgeApiInfo = new Bundle();

        try {
            cradleInfo = PsObject.cradle.getCradleInfo();
        } catch (CradleException e) {
            throw e;
        }

        if (cradleInfo != null) {
            bCradleWedgeApiInfo.putString("Partnumber", cradleInfo.getPartNumber());
            bCradleWedgeApiInfo.putString("SerialNumber", cradleInfo.getSerialNumber());
            bCradleWedgeApiInfo.putString("FirmwareVersion", cradleInfo.getFirmwareVersion());
            bCradleWedgeApiInfo.putString("DateOfManufacturing", cradleInfo.getDateOfManufacture());
            bCradleWedgeApiInfo.putString("HardwareID", cradleInfo.getHardwareID());
        }
        return bCradleWedgeApiInfo;
    }

    protected Bundle getCradleWedgeApiDiagnostics() throws DiagnosticException {

        Bundle bCradleWedgeApiDiagnostics = new Bundle();

        DiagnosticData diagnosticData = null;
        DiagnosticParamId diagnosticparamID = new DiagnosticParamId();
        @SuppressWarnings("static-access")
        int paramId = diagnosticparamID.ALL;

        DiagnosticConfig diagnosticconfig = new DiagnosticConfig(200,60);

        if(null!=PsObject.diagnostic)
        {
            try{
                diagnosticData =  PsObject.diagnostic.getDiagnosticData(paramId, diagnosticconfig);
            } catch (DiagnosticException e) {
                throw e;
            }

            if(diagnosticData!=null)
            {
                bCradleWedgeApiDiagnostics.putString("batteryStateOfCharge", String.valueOf(diagnosticData.batteryStateOfCharge));
                bCradleWedgeApiDiagnostics.putString("batteryTimeToEmpty", String.valueOf(diagnosticData.batteryTimeToEmpty));
                bCradleWedgeApiDiagnostics.putString("batteryStateOfHealth", String.valueOf(diagnosticData.batteryStateOfHealth));
                bCradleWedgeApiDiagnostics.putString("batteryChargingTime", String.valueOf(diagnosticData.batteryChargingTime));
                bCradleWedgeApiDiagnostics.putString("timeSinceBatteryReplaced", String.valueOf(diagnosticData.timeSinceBatteryReplaced));
                bCradleWedgeApiDiagnostics.putString("timeSinceReboot", String.valueOf(diagnosticData.timeSinceReboot));
                bCradleWedgeApiDiagnostics.putString("batteryChargingTimeElapsed", String.valueOf(diagnosticData.batteryChargingTimeElapsed));
                bCradleWedgeApiDiagnostics.putString("batteryDateOfManufacture", String.valueOf(diagnosticData.batteryDateOfManufacture));
            }
        }

        return bCradleWedgeApiDiagnostics;
    }

    protected boolean doFlashLeds(int onDuration, int offDuration, int flashCount, boolean mLedsmooth) throws CradleException {

        boolean returnResult = false;

        if(null!=PsObject.cradle){

            try {
                CradleLedFlashInfo ledFlashInfo = new CradleLedFlashInfo(onDuration, offDuration, mLedsmooth);
                CradleResults result = PsObject.cradle.flashLed(flashCount, ledFlashInfo);
                if(result == CradleResults.SUCCESS){
                    returnResult = true;
                }
            } catch (CradleException e) {
                throw e;
            }
        }
        return returnResult;
    }

    protected boolean doUnlockCradle(int onDuration, int offDuration, int unlockDuration, boolean mLedsmooth) throws CradleException {

        boolean returnResult = false;

        if (null != PsObject.cradle) {

            try {
                CradleLedFlashInfo ledFlashInfo = new CradleLedFlashInfo(onDuration, offDuration, mLedsmooth);
                CradleResults result = PsObject.cradle.unlock(unlockDuration, ledFlashInfo);
                if(result == CradleResults.SUCCESS){
                    returnResult = true;
                }
            } catch (CradleException e) {
                throw e;
            }
        }
        return returnResult;
    }

    protected Bundle doFastChargeCradle(String action, Boolean status) throws CradleException {

        Bundle bCradleWedgeApiFastCharge = new Bundle();

        if (null != PsObject.cradle) {

            try {
                switch (action) {
                    case "set" :
                        PsObject.cradle.config.setFastChargingState(status);
                        // no break; as we want to read back the configuration
                    case "get" :
                        bCradleWedgeApiFastCharge.putBoolean("fastcharge" ,PsObject.cradle.config.getFastChargingState());
                        break;
                }
            } catch (CradleException e) {
                throw e;
            }
        }
        return bCradleWedgeApiFastCharge;
    }

    protected Bundle doLocationCradle(String action, int row, int column, int wall) throws CradleException {

        CradleConfig.CradleLocation cradleLocation = null;

        Bundle bCradleWedgeApiLocation = new Bundle();

        if (null != PsObject.cradle) {

            try {
                switch (action) {
                    case "set" :
                        cradleLocation = PsObject.cradle.config.getLocation();
                        cradleLocation.row = row;
                        cradleLocation.column = column;
                        cradleLocation.wall = wall;
                        PsObject.cradle.config.setLocation(cradleLocation);
                        // no break as we want to read the config back from the cradle
                    case "get" :
                        cradleLocation = PsObject.cradle.config.getLocation();
                        bCradleWedgeApiLocation.putInt("row",cradleLocation.row);
                        bCradleWedgeApiLocation.putInt("column",cradleLocation.column);
                        bCradleWedgeApiLocation.putInt("wall",cradleLocation.wall);
                        break;
                }
            } catch (CradleException e) {
                throw e;
            }
        }
        return bCradleWedgeApiLocation;
    }

    protected void getCradleFastCharge() {
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_FASTCHARGE);
        requestIntent.putExtra("action", "get");
        sendBroadcast(requestIntent);
    }

    protected void getCradleLocation() {
        Intent requestIntent = new Intent();
        requestIntent.setAction(ACTION_CRADLEWEDGE_API_LOCATION);
        requestIntent.putExtra("action", "get");
        sendBroadcast(requestIntent);
    }


    /************************
     * Monitor Docking State *
     ************************/

    private final BroadcastReceiver mDockStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String intentAction = intent.getAction();
            if (intentAction != null && intentAction.equals(ACTION_DEVICE_DOCKED)) {
                // TODO we can communicate with cradle
                setDeviceDocked(true);
                // trigger some intents so applications get updated automatically after docking event
                getCradleFastCharge();
                getCradleLocation();
                Log.i(TAG,ACTION_DEVICE_DOCKED);
            }
            if (intentAction != null && intentAction.equals(ACTION_DEVICE_UNDOCKED)) {
                // TODO we can NOT communicate with cradle
                setDeviceDocked(false);
                Log.i(TAG,ACTION_DEVICE_UNDOCKED);
            }
            if (intentAction != null && intentAction.equals(Intent.ACTION_POWER_CONNECTED)) {
                // TODO we can NOT communicate with cradle
                setDeviceCharged(true);
                Log.i(TAG,Intent.ACTION_POWER_CONNECTED);
            }
            if (intentAction != null && intentAction.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                // TODO we can NOT communicate with cradle
                setDeviceCharged(false);
                Log.i(TAG,Intent.ACTION_POWER_DISCONNECTED);
            }

        }
    };

    /***************
     * Unsupported *
     ***************/

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("This feature is not supported");
    }
}
