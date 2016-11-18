package nya.miku.wishmaster.ui.settings;

import static nya.miku.wishmaster.ui.settings.ImportExportConstants.*;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
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

public class SettingsImporter {
    private static final String TAG = "SettingsImporter";
    
    public static void Import(final File filename, final boolean overwrite, final Activity activity) {
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

            private String getStringOrNull(JSONObject o, String key){
                try {
                    return o.getString(key);
                } catch (JSONException e) {
                    return null;
                }
            }
            
            private int getIntOrNull(JSONObject o, String key){
                try {
                    return o.getInt(key);
                } catch (JSONException e) {
                    return 0;
                }
            }
            
            private void ImportTabs(JSONArray tabs) {
                List<TabModel> pages = new ArrayList<TabModel>();
                for (int i = 0; i < tabs.length(); i++) {
                    JSONObject tab = tabs.getJSONObject(i);
                    
                    UrlPageModel page = new UrlPageModel();
                    page.type = getIntOrNull(tab, "type");
                    page.chanName = getStringOrNull(tab, "chanName");
                    page.boardName = getStringOrNull(tab, "boardName");
                    page.boardPage = getIntOrNull(tab, "boardPage");
                    page.catalogType = getIntOrNull(tab, "catalogType");
                    page.threadNumber = getStringOrNull(tab, "threadNumber");
                    page.postNumber = getStringOrNull(tab, "postNumber");
                    page.searchRequest = getStringOrNull(tab, "searchRequest");
                    page.otherPath = getStringOrNull(tab, "otherPath");
                    TabModel tabModel = new TabModel();
                    tabModel.title = getStringOrNull(tab, "title");
                    tabModel.pageModel = page;
                    pages.add(0, tabModel);
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
            
            private void Import() throws Exception {
                FileInputStream inputStream = null;
                inputStream = new FileInputStream(filename);
                StringBuffer json_string = new StringBuffer("");
                byte[] buffer = new byte[1024];
                int d;
                while ((d = inputStream.read(buffer)) != -1) {
                    json_string.append(new String(buffer, 0, d));
                }
                inputStream.close();
                if (task.isCancelled()) throw new Exception("Interrupted");
                JSONObject json = new JSONObject(json_string.toString());
                // TODO Check version
                long file_version;
                try {
                    file_version = json.getLong(JSON_KEY_FILE_VERSION);
                } catch (JSONException e) {
                    file_version = 0;
                }
                int version = json.getInt(JSON_KEY_VERSION);
                boolean tablet = settings.isRealTablet();
                if (file_version > 0)
                    tablet = json.getBoolean(JSON_KEY_TABLET);
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
                    String[] exclude = {
                            "PREF_KEY_CLOUDFLARE_COOKIE",
                            "PREF_KEY_CLOUDFLARE_COOKIE_DOMAIN",
                            "PREF_KEY_USE_PROXY",
                            "PREF_KEY_PROXY_HOST",
                            "PREF_KEY_PROXY_PORT",
                            "PREF_KEY_PASSWORD",
                            "PREF_KEY_USE_HTTPS",
                            "PREF_KEY_ONLY_NEW_POSTS",
                            "PREF_KEY_CAPTCHA_AUTO_UPDATE",
                            "PREF_KEY_CACHE_MAXSIZE",
                            "PREF_KEY_SETTINGS_IMPORT_OVERWRITE",
                    };
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
