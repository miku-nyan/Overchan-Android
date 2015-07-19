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

package nya.miku.wishmaster.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

public class NsfwUnlock {
    public static boolean isUnlocked() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            File unlock = new File(Environment.getExternalStorageDirectory(), ".overchan");
            if (unlock.exists()) {
                byte[] controlValue = getUnlockData();
                if (controlValue != null) {
                    InputStream is = null;
                    try {
                        is = new FileInputStream(unlock);
                        byte[] value = new byte[controlValue.length];
                        is.read(value);
                        for (int i=0; i<controlValue.length; ++i) if (controlValue[i] != value[i]) return false;
                        return true;
                    } catch (Exception e) {
                        return false;
                    } finally {
                        if (is != null) IOUtils.closeQuietly(is);
                    }
                }
            }
        }
        return false;
    }
    
    @SuppressWarnings("deprecation")
    public static byte[] getUnlockData() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(Build.MODEL).append(';').append(Build.VERSION.SDK_INT).append(';');
            StatFs fs = new StatFs(Environment.getExternalStorageDirectory().getPath());
            sb.append(fs.getBlockCount()).append(';').append(fs.getBlockSize());
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.reset();
            messageDigest.update(sb.toString().getBytes());
            return messageDigest.digest();
        } catch (Exception e) {
            return null;
        }
    }
}
