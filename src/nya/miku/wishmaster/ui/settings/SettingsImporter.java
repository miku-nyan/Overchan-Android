/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.presentation.Subscriptions;
import nya.miku.wishmaster.ui.tabs.TabModel;

import static nya.miku.wishmaster.ui.settings.ImportExportConstants.*;

public class SettingsImporter {
    private static final String TAG = "SettingsImporter";

    /**
     * Импортирует настройки из строки в формате JSON
     *
     * @param json      настройки в виде строки в формате JSON
     * @param overwrite перезапись настроек, если true
     * @param activity
     */
    public static void Import(final String json, final boolean overwrite, final Activity activity) {
        Import(null, json, overwrite, activity);
    }

    /**
     * Импортирует настройки из файла в формате JSON
     *
     * @param filename  файл из которого нужно импортировать настройки
     * @param overwrite перезапись настроек, если true
     * @param activity
     */
    public static void Import(final File filename, final boolean overwrite, final Activity activity) {
        Import(filename, null, overwrite, activity);
    }

    /**
     * @param filename  файл из которого нужно импортировать настройки, может быть null
     * @param json_     настройки в виде строки в формате JSON, может быть null
     * @param overwrite перезапись настроек, если true
     * @param activity
     */
    private static void Import(final File filename, final String json_, final boolean overwrite, final Activity activity) {
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        final Database database = MainApplication.getInstance().database;
        final ApplicationSettings settings = MainApplication.getInstance().settings;
        progressDialog.setMessage(activity.getString(R.string.app_settings_importing));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(6);
        progressDialog.setProgress(0);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.cancel();
            }
        });
        progressDialog.show();
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    Import();
                } catch (Exception e) {
                    showMessage(e.getMessage());
                    Logger.e(TAG, e);
                    return;
                }
                showMessage(activity.getString(R.string.app_settings_import_completed));
            }
            
            private JSONArray mergeAutohide(JSONObject preferences){
                JSONArray autohide;
                try {
                    autohide = new JSONArray(preferences.getString(activity.getString(R.string.pref_key_autohide_json)));
                } catch (JSONException e) {
                    autohide = new JSONArray();
                    Logger.e(TAG, e);
                }
                JSONArray current_autohide;
                try {
                    current_autohide = new JSONArray(settings.getAutohideRulesJson());
                } catch (JSONException e) {
                    current_autohide = new JSONArray();
                    Logger.e(TAG, e);
                }
                Map<Integer, AutohideActivity.AutohideRule> autohide_set = new HashMap<Integer, AutohideActivity.AutohideRule>();
                for (int i = 0; i < autohide.length(); i++){
                    AutohideActivity.AutohideRule item = AutohideActivity.AutohideRule.fromJson(autohide.getJSONObject(i));
                    autohide_set.put(item.hashCode(), item);
                }
                for (int i = 0; i < current_autohide.length(); i++){
                    AutohideActivity.AutohideRule item = AutohideActivity.AutohideRule.fromJson(current_autohide.getJSONObject(i));
                    autohide_set.put(item.hashCode(), item);
                }
                JSONArray autohide_unique = new JSONArray();
                Iterator<Integer> iter = autohide_set.keySet().iterator();
                while (iter.hasNext()) {
                    AutohideActivity.AutohideRule item = autohide_set.get(iter.next());
                    autohide_unique.put(item.toJson());
                }
                return autohide_unique;
            }
            
            private void ImportTabs(JSONArray tabs) {
                List<TabModel> pages = new ArrayList<TabModel>();
                for (int i = 0; i < tabs.length(); i++) {
                    JSONObject tab = tabs.getJSONObject(i);
                    
                    UrlPageModel page = new UrlPageModel();
                    page.type = tab.optInt("type", 0);
                    page.chanName = tab.optString("chanName", "");
                    page.boardName = tab.optString("boardName", "");
                    page.boardPage = tab.optInt("boardPage", 0);
                    page.catalogType = tab.optInt("catalogType", 0);
                    page.threadNumber = tab.optString("threadNumber", "");
                    page.postNumber = tab.optString("postNumber", "");
                    page.searchRequest = tab.optString("searchRequest", "");
                    page.otherPath = tab.optString("otherPath", "");
                    TabModel tabModel = new TabModel();
                    tabModel.title = tab.optString("title", "");
                    tabModel.pageModel = page;
                    pages.add(tabModel);
                }
                MainApplication.getInstance().pagesToOpen = pages;
            }

            private String[] filterKeys(List<String> keys, String[] exclude){
                Iterator<String> iter = keys.iterator();
                while (iter.hasNext()) {
                    String key = iter.next();
                    for (String ex : exclude)
                        if (key.contains(ex)) {
                            iter.remove();
                            break;
                        }
                }
                return keys.toArray(new String [keys.size()]);
            }

            private String loadFromFile(final File filename) throws IOException {
                FileInputStream inputStream = null;
                inputStream = new FileInputStream(filename);
                StringBuffer json_string = new StringBuffer("");
                byte[] buffer = new byte[1024];
                int d;
                while ((d = inputStream.read(buffer)) != -1) {
                    json_string.append(new String(buffer, 0, d));
                }
                inputStream.close();
                return json_string.toString();
            }

            private void Import() throws Exception {
                if (task.isCancelled()) throw new Exception("Interrupted");
                String json_string = null;
                if (filename != null) {
                    json_string = loadFromFile(filename);
                } else if (json_ != null) {
                    json_string = json_;
                }
                JSONObject json = new JSONObject(json_string);
                // TODO Check version
                int version = json.getInt(JSON_KEY_VERSION);
                updateProgress();
                JSONArray history = json.getJSONArray(JSON_KEY_HISTORY);
                List<Database.HistoryEntry> history_list = new ArrayList<Database.HistoryEntry>();
                for (int i = 0; i < history.length(); i++)
                    history_list.add(new Database.HistoryEntry(history.getJSONObject(i)));
                database.importHistory(history_list.toArray(new Database.HistoryEntry[history_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                JSONArray favorites = json.getJSONArray(JSON_KEY_FAVORITES);
                List<Database.FavoritesEntry> favorites_list = new ArrayList<Database.FavoritesEntry>();
                for (int i = 0; i < favorites.length(); i++)
                    favorites_list.add(new Database.FavoritesEntry(favorites.getJSONObject(i)));
                database.importFavorites(favorites_list.toArray(new Database.FavoritesEntry[favorites_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                JSONArray hidden = json.getJSONArray(JSON_KEY_HIDDEN);
                List<Database.HiddenEntry> hidden_list = new ArrayList<Database.HiddenEntry>();
                for (int i = 0; i < hidden.length(); i++)
                    hidden_list.add(new Database.HiddenEntry(hidden.getJSONObject(i)));
                database.importHidden(hidden_list.toArray(new Database.HiddenEntry[hidden_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                JSONArray subscriptions = json.getJSONArray(JSON_KEY_SUBSCRIPTIONS);
                List<Subscriptions.SubscriptionEntry> subscriptions_list = new ArrayList<Subscriptions.SubscriptionEntry>();
                for (int i = 0; i < subscriptions.length(); i++)
                    subscriptions_list.add(new Subscriptions.SubscriptionEntry(subscriptions.getJSONObject(i)));
                MainApplication.getInstance().subscriptions.importSubscriptions(subscriptions_list.toArray(new Subscriptions.SubscriptionEntry[subscriptions_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                updateProgress();
                JSONObject preferences = json.getJSONObject(JSON_KEY_PREFERENCES);

                if (!overwrite) {
                    // TODO replace hard coded strings with resources
                    preferences = new JSONObject(
                            preferences, 
                            filterKeys(new ArrayList<String>(preferences.keySet()), exclude)
                    );
                    JSONArray autohide = mergeAutohide(preferences);
                    preferences.put(activity.getString(R.string.pref_key_autohide_json), autohide.toString());
                }
                try {
                    String dir = preferences.getString(activity.getString(R.string.pref_key_download_dir));
                    File download_dir = new File(dir);
                    if (!download_dir.isDirectory()) {
                        preferences.remove(activity.getString(R.string.pref_key_download_dir));
                    }
                } catch (JSONException e) {
                    Logger.e(TAG, e);
                }
                settings.setSharedPreferences(preferences);
                String theme = preferences.getString(activity.getString(R.string.pref_key_theme));
                if (theme.equals(activity.getString(R.string.pref_theme_value_custom)))
                    settings.setCustomTheme(
                            preferences.getString(activity.getString(R.string.pref_key_custom_theme_json))
                    );
                updateProgress();
                try {
                    JSONArray tabs = json.getJSONArray(JSON_KEY_TABS);
                    ImportTabs(tabs);
                } catch (JSONException e) {
                    Logger.e(TAG, e);
                }
            }

            private void updateProgress() {
                if (task.isCancelled()) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        try {
                            progressDialog.incrementProgressBy(1);
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            return;
                        }
                    }
                });
            }
            
            private void showMessage(final String message) {
                if (task.isCancelled()) return;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (task.isCancelled()) return;
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            Logger.e(TAG, e);
                            return;
                        }
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
}
