package nya.miku.wishmaster.ui.settings;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
                if (overwrite)
                    MainApplication.getInstance().database.clearHistory();
                for (int i = 0; i < history.length(); i++) {
                    MainApplication.getInstance().database.addHistory(new Database.HistoryEntry(history.getJSONObject(i)));
                }
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(2);
                JSONArray favorites = json.getJSONArray("favorites");
                if (overwrite)
                    MainApplication.getInstance().database.clearFavorites();
                for (int i = 0; i < favorites.length(); i++) {
                    MainApplication.getInstance().database.addFavorite(new Database.FavoritesEntry(favorites.getJSONObject(i)));
                }
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(3);
                JSONArray hidden = json.getJSONArray("hidden");
                /**
                 * TODO implement database.clearHidden()
                 * if (overwrite)
                 *     MainApplication.getInstance().database.clearHidden();
                 */
                for (int i = 0; i < hidden.length(); i++) {
                    MainApplication.getInstance().database.addHidden(new Database.HiddenEntry(hidden.getJSONObject(i)));
                }
                if (task.isCancelled()) throw new Exception("Interrupted");
                progressDialog.setProgress(4);
                JSONArray subscriptions = json.getJSONArray("subscriptions");
                if (overwrite) {
                    MainApplication.getInstance().subscriptions.reset();
                    MainApplication.getInstance().settings.setSubscriptionsClear(true);
                }
                for (int i = 0; i < subscriptions.length(); i++) {
                    MainApplication.getInstance().subscriptions.addSubscription(new Subscriptions.SubscriptionEntry(subscriptions.getJSONObject(i)));
                }
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
                    JSONArray autohide = new JSONArray();
                    try {
                        autohide = new JSONArray(preferences.getString("PREF_KEY_AUTOHIDE_JSON"));
                    } catch (JSONException e) {
                        Logger.e(TAG, e);
                    }
                    JSONArray current_autohide = new JSONArray(MainApplication.getInstance().settings.getAutohideRulesJson());
                    //TODO implement merging of autohide rules
                }
                MainApplication.getInstance().settings.setSharedPreferences(preferences);
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
