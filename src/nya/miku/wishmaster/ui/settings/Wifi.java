package nya.miku.wishmaster.ui.settings;

import nya.miku.wishmaster.common.Logger;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class Wifi extends BroadcastReceiver {
    private static final String TAG = "Wifi";
    
    private static boolean isWifi;
    
    public static void updateState(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            isWifi = activeNetwork != null && activeNetwork.getType() == ConnectivityManager.TYPE_WIFI;
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        updateState(context);
    }
    
    public static boolean isConnected() {
        return isWifi;
    }
    
}
