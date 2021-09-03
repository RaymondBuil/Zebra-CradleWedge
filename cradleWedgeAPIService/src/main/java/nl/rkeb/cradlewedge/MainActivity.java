package nl.rkeb.cradlewedge;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.symbol.emdk.EMDKManager;
import com.symbol.emdk.EMDKManager.EMDKListener;
import com.symbol.emdk.EMDKResults;

import nl.rkeb.cradlewedge.utilities.PermissionsHelper;

public class MainActivity extends AppCompatActivity implements PermissionsHelper.OnPermissionsResultListener {

    // Utility Class
    private PermissionsHelper mPermissionsHelper = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request Permissions
        mPermissionsHelper = new PermissionsHelper(this, this);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mPermissionsHelper.onActivityResult();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mPermissionsHelper.onRequestPermissionsResult();
    }

    @Override
    public void onPermissionsGranted() {

        Toast.makeText(this, "Permissions Granted", Toast.LENGTH_LONG).show();

        // Start Service
        startService(new Intent(this, CradleMonitoringService.class));
        finish();
    }
}

