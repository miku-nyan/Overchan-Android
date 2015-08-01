/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2015  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
