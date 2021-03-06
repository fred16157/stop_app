package com.stop.stop_app;

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
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.preference.PreferenceManager;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainService extends LifecycleService {
    final String RUNNING_CHANNEL_ID = "ALERT_RUNNING";
    final String CROSSWALK_CHANNEL_ID = "ALERT_CROSSWALK";
    final String TRAFFIC_LIGHT_CHANNEL_ID = "ALERT_TRAFFIC_LIGHT";
    final String COLLISION_CHANNEL_ID = "ALERT_COLLISION";
    final String COVERED_CHANNEL_ID = "ALERT_COVERED";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    public Consumer<Bitmap> imageUpdateCallback;
    public Consumer<Bitmap> predictionUpdateCallback;
    public Consumer<Long> predictionTimeUpdateCallback;
    public boolean doCrosswalkAlert;
    public boolean doTrafficLightAlert;
    public boolean doCollisionAlert;
    public boolean doCameraCoveredAlert;
    public int alertThreshold;
    public int sharpnessThreshold;
    public float confidenceThreshold;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private YuvToRgbConverter converter;
    private YoloPredictionHelper helper;
    private boolean isBusy = false;
    private final short[] prevPredictions = new short[]{11, 11, 11, 11, 11, 11};
    Executor executor = Executors.newSingleThreadExecutor();
    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        if(preferences.getBoolean("isFirstLaunch", true)) {
            stopSelf(1223);
        }
        converter = new YuvToRgbConverter(this);
        BaseLoaderCallback loaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                if(status == LoaderCallbackInterface.SUCCESS) {
                    start();
                }
                super.onManagerConnected(status);
            }
        };
        if(!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, loaderCallback);
        } else {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateBroadcastReceiver, screenStateFilter);
        doCrosswalkAlert = preferences.getBoolean("do_crosswalk_alert", false);
        doTrafficLightAlert = preferences.getBoolean("do_traffic_light_alert", false);
        doCollisionAlert = preferences.getBoolean("do_collision_alert", false);
        confidenceThreshold = preferences.getInt("confidence_threshold", 50) / 100.0f;
        alertThreshold = preferences.getInt("alert_threshold", 10);
        doCameraCoveredAlert = preferences.getBoolean("do_camera_covered_alert", false);
        sharpnessThreshold = preferences.getInt("sharpness_threshold", 30);
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
        for(short i = 0; i < 6; i++) {
            prevPredictions[i] = (short)(alertThreshold + 1);
        }
    }

    @Override
    @UseExperimental(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    public int onStartCommand(Intent intent, int flags, int startId) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        helper = new YoloPredictionHelper(this, confidenceThreshold);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
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
                    Bitmap resized = Bitmap.createScaledBitmap(bitmap, 416, 416, false);
                    Bitmap rotated = Bitmap.createBitmap(resized, 0, 0, 416, 416, matrix, false);
                    NotificationManager manager = getSystemService(NotificationManager.class);
                    ArrayList<YoloPredictionHelper.Recognition> predictions = new ArrayList<>();
                    try {
                        if(doCameraCoveredAlert) {
                            if(CameraCoveredAlertHelper.getSharpnessFromBitmap(bitmap) < sharpnessThreshold) {
                                if(Arrays.stream(manager.getActiveNotifications()).noneMatch((notification) -> notification.getId() == 3)) {

                                    Notification.Builder notification = null;
                                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        notification = new Notification.Builder(MainService.this, COVERED_CHANNEL_ID);
                                    } else {
                                        notification = new Notification.Builder(MainService.this);
                                    }
                                    notification.setSmallIcon(R.drawable.ic_small_icon)
                                            .setPriority(Notification.PRIORITY_MAX)
                                            .setContentTitle("???????????? ?????????")
                                            .setContentText("???????????? ????????? ????????? ??? ????????????. ?????? ?????? ????????? ???????????? ?????? ??? ????????????.");
                                    manager.notify(3, notification.build());
                                }
                            } else {
                                manager.cancel(3);
                            }
                        }

                        predictions = helper.predict(rotated);
                        if(imageUpdateCallback != null) imageUpdateCallback.accept(rotated);
                        if(predictionUpdateCallback != null) predictionUpdateCallback.accept(helper.getResultOverlay(predictions));
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                    boolean[] curPredictions = new boolean[6];
                    for(YoloPredictionHelper.Recognition prediction : predictions) {
                        curPredictions[prediction.getDetectedClass()] = true;
                        //?????? ??? ????????? ?????? ????????? ???????????? ??????
                        if(prevPredictions[prediction.getDetectedClass()] < alertThreshold) continue;
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
                                case 3: case 4: case 5:
                                    if(!doCollisionAlert) continue;
                                    notification = new Notification.Builder(MainService.this, COLLISION_CHANNEL_ID);
                                    break;
                            }
                        } else {
                            notification = new Notification.Builder(MainService.this);
                        }
                        assert notification != null;
                        notification.setSmallIcon(R.drawable.ic_small_icon).setPriority(Notification.PRIORITY_MAX);
                        switch (prediction.getDetectedClass()) {
                            case 0:
                                notification.setContentTitle("???????????? ??????")
                                        .setContentText("????????? ??????????????? ?????????????????????.");
                                break;
                            case 1:
                                notification.setContentTitle("????????? ?????????")
                                        .setContentText("??????????????? ?????? ??? ?????? ????????? ?????? ????????? ????????????????????????.");
                                break;
                            case 2:
                                notification.setContentTitle("??????????????? ?????? ??? ??????")
                                        .setContentText("????????? ??? ????????? ???????????????.");
                                break;
                            case 3: case 4:
                                notification.setContentTitle("?????? ?????? ?????? ??????")
                                        .setContentText("????????? ????????? ????????? ?????? ????????? ????????????.");
                                break;
                        }
                        System.out.println(alertThreshold);
                        manager.notify(2, notification.build());
                    }
                    image.close();
                    for(short i = 0; i < 6; i++){
                        if(curPredictions[i]) prevPredictions[i] = 0;
                        else if(prevPredictions[i] < alertThreshold) prevPredictions[i]++;
                    }
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
            NotificationChannel crosswalkChannel = new NotificationChannel(CROSSWALK_CHANNEL_ID, "Crosswalk Alert", NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel trafficLightChannel = new NotificationChannel(TRAFFIC_LIGHT_CHANNEL_ID, "Traffic Light Alert", NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel collisionChannel = new NotificationChannel(COLLISION_CHANNEL_ID, "Collision Alert", NotificationManager.IMPORTANCE_HIGH);
            NotificationChannel coveredChannel = new NotificationChannel(COVERED_CHANNEL_ID, "Lens Covered Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannels(Arrays.asList(runningChannel, crosswalkChannel, trafficLightChannel,collisionChannel,coveredChannel));
            notification = new Notification.Builder(this, RUNNING_CHANNEL_ID);
        }
        else {
            notification = new Notification.Builder(this);
        }

        notification.setSmallIcon(R.drawable.ic_small_icon)
                .setContentIntent(pendingIntent)
                .setContentTitle("??????!??? ??????????????????.")
                .setContentText("??????????????? ???????????????.")
                .setPriority(Notification.PRIORITY_MAX);
        startForeground(1, notification.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(helper != null) helper.close();
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
            case "confidence_threshold":
                helper.confidenceThreshold = sharedPreferences.getInt(key, 50) / 100.0f;
                break;
            case "alert_threshold":
                alertThreshold = sharedPreferences.getInt(key, 10);
                break;
            case "do_camera_covered_alert":
                doCameraCoveredAlert = sharedPreferences.getBoolean(key, false);
                break;
            case "sharpness_threshold":
                sharpnessThreshold = sharedPreferences.getInt(key, 30);
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
