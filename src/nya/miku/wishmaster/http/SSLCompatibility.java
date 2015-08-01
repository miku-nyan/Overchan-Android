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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.Context;
import android.os.Build;
import nya.miku.wishmaster.common.Logger;

public class SSLCompatibility {
    private static final String TAG = "SSLCompatibility";
    
    private static AtomicBoolean fixed = new AtomicBoolean(false);
    
    public static void fixSSLs(Context c) {
        if (fixed.compareAndSet(false, true)) {
            try {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.FROYO) {
                    Logger.d(TAG, "fix 'supportedProtocol' value of SSL socket factory");
                    Class<?> classOpenSSLSocketImpl = Class.forName("org.apache.harmony.xnet.provider.jsse.OpenSSLSocketImpl");
                    Field field = classOpenSSLSocketImpl.getDeclaredField("supportedProtocols");
                    field.setAccessible(true);
                    field.set(null, new String[] { "SSLv3", "SSLv3", "TLSv1" });
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                    Logger.d(TAG, "try to install security provider");
                    Context remote = c.createPackageContext("com.google.android.gms", Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
                    Method method = remote.getClassLoader().loadClass("com.google.android.gms.common.security.ProviderInstallerImpl").
                            getMethod("insertProvider", Context.class);
                    method.invoke(null, remote);
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }
}
