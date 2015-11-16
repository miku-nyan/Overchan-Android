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

package nya.miku.wishmaster.ui.downloading;

import java.util.HashSet;
import java.util.Set;

/**
 * Синхронизация загрузок (блокировка загружающихся в данный момент файлов)
 * @author miku-nyan
 *
 */
public class DownloadingLocker {
    private Set<String> currentDownloads;
    
    public DownloadingLocker() {
        currentDownloads = new HashSet<String>();
    }
    
    public boolean lock(String filename) {
        synchronized (currentDownloads) {
            if (currentDownloads.contains(filename)) return false;
            currentDownloads.add(filename);
            return true;
        }
    }
    
    public void unlock(String filename) {
        synchronized (currentDownloads) {
            currentDownloads.remove(filename);
            currentDownloads.notifyAll();
        }
    }
    
    public boolean isLocked(String filename) {
        synchronized (currentDownloads) {
            return currentDownloads.contains(filename);
        }
    }
    
    public void waitUnlock(String filename) {
        synchronized (currentDownloads) {
            while (currentDownloads.contains(filename)) {
                try {
                    currentDownloads.wait();
                } catch (Exception e) {
                }
            }
        }
    }
    
}
