package com.example.stop_app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

public class MainActivity extends AppCompatActivity {
    MainService mainService = null;
    boolean isBinded = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(preferences.getBoolean("isFirstLaunch", true)) {
            startActivity(new Intent(this, MainAppIntro.class));
            finish();
            return;
        }
        if(hasCameraPermission()) {
            startMainService();
        } else {
            requestPermission();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()) {
            case R.id.settings_btn:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (hasCameraPermission()) {
                startMainService();
            } else {
                Toast.makeText(this, "카메라 권한이 허용되지 않았습니다.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 1) {
            assert data != null;
            boolean doCrosswalkAlert = data.getBooleanExtra("DO_CROSSWALK_ALERT", false);
            boolean doTrafficLightAlert = data.getBooleanExtra("DO_TRAFFIC_LIGHT_ALERT", false);
            boolean doCollisionAlert = data.getBooleanExtra("DO_COLLISION_ALERT", false);
            if(mainService != null) {
                mainService.doCrosswalkAlert = doCrosswalkAlert;
                mainService.doTrafficLightAlert = doTrafficLightAlert;
                mainService.doCollisionAlert = doCollisionAlert;
            }

        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mainService = ((MainService.LocalBinder) iBinder).getService();
            mainService.imageUpdateCallback = (image) -> {
                runOnUiThread(() -> {
                    ((ImageView)findViewById(R.id.imageView)).setImageBitmap(image);
                });
            };
            mainService.predictionUpdateCallback = (image) -> {
                runOnUiThread(() -> {
                    ((ImageView)findViewById(R.id.predictionView)).setImageBitmap(image);
                });
            };
            mainService.predictionTimeUpdateCallback = (millis) -> {
                runOnUiThread(() -> {
                    ((TextView)findViewById(R.id.textView)).setText(millis+"ms");
                });
            };

            isBinded = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mainService.imageUpdateCallback = null;
            mainService = null;
            unbindService(connection);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mainService != null) {
            mainService.imageUpdateCallback = null;
        }
        if(isBinded) unbindService(connection); isBinded = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mainService != null) {
            mainService.imageUpdateCallback = null;
        }
        if(isBinded) unbindService(connection); isBinded = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(!isBinded) bindService(new Intent(this, MainService.class), connection, Context.BIND_AUTO_CREATE);
    }

    public void startMainService() {
        Intent intent = new Intent(this, MainService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
//        ActivityManager manager = (ActivityManager)this.getSystemService(Context.ACTIVITY_SERVICE);
//        for(ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
//            if(MainService.class.getName().equals(service.service.getClassName())) return;
//        }
        startService(intent);
    }

    public void stopMainService() {
        Intent intent = new Intent(this, MainService.class);
        unbindService(connection);
        stopService(intent);
    }
}
