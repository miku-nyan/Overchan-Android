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

package nya.miku.wishmaster.ui.tabs;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

import org.apache.commons.lang3.tuple.Pair;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.CryptoUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.containers.ReadableContainer;
import nya.miku.wishmaster.ui.MainActivity;
import nya.miku.wishmaster.ui.downloading.DownloadingService;
import android.content.res.Resources;
import android.widget.Toast;

public class LocalHandler {
    private static final String TAG = "LocalHandler";
    
    public static void open(final String filename, final MainActivity activity) {
        TabModel model = getTabModel(filename, activity.getResources());
        if (model == null) {
            Toast.makeText(activity, R.string.error_open_local, Toast.LENGTH_LONG).show();
            return;
        }
        
        TabsAdapter tabsAdapter = activity.tabsAdapter;
        for (int i=0; i<tabsAdapter.getCount(); ++i) {
            if (tabsAdapter.getItem(i).hash != null && tabsAdapter.getItem(i).hash.equals(model.hash)) {
                tabsAdapter.getItem(i).forceUpdate = true;
                tabsAdapter.setSelectedItem(i);
                return;
            }
        }
        
        tabsAdapter.add(model);
        tabsAdapter.setSelectedItemId(model.id);
    }
    
    public static TabModel getTabModel(String filename, Resources resources) {
        File file = new File(filename);
        if (file.exists() && !file.isDirectory()) {
            String lfilename = filename.toLowerCase(Locale.US);
            if (!lfilename.endsWith(".zip") && !lfilename.endsWith(".mht") && !lfilename.endsWith(".mhtml")) {
                file = file.getParentFile();
                filename = file.getAbsolutePath();
            }
        }
        
        ReadableContainer zip = null;
        UrlPageModel pageModel;
        String pageTitle;
        try {
            zip = ReadableContainer.obtain(file);
            Pair<String, UrlPageModel> p = MainApplication.getInstance().serializer.loadPageInfo(zip.openStream(DownloadingService.MAIN_OBJECT_FILE));
            pageTitle = p.getLeft();
            pageModel = p.getRight();
        } catch (Exception e) {
            Logger.e(TAG, e);
            MainApplication.getInstance().database.removeSavedThread(filename);
            return null;
        } finally {
            try {
                if (zip != null) zip.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        TabModel model = new TabModel();
        model.type = TabModel.TYPE_LOCAL;
        model.id = new Random().nextLong();
        model.title = pageTitle;
        model.pageModel = pageModel;
        model.hash = CryptoUtils.computeMD5(filename);
        try {
            model.webUrl = MainApplication.getInstance().getChanModule(pageModel.chanName).buildUrl(pageModel);
        } catch (IllegalArgumentException e) {
            model.webUrl = null;
        }
        model.localFilePath = filename;
        model.forceUpdate = true;
        return model;
    }
}
