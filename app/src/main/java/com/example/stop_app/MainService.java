package com.example.stop_app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.preference.PreferenceManager;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainService extends LifecycleService {
    final String RUNNING_CHANNEL_ID = "ALERT_RUNNING";
    final String CROSSWALK_CHANNEL_ID = "ALERT_CROSSWALK";
    final String TRAFFIC_LIGHT_CHANNEL_ID = "ALERT_TRAFFIC_LIGHT";
    final String COLLISION_CHANNEL_ID = "ALERT_COLLISION";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    public Consumer<Bitmap> imageUpdateCallback;
    public Consumer<Bitmap> predictionUpdateCallback;
    public Consumer<Long> predictionTimeUpdateCallback;
    public boolean doCrosswalkAlert;
    public boolean doTrafficLightAlert;
    public boolean doCollisionAlert;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private YuvToRgbConverter converter;
    private YoloPredictionHelper helper;
    private boolean isBusy = false;
    private boolean[] prevPredictions = new boolean[5];
    Executor executor = Executors.newSingleThreadExecutor();
    @Override
    public void onCreate() {
        super.onCreate();
        converter = new YuvToRgbConverter(this);
        start();
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateBroadcastReceiver, screenStateFilter);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        doCrosswalkAlert = preferences.getBoolean("do_crosswalk_alert", false);
        doTrafficLightAlert = preferences.getBoolean("do_traffic_light_alert", false);
        doCollisionAlert = preferences.getBoolean("do_collision_alert", false);
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    @Override
    @UseExperimental(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    public int onStartCommand(Intent intent, int flags, int startId) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        helper = new YoloPredictionHelper(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(416, 416))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();


                imageAnalysis.setAnalyzer(executor, (image) -> {
                    if(isBusy) return;
                    isBusy = true;
                    long startTime = SystemClock.uptimeMillis();
                    Bitmap bitmap = Bitmap.createBitmap(
                            image.getWidth(),
                            image.getHeight(),
                            Bitmap.Config.ARGB_8888
                    );
                    Image img = image.getImage();
                    if(img == null) {
                        return;
                    }
                    converter.yuvToRgb(img, bitmap);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(90);
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, 416, 416, matrix, false);
                    ArrayList<YoloPredictionHelper.Recognition> predictions = helper.predict(rotated);
                    if(imageUpdateCallback != null) imageUpdateCallback.accept(rotated);
                    if(predictionUpdateCallback != null) predictionUpdateCallback.accept(helper.getResultOverlay(predictions));
                    boolean[] curPredictions = new boolean[5];
                    for(YoloPredictionHelper.Recognition prediction : predictions) {
                        curPredictions[prediction.getDetectedClass()] = true;
                        //방금 전 예측에 같은 경고를 보냈다면 멈춤
                        if(prevPredictions[prediction.getDetectedClass()]) continue;
                        Notification.Builder notification = null;
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            switch (prediction.getDetectedClass()) {
                                case 0:
                                    if(!doCrosswalkAlert) continue;
                                    notification = new Notification.Builder(MainService.this, CROSSWALK_CHANNEL_ID);
                                    break;
                                case 1: case 2:
                                    if(!doTrafficLightAlert) continue;
                                    notification = new Notification.Builder(MainService.this, TRAFFIC_LIGHT_CHANNEL_ID);
                                    break;
                                case 3: case 4:
                                    if(!doCollisionAlert) continue;
                                    notification = new Notification.Builder(MainService.this, COLLISION_CHANNEL_ID);
                                    break;
                            }
                        } else {
                            notification = new Notification.Builder(MainService.this);
                        }
                        assert notification != null;
                        notification.setSmallIcon(R.mipmap.ic_launcher).setPriority(Notification.PRIORITY_MAX);
                        switch (prediction.getDetectedClass()) {
                            case 0:
                                notification.setContentTitle("횡단보도 경고")
                                        .setContentText("전방에 횡단보도가 감지되었습니다.");
                                break;
                            case 1:
                                notification.setContentTitle("신호등 감지됨")
                                        .setContentText("횡단보도를 건널 수 있는 상태가 되면 알림을 보내드리겠습니다.");
                                break;
                            case 2:
                                notification.setContentTitle("횡단보도를 건널 수 있음")
                                        .setContentText("주변을 잘 살피고 건너주세요.");
                                break;
                            case 3: case 4:
                                notification.setContentTitle("전방 충돌 위험 알림")
                                        .setContentText("전방에 충돌할 위험이 있는 사물이 있습니다.");
                                break;
                        }
                        getSystemService(NotificationManager.class).notify(2, notification.build());
                    }
                    image.close();
                    prevPredictions = curPredictions;
                    if(predictionTimeUpdateCallback != null) predictionTimeUpdateCallback.accept(SystemClock.uptimeMillis() - startTime);
                    isBusy = false;
                });
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
            } catch (Exception e) {
                e.printStackTrace();
                stopSelf(1223);
            }
        }, ContextCompat.getMainExecutor(this));
        return super.onStartCommand(intent, flags, startId);
    }

    public void start() {
        Intent notificationIntent = new Intent(this, StopServiceBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Notification.Builder notification;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE));
            NotificationChannel runningChannel = new NotificationChannel(RUNNING_CHANNEL_ID, "Running Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(runningChannel);
            NotificationChannel crosswalkChannel = new NotificationChannel(CROSSWALK_CHANNEL_ID, "Crosswalk Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(crosswalkChannel);
            NotificationChannel trafficLightChannel = new NotificationChannel(TRAFFIC_LIGHT_CHANNEL_ID, "Traffic Light Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(trafficLightChannel);
            NotificationChannel collisionChannel = new NotificationChannel(COLLISION_CHANNEL_ID, "Collision Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(collisionChannel);
            notification = new Notification.Builder(this, RUNNING_CHANNEL_ID);
        }
        else {
            notification = new Notification.Builder(this);
        }

        notification.setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setContentTitle("멈춰!가 실행중입니다.")
                .setContentText("중지하려면 터치하세요.")
                .setPriority(Notification.PRIORITY_MAX);
        startForeground(1, notification.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        helper.close();
        unregisterReceiver(screenStateBroadcastReceiver);
        stopSelf(1223);
    }

    public class LocalBinder extends Binder {
        MainService getService() {
            return MainService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return new LocalBinder();
    }

    private final SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener = (sharedPreferences, key) -> {
        switch (key) {
            case "do_crosswalk_alert":
                doCrosswalkAlert = sharedPreferences.getBoolean(key, false);
                break;
            case "do_traffic_light_alert":
                doTrafficLightAlert = sharedPreferences.getBoolean(key, false);
                break;
            case "do_collision_alert":
                doCollisionAlert = sharedPreferences.getBoolean(key, false);
                break;
        }
    };

    private final BroadcastReceiver screenStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch(intent.getAction()) {
                case Intent.ACTION_SCREEN_ON:
                    start();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    stopForeground(true);
                    break;
            }
        }
    };
}
