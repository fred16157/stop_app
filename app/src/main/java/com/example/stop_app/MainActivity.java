package com.example.stop_app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    MainService mainService = null;
    boolean isBinded = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if(hasCameraPermission()) {
            startMainService();
        } else {
            requestPermission();
        }
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

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mainService = ((MainService.LocalBinder) iBinder).getService();
            mainService.imageUpdateCallback = (image) -> {
                System.out.println("Image update called");
                runOnUiThread(() -> {
                    System.out.println("Rendering preview");
                    ((ImageView)findViewById(R.id.imageView)).setImageBitmap(image);
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
