package com.example.stop_app;

import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.N)
public class ToggleTileService extends TileService {
    @Override
    public void onTileAdded() {
        Tile qsTile = getQsTile();
        qsTile.setState(Tile.STATE_INACTIVE);
        qsTile.updateTile();
        super.onTileAdded();
    }

    @Override
    public void onTileRemoved() {
        super.onTileRemoved();
    }

    @Override
    public void onStartListening() {
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
}
