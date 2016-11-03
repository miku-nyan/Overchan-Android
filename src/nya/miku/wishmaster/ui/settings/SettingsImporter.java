package nya.miku.wishmaster.ui.settings;

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
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.ui.Database;
import nya.miku.wishmaster.ui.presentation.Subscriptions;

public class SettingsImporter {
    private static final String TAG = "SettingsImporter";

    public static void Import(final File filename, final boolean overwrite, final Activity activity) {
        final CancellableTask task = new CancellableTask.BaseCancellableTask();
        final ProgressDialog progressDialog = new ProgressDialog(activity);
        progressDialog.setMessage(activity.getString(R.string.app_settings_importing));
        progressDialog.setIndeterminate(false);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMax(5);
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
                    current_autohide = new JSONArray(MainApplication.getInstance().settings.getAutohideRulesJson());
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
            
            private void Import() throws Exception {
                progressDialog.setProgress(0);
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
                int version = json.getInt("version");
                progressDialog.setProgress(1);
                JSONArray history = json.getJSONArray("history");
                List<Database.HistoryEntry> history_list = new ArrayList<Database.HistoryEntry>();
                for (int i = 0; i < history.length(); i++)
                    history_list.add(new Database.HistoryEntry(history.getJSONObject(i)));
                MainApplication.getInstance().database.importHistory(history_list.toArray(new Database.HistoryEntry[history_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(2);
                JSONArray favorites = json.getJSONArray("favorites");
                List<Database.FavoritesEntry> favorites_list = new ArrayList<Database.FavoritesEntry>();
                for (int i = 0; i < favorites.length(); i++)
                    favorites_list.add(new Database.FavoritesEntry(favorites.getJSONObject(i)));
                MainApplication.getInstance().database.importFavorites(favorites_list.toArray(new Database.FavoritesEntry[favorites_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(3);
                JSONArray hidden = json.getJSONArray("hidden");
                List<Database.HiddenEntry> hidden_list = new ArrayList<Database.HiddenEntry>();
                for (int i = 0; i < hidden.length(); i++)
                    hidden_list.add(new Database.HiddenEntry(hidden.getJSONObject(i)));
                MainApplication.getInstance().database.importHidden(hidden_list.toArray(new Database.HiddenEntry[hidden_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(4);
                JSONArray subscriptions = json.getJSONArray("subscriptions");
                List<Subscriptions.SubscriptionEntry> subscriptions_list = new ArrayList<Subscriptions.SubscriptionEntry>();
                for (int i = 0; i < subscriptions.length(); i++)
                    subscriptions_list.add(new Subscriptions.SubscriptionEntry(subscriptions.getJSONObject(i)));
                MainApplication.getInstance().subscriptions.importSubscriptions(subscriptions_list.toArray(new Subscriptions.SubscriptionEntry[subscriptions_list.size()]), overwrite);
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(5);
                JSONObject preferences = json.getJSONObject("preferences");

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
                    List<String> keys = new ArrayList<String>(preferences.keySet());
                    Iterator<String> iter = keys.iterator();
                    while (iter.hasNext()) {
                        String key = iter.next();
                        for (String ex : exclude)
                            if (key.contains(ex)) {
                                iter.remove();
                                break;
                            }
                    }
                    preferences = new JSONObject(preferences, keys.toArray(new String [keys.size()]));
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
                MainApplication.getInstance().settings.setSharedPreferences(preferences);
                String theme = preferences.getString(activity.getString(R.string.pref_key_theme));
                if (theme.equals(activity.getString(R.string.pref_theme_value_custom)))
                    MainApplication.getInstance().settings.setCustomTheme(
                            preferences.getString(activity.getString(R.string.pref_key_custom_theme_json))
                    );
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
