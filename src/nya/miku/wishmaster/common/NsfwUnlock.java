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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.os.Environment;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;

public class NsfwUnlock {
    public static Set<String> getUnlockedChans(List<ChanModule> chans) {
        Set<String> set = new HashSet<>();
        try {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File unlock = new File(Environment.getExternalStorageDirectory(), ".overchan");
                if (unlock.exists()) {
                    BufferedReader reader = new BufferedReader(new FileReader(unlock));
                    String current;
                    while ((current = reader.readLine()) != null) {
                        if (!current.contains("://")) current = "http://" + current;
                        for (ChanModule chan : chans) {
                            try {
                                UrlPageModel page = chan.parseUrl(current);
                                if (page.type == UrlPageModel.TYPE_INDEXPAGE) {
                                    set.add(chan.getChanName());
                                    break;
                                }
                                
                                //single-board chans
                                UrlPageModel index = new UrlPageModel();
                                index.type = UrlPageModel.TYPE_INDEXPAGE;
                                index.chanName = chan.getChanName();
                                if (page.type == chan.parseUrl(chan.buildUrl(index)).type) set.add(chan.getChanName());
                                break;
                            } catch (Exception e) {}
                        }
                    }
                    reader.close();
                }
            }
        } catch (Exception e) {}
        return set;
    }
}
