/* Copyright (c) 2026 XiaoLeXLDW. */
package dev.xiaolexldw.fiveg;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

/** Quick Settings entry point; authorization is handled by the unlocked application. */
public class FiveGTileService extends TileService {
    private static final String TAG = "FiveGTile";
    private static final String TILE_CLASS = "com.ysy.switcherfiveg.SwitcherTileService";
    private FiveGController controller;
    private Icon icon;
    private boolean listening;
    private final FiveGController.Listener listener = this::render;

    @Override public void onCreate() {
        super.onCreate();
        controller = FiveGController.get(this);
    }

    @Override public void onStartListening() {
        super.onStartListening();
        icon = TileIcon.create();
        listening = true;
        controller.addListener(listener);
        controller.refresh();
    }

    @Override public void onTileAdded() {
        super.onTileAdded();
        requestUpdate(this);
    }

    @Override public void onStopListening() {
        listening = false;
        controller.removeListener(listener);
        super.onStopListening();
    }

    @Override public void onDestroy() {
        listening = false;
        if (controller != null) controller.removeListener(listener);
        super.onDestroy();
    }

    @Override public void onClick() {
        super.onClick();
        if (isLocked()) unlockAndRun(this::handleClick);
        else handleClick();
    }

    private void handleClick() {
        FiveGController.State state = controller.state();
        if (state.busy) return;
        if (state.access != FiveGController.Access.READY) {
            openApplication();
            return;
        }
        // The controller always rereads the actual setting before deriving a toggle.
        controller.toggle();
    }

    private void render(FiveGController.State state) {
        if (!listening) return;
        try {
            Tile tile = getQsTile();
            if (tile == null) return;
            String label = TilePreferences.label(this);
            tile.setLabel(label);
            tile.setIcon(icon);
            boolean known = state.access == FiveGController.Access.READY && state.enabled >= 0 && !state.busy;
            // UNAVAILABLE suppresses clicks. Keep connection/authorization states tappable so they open the app.
            int tileState = state.busy || state.access == FiveGController.Access.UNSUPPORTED ? Tile.STATE_UNAVAILABLE
                    : known && state.enabled == 1 ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            tile.setState(tileState);
            tile.setSubtitle(state.status);
            tile.setStateDescription(state.status);
            tile.setContentDescription(label + "，" + state.status);
            tile.updateTile();
        } catch (IllegalStateException | SecurityException disconnected) {
            Log.d(TAG, "Tile connection ended", disconnected);
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private void openApplication() {
        Intent intent = LauncherVisibility.settingsIntent(this);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                PendingIntent pending = PendingIntent.getActivity(this, 5, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                startActivityAndCollapse(pending);
            } else {
                // Android 11-13 lack the PendingIntent overload; the old call is only used there.
                startActivityAndCollapse(intent);
            }
        } catch (RuntimeException unavailable) {
            Log.w(TAG, "Could not open application", unavailable);
        }
    }

    /** Requests an update when allowed; normal tiles also refresh whenever the panel opens. */
    public static void requestUpdate(Context context) {
        try {
            TileService.requestListeningState(context,
                    new ComponentName(context.getPackageName(), TILE_CLASS));
        } catch (RuntimeException unavailable) {
            Log.d(TAG, "Tile update deferred until next listen", unavailable);
        }
    }
}
