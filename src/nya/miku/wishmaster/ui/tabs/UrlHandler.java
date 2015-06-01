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

import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.ui.MainActivity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.provider.Browser;
import android.widget.Toast;

public class UrlHandler {
    private UrlHandler() {}
    
    public static void open(final String url, final MainActivity activity) {
        TabModel model = getTabModel(getPageModel(url), activity.getResources());
        if (model != null) {
            open(model, activity, true);
            return;
        }
        boolean isEmail = url.toLowerCase(Locale.US).startsWith("mailto:");
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    launchExternalBrowser(activity, url);
                }
            }
        };
        if (MainApplication.getInstance().settings.askExternalLinks()) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity).
                    setTitle(isEmail ? R.string.dialog_external_mail_title : R.string.dialog_external_url_title).
                    setMessage(activity.getString(isEmail ? R.string.dialog_external_mail_text : R.string.dialog_external_url_text, url)).
                    setPositiveButton(android.R.string.yes, dialogClickListener).
                    setNegativeButton(android.R.string.no, dialogClickListener);
            dialogBuilder.show();
        } else {
            launchExternalBrowser(activity, url);
        }
    }
    
    public static void open(UrlPageModel urlPageModel, MainActivity activity) {
        open(urlPageModel, activity, true, null);
    }
    
    public static void open(UrlPageModel urlPageModel, MainActivity activity, boolean switchAfter, String tabTitle) {
        TabModel model = getTabModel(urlPageModel, activity.getResources());
        if (tabTitle != null) model.title = tabTitle;
        open(model, activity, switchAfter);
    }
    
    private static void open(TabModel model, MainActivity activity, boolean switchAfter) {
        TabsAdapter tabsAdapter = activity.tabsAdapter;
        for (int i=0; i<tabsAdapter.getCount(); ++i) {
            if (tabsAdapter.getItem(i).hash != null && tabsAdapter.getItem(i).hash.equals(model.hash)) {
                tabsAdapter.getItem(i).startItemNumber = model.startItemNumber;
                tabsAdapter.getItem(i).startItemTop = 0;
                tabsAdapter.getItem(i).forceUpdate = true;
                if (switchAfter) tabsAdapter.setSelectedItem(i);
                return;
            }
        }
        
        int selected = tabsAdapter.getSelectedItem();
        if (selected >= 0 && selected < tabsAdapter.getCount()) {
            tabsAdapter.insert(model, selected + 1);
        } else {
            tabsAdapter.add(model);
        }
        if (switchAfter) tabsAdapter.setSelectedItemId(model.id);
    }
    
    public static TabModel getTabModel(UrlPageModel pageModel, Resources resources) {
        if (pageModel == null) return null;
        
        TabModel model = new TabModel();
        model.type = TabModel.TYPE_NORMAL;
        model.id = new Random().nextLong();
        model.pageModel = pageModel;
        switch (pageModel.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                model.title = pageModel.chanName;
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                model.title = resources.getString(R.string.tabs_title_boardpage, pageModel.boardName, pageModel.boardPage);
                break;
            case UrlPageModel.TYPE_CATALOGPAGE:
                model.title = resources.getString(R.string.tabs_title_catalogpage, pageModel.boardName);
                break;
            case UrlPageModel.TYPE_SEARCHPAGE:
                model.title = resources.getString(R.string.tabs_title_searchpage, pageModel.boardName, pageModel.searchRequest);
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                model.title = resources.getString(R.string.tabs_title_threadpage, pageModel.boardName, pageModel.threadNumber);
                break;
        }
        if (pageModel.type == UrlPageModel.TYPE_THREADPAGE) model.autoupdateBackground = true;
        model.startItemNumber = model.pageModel.postNumber;
        model.forceUpdate = true;
        try {
            model.hash = ChanModels.hashUrlPageModel(model.pageModel);
        } catch (IllegalArgumentException e) {
            return null;
        }
        try {
            model.webUrl = MainApplication.getInstance().getChanModule(pageModel.chanName).buildUrl(pageModel);
        } catch (IllegalArgumentException e) {
            model.webUrl = null;
        }
        return model;
    }
    
    public static UrlPageModel getPageModel(String url) {
        if (Uri.parse(url).getScheme() == null) url = "http://" + url;
        UrlPageModel pageModel = null;
        Iterator<ChanModule> chansIterator = MainApplication.getInstance().chanModulesList.iterator();
        while (pageModel == null && chansIterator.hasNext()) {
            ChanModule chan = chansIterator.next();
            try {
                pageModel = chan.parseUrl(url);
            } catch (Exception e) {/* url не распознался данным чаном, пробуем следующий */}
        }
        return pageModel;
    }
    
    public static void launchExternalBrowser(Context context, String url) {
        if (Uri.parse(url).getScheme() == null) url = "http://" + url;
        Uri uri = Uri.parse(url);
        
        Intent browser = new Intent(Intent.ACTION_VIEW, uri);
        browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        browser.putExtra(Browser.EXTRA_APPLICATION_ID, context.getPackageName());
        try {
            context.startActivity(browser);
        } catch (Exception e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();;
        }
    }
}
