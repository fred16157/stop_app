package com.example.stop_app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainService extends LifecycleService {
    final String RUNNING_CHANNEL_ID = "ALERT_RUNNING";
    final String WARNING_CHANNEL_ID = "ALERT_WARNING";
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    public Consumer<Bitmap> imageUpdateCallback;
    public Consumer<Bitmap> predictionUpdateCallback;
    public Consumer<Long> predictionTimeUpdateCallback;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private YuvToRgbConverter converter;
    private DeeplabPredictionHelper helper;
    private boolean isBusy = false;
    private long lastAlerted = 0;
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
    }

    @Override
    @UseExperimental(markerClass = androidx.camera.core.ExperimentalGetImage.class)
    public int onStartCommand(Intent intent, int flags, int startId) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        helper = new DeeplabPredictionHelper(this, DeeplabPredictionHelper.PredictionSize.SIZE_513);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(513, 513))
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
                    Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, 513, 513, matrix, false);
                    TensorBuffer buffer = helper.predict(rotated);
                    Pair<Bitmap,Boolean> predicted = helper.fetchArgmax(buffer.getBuffer(), DeeplabPredictionHelper.PredictionSize.SIZE_513);
                    if(imageUpdateCallback != null) imageUpdateCallback.accept(rotated);
                    if(predictionUpdateCallback != null) predictionUpdateCallback.accept(predicted.first);

                    if(predicted.second && SystemClock.uptimeMillis() - lastAlerted > 120000) {
                        lastAlerted = SystemClock.uptimeMillis();
                        Notification.Builder notification;
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            notification = new Notification.Builder(MainService.this, WARNING_CHANNEL_ID);
                        } else {
                            notification = new Notification.Builder(MainService.this);
                        }
                        notification.setSmallIcon(R.mipmap.ic_launcher)
                                .setContentTitle("경고")
                                .setContentText("전방에 차도가 감지되었습니다. 주변 상황에 주의해주세요.")
                                .setPriority(Notification.PRIORITY_MAX);
                        NotificationManager notificationManager = getSystemService(NotificationManager.class);
                        notificationManager.notify(2, notification.build());
                    }
                    image.close();
                    if(predictionTimeUpdateCallback != null) predictionTimeUpdateCallback.accept(SystemClock.uptimeMillis() - startTime);
                    isBusy = false;
                });
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                stopSelf(1223);
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
            NotificationChannel runningChannel = new NotificationChannel(RUNNING_CHANNEL_ID, "Running Alert", NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(runningChannel);
            NotificationChannel warningChannel = new NotificationChannel(WARNING_CHANNEL_ID, "Warning Alert", NotificationManager.IMPORTANCE_HIGH);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                    .createNotificationChannel(warningChannel);
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
