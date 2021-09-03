package nl.rkeb.cradlewedge.utilities;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission.BLUETOOTH;
import static android.Manifest.permission.BLUETOOTH_ADMIN;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionsHelper {

    // Constants
    public static final int PERMISSIONS_REQUEST_CODE = 1000;
    public static final int OVERLAY_REQUEST_CODE = 2000;
    public static final int BT_REQUEST_CODE = 3000;
    private static final String[] PERMISSIONS = {
            ACCESS_FINE_LOCATION, WRITE_EXTERNAL_STORAGE
    };

    // Variables
    private Activity mActivity;
    private OnPermissionsResultListener mOnPermissionsResultListener;

    // Interfaces
    public interface OnPermissionsResultListener {
        void onPermissionsGranted();
    }

    public PermissionsHelper(@NonNull Activity activity,
                             @NonNull OnPermissionsResultListener onPermissionsResultListener) {
        this.mActivity = activity;
        this.mOnPermissionsResultListener = onPermissionsResultListener;
        forcePermissionsUntilGranted();
    }

    public void forcePermissionsUntilGranted() {
        if (checkOverlayPermission() && checkStandardPermissions() ) {
            mOnPermissionsResultListener.onPermissionsGranted();
        } else if (!checkStandardPermissions()) {
            requestStandardPermission();
        } else if (!checkOverlayPermission()){
            requestOverlayPermission();
        }
    }

    private boolean checkOverlayPermission() {
        return Settings.canDrawOverlays(mActivity);
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + mActivity.getPackageName()));
        mActivity.startActivityForResult(intent, OVERLAY_REQUEST_CODE);
    }

    private boolean checkStandardPermissions() {
        boolean permissionsGranted = true;
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(mActivity, permission) != PERMISSION_GRANTED) {
                permissionsGranted = false;
                break;
            }
        }
        return permissionsGranted;
    }

    private void requestStandardPermission() {
        ActivityCompat.requestPermissions(mActivity, PERMISSIONS, PERMISSIONS_REQUEST_CODE);
    }

    public void onRequestPermissionsResult() {
        forcePermissionsUntilGranted();
    }

    public void onActivityResult() {
        forcePermissionsUntilGranted();
    }

}
