package com.stop.stop_app;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ToggleTileService extends TileService {
    @Override
    public void onTileAdded() {
        init(getQsTile());
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
        init(getQsTile());
        super.onStartListening();
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
    }

    @Override
    public void onClick() {
        Tile qsTile = getQsTile();
        switch (qsTile.getState()) {
            case Tile.STATE_ACTIVE:
                sendBroadcast(new Intent(this, StopServiceBroadcastReceiver.class));
                qsTile.setState(Tile.STATE_INACTIVE);
                break;
            case Tile.STATE_INACTIVE:
                startService(new Intent(this, MainService.class));
                qsTile.setState(Tile.STATE_ACTIVE);
                break;
        }
        qsTile.updateTile();
        super.onClick();
    }

    public void init(Tile qsTile) {
        ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        boolean isRunning = false;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)){
            if("com.stop.stop_app.MainService".equals(service.service.getClassName())) {
                isRunning = true;
            }
        }
        if (isRunning) {
            qsTile.setState(Tile.STATE_ACTIVE);
        } else {
            qsTile.setState(Tile.STATE_INACTIVE);
        }
        qsTile.updateTile();
    }

}
