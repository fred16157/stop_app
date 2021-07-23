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
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;
import androidx.preference.PreferenceManager;

import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.TextRecognizerOptions;

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
    final String BUS_CHANNEL_ID = "ALERT_BUS";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    public Consumer<Bitmap> imageUpdateCallback;
    public Consumer<Bitmap> predictionUpdateCallback;
    public Consumer<Long> predictionTimeUpdateCallback;
    public boolean doCrosswalkAlert;
    public boolean doTrafficLightAlert;
    public boolean doCollisionAlert;
    public boolean doBusAlert;
    public String targetBus;
    public int alertThreshold;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private YuvToRgbConverter converter;
    private YoloPredictionHelper helper;
    private boolean isBusy = false;
    private boolean isOCRBusy = false;
    private final short[] prevPredictions = new short[]{11, 11, 11, 11, 11, 11};
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
        doBusAlert = preferences.getBoolean("do_bus_alert", false);
        targetBus = preferences.getString("target_bus", "");
        alertThreshold = preferences.getInt("alert_threshold", 10);
        preferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);

        for(short i = 0; i < 6; i++) {
            prevPredictions[i] = (short)(alertThreshold + 1);
        }
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
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, 416, 416, matrix, false);
                    ArrayList<YoloPredictionHelper.Recognition> predictions = helper.predict(rotated);
                    if(imageUpdateCallback != null) imageUpdateCallback.accept(rotated);
                    if(predictionUpdateCallback != null) predictionUpdateCallback.accept(helper.getResultOverlay(predictions));
                    boolean[] curPredictions = new boolean[6];
                    for(YoloPredictionHelper.Recognition prediction : predictions) {
//                        if(prediction.getDetectedClass() == 5) {
//                            if(isOCRBusy) continue;
//                            isOCRBusy = true;
//                            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
//                            recognizer.process(InputImage.fromBitmap(rotated, 90)).addOnSuccessListener((text) -> {
//                                Notification.Builder notification = null;
//                                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                                    notification = new Notification.Builder(MainService.this, BUS_CHANNEL_ID);
//                                }
//                                else {
//                                    notification = new Notification.Builder(MainService.this);
//                                }
//                                notification.setSmallIcon(R.mipmap.ic_launcher)
//                                        .setPriority(Notification.PRIORITY_MAX)
//                                        .setContentTitle("버스 번호 알림")
//                                        .setContentText(text.getText());
//                                getSystemService(NotificationManager.class).notify(3, notification.build());
//                                isOCRBusy = false;
//                            });
//                            continue;
//                        }
                        curPredictions[prediction.getDetectedClass()] = true;
                        //방금 전 예측에 같은 경고를 보냈다면 멈춤
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
                                case 3: case 4:
                                    if(!doCollisionAlert) continue;
                                    notification = new Notification.Builder(MainService.this, COLLISION_CHANNEL_ID);
                                    break;
                                case 5:
                                    if(!doBusAlert) continue;
                                    notification = new Notification.Builder(MainService.this, BUS_CHANNEL_ID);
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
                            case 5:
                                TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                                Text visionText;
                                try {
                                    visionText = Tasks.await(recognizer.process(InputImage.fromBitmap(rotated, 90)));
                                    recognizer.close();
                                } catch (ExecutionException | InterruptedException e) {
                                    e.printStackTrace();
                                    continue;
                                }
                                System.out.println(visionText.getText());
                                if(!visionText.getText().contains(targetBus)) continue;
                                prevPredictions[5] = 0;
                                notification.setContentTitle("버스 탐지됨")
                                        .setContentText("지정한 버스 " + targetBus + " 가 탐지되었습니다.");
                                break;
                        }
                        getSystemService(NotificationManager.class).notify(2, notification.build());
                    }
                    image.close();
                    for(short i = 0; i < 6; i++){
                        if(curPredictions[i] && i != 5) prevPredictions[i] = 0;
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
            manager.createNotificationChannel(runningChannel);
            NotificationChannel crosswalkChannel = new NotificationChannel(CROSSWALK_CHANNEL_ID, "Crosswalk Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(crosswalkChannel);
            NotificationChannel trafficLightChannel = new NotificationChannel(TRAFFIC_LIGHT_CHANNEL_ID, "Traffic Light Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(trafficLightChannel);
            NotificationChannel collisionChannel = new NotificationChannel(COLLISION_CHANNEL_ID, "Collision Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(collisionChannel);
            NotificationChannel busChannel = new NotificationChannel(BUS_CHANNEL_ID, "Collision Alert", NotificationManager.IMPORTANCE_HIGH);
            manager.createNotificationChannel(busChannel);
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
            case "do_bus_alert":
                doBusAlert = sharedPreferences.getBoolean(key, false);
                break;
            case "target_bus":
                targetBus = sharedPreferences.getString(key, "");
                break;
            case "alert_threshold":
                alertThreshold = sharedPreferences.getInt(key, 10);
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
