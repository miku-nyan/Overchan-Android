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

package nya.miku.wishmaster.http;

import java.lang.reflect.Method;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.PriorityThreadFactory;

public class SSLCompatibility {
    private static final String TAG = "SSLCompatibility";
    
    private static volatile Thread workingThread = null;
    
    /**
     * Вызывается один раз в начале работы приложения (из {@link android.app.Application#onCreate()})
     */
    public static void fixSSLs(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            workingThread = PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                @Override
                public void run() {
                    installProviderImpl(context);
                    workingThread = null;
                }
            });
            workingThread.start();
        }
    }
    
    public static void waitIfInstallingAsync() {
        if (workingThread == null) return;
        synchronized (SSLCompatibility.class) {
            try {
                Thread thread = workingThread;
                if (thread == null) return;
                thread.join(10000);
                if (thread.isAlive()) Logger.e(TAG, "security provider installation timeout");
                workingThread = null;
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }
    
    private static void installProviderImpl(Context c) {
        try {
            Logger.d(TAG, "trying to install security provider");
            Context remote = c.createPackageContext("com.google.android.gms", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            Method method = remote.getClassLoader().loadClass("com.google.android.gms.common.security.ProviderInstallerImpl").
                    getMethod("insertProvider", Context.class);
            method.invoke(null, remote);
            Logger.d(TAG, "security provider installed");
        } catch (PackageManager.NameNotFoundException e) {
            Logger.d(TAG, "package not found");
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
}
