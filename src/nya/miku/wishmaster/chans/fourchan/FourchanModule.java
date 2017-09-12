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

package nya.miku.wishmaster.chans.fourchan;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.support.v4.content.res.ResourcesCompat;
import android.text.Html;
import android.text.InputType;
import android.webkit.WebView;
import android.widget.Toast;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.CloudflareChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.interactive.SimpleCaptchaException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class FourchanModule extends CloudflareChanModule {
    
    static final String CHAN_NAME = "4chan.org";
    
    private static final boolean NEW_RECAPTCHA_DEFAULT = Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD_MR1;
    
    private static final String PREF_KEY_NEW_RECAPTCHA = "PREF_KEY_NEW_RECAPTCHA1";
    private static final String PREF_KEY_NEW_RECAPTCHA_FALLBACK = "PREF_KEY_NEW_RECAPTCHA_FALLBACK";
    private static final String PREF_KEY_PASS_TOKEN = "PREF_KEY_PASS_TOKEN";
    private static final String PREF_KEY_PASS_PIN = "PREF_KEY_PASS_PIN";
    private static final String PREF_KEY_PASS_COOKIE = "PREF_KEY_PASS_COOKIE";
    
    static final String RECAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
    
    private boolean usingPasscode = false;
    
    private Map<String, BoardModel> boardsMap = null;
    
    private Recaptcha recaptcha = null;
    
    private Recaptcha reportRecaptcha = null;
    private String reportCaptchaAnswer = null;
    
    private static final Pattern ERROR_POSTING = Pattern.compile("<span id=\"errmsg\"(?:[^>]*)>(.*?)(?:</span>|<br)");
    private static final Pattern SUCCESS_POSTING = Pattern.compile("<!-- thread:(\\d+),no:(\\d+) -->");
    
    public FourchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "4chan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_4chan, null);
    }
    
    @Override
    protected void initHttpClient() {
        super.initHttpClient();
        setPasscodeCookie(preferences.getString(getSharedKey(PREF_KEY_PASS_COOKIE), ""), false);
    }
    
    private void addPasscodePreference(PreferenceGroup preferenceGroup) {
        final Context context = preferenceGroup.getContext();
        PreferenceScreen passScreen = preferenceGroup.getPreferenceManager().createPreferenceScreen(context);
        passScreen.setTitle("4chan pass");
        EditTextPreference passTokenPreference = new EditTextPreference(context);
        EditTextPreference passPINPreference = new EditTextPreference(context);
        Preference passLoginPreference = new Preference(context);
        Preference passClearPreference = new Preference(context);
        passTokenPreference.setTitle("Token");
        passTokenPreference.setDialogTitle("Token");
        passTokenPreference.setKey(getSharedKey(PREF_KEY_PASS_TOKEN));
        passTokenPreference.getEditText().setSingleLine();
        passTokenPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        passPINPreference.setTitle("PIN");
        passPINPreference.setDialogTitle("PIN");
        passPINPreference.setKey(getSharedKey(PREF_KEY_PASS_PIN));
        passPINPreference.getEditText().setSingleLine();
        passPINPreference.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        passLoginPreference.setTitle("Log In");
        passLoginPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                if (!useHttps()) Toast.makeText(context, "Using HTTPS even if HTTP is selected", Toast.LENGTH_SHORT).show();
                final String token = preferences.getString(getSharedKey(PREF_KEY_PASS_TOKEN), "");
                final String pin = preferences.getString(getSharedKey(PREF_KEY_PASS_PIN), "");
                final String authUrl = "https://sys.4chan.org/auth"; //only https
                final CancellableTask passAuthTask = new CancellableTask.BaseCancellableTask();
                final ProgressDialog passAuthProgressDialog = new ProgressDialog(context);
                passAuthProgressDialog.setMessage("Logging in");
                passAuthProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        passAuthTask.cancel();
                    }
                });
                passAuthProgressDialog.setCanceledOnTouchOutside(false);
                passAuthProgressDialog.show();
                Async.runAsync(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (passAuthTask.isCancelled()) return;
                            setPasscodeCookie(null, true);
                            List<NameValuePair> pairs = new ArrayList<NameValuePair>();
                            pairs.add(new BasicNameValuePair("act", "do_login"));
                            pairs.add(new BasicNameValuePair("id", token));
                            pairs.add(new BasicNameValuePair("pin", pin));
                            HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).build();
                            String response = HttpStreamer.getInstance().getStringFromUrl(authUrl, request, httpClient, null, passAuthTask, false);
                            if (passAuthTask.isCancelled()) return;
                            if (response.contains("Your device is now authorized")) {
                                String passId = null;
                                for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
                                    if (cookie.getName().equals("pass_id")) {
                                        String value = cookie.getValue();
                                        if (!value.equals("0")) {
                                            passId = value;
                                            break;
                                        }
                                    }
                                }
                                if (passId == null) {
                                    showToast("Could not get pass id");
                                } else {
                                    setPasscodeCookie(passId, true);
                                    showToast("Success! Your device is now authorized.");
                                }
                            } else if (response.contains("Your Token must be exactly 10 characters")) {
                                showToast("Incorrect token");
                            } else if (response.contains("You have left one or more fields blank")) {
                                showToast("You have left one or more fields blank");
                            } else if (response.contains("Incorrect Token or PIN")) {
                                showToast("Incorrect Token or PIN");
                            } else {
                                Matcher m = Pattern.compile("<strong style=\"color: red; font-size: larger;\">(.*?)</strong>").matcher(response);
                                if (m.find()) {
                                    showToast(m.group(1));
                                } else {
                                    showWebView(response);
                                }
                            }
                        } catch (Exception e) {
                            showToast(e.getMessage() == null ? resources.getString(R.string.error_unknown) : e.getMessage());
                        } finally {
                            passAuthProgressDialog.dismiss();
                        }
                    }
                    private void showToast(final String message) {
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    }
                    private void showWebView(final String html) {
                        if (context instanceof Activity) {
                            ((Activity) context).runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    WebView webView = new WebView(context);
                                    webView.getSettings().setSupportZoom(true);
                                    webView.loadData(html, "text/html", null);
                                    new AlertDialog.Builder(context).setView(webView).setNeutralButton(android.R.string.ok, null).show();
                                }
                            });
                        }
                    }
                });
                return true;
            }
        });
        passClearPreference.setTitle("Reset pass cookie");
        passClearPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                setPasscodeCookie(null, true);
                Toast.makeText(context, "Cookie is reset", Toast.LENGTH_LONG).show();
                return true;
            }
        });
        passScreen.addPreference(passTokenPreference);
        passScreen.addPreference(passPINPreference);
        passScreen.addPreference(passLoginPreference);
        passScreen.addPreference(passClearPreference);
        preferenceGroup.addPreference(passScreen);
    }
    
    private void setPasscodeCookie(String cookie, boolean saveToPreferences) {
        if (cookie == null || cookie.equals("0")) cookie = "";
        if (saveToPreferences) preferences.edit().putString(getSharedKey(PREF_KEY_PASS_COOKIE), cookie).commit();
        if (cookie.length() > 0) {
            usingPasscode = true;
            BasicClientCookie c1 = new BasicClientCookie("pass_id", cookie);
            c1.setDomain(".4chan.org");
            c1.setPath("/");
            httpClient.getCookieStore().addCookie(c1);
            BasicClientCookie c2 = new BasicClientCookie("pass_enabled", "1");
            c2.setDomain(".4chan.org");
            c2.setPath("/");
            httpClient.getCookieStore().addCookie(c2);
        } else {
            usingPasscode = false;
            BasicClientCookie c = new BasicClientCookie("pass_id", "0");
            c.setDomain(".4chan.org");
            c.setPath("/");
            httpClient.getCookieStore().addCookie(c);
            BasicClientCookie c2 = new BasicClientCookie("pass_enabled", "0");
            c2.setDomain(".4chan.org");
            c2.setPath("/");
            httpClient.getCookieStore().addCookie(c2);
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasscodePreference(preferenceGroup);
        
        CheckBoxPreference newRecaptchaPref = new LazyPreferences.CheckBoxPreference(context);
        newRecaptchaPref.setTitle(R.string.fourchan_prefs_new_recaptcha);
        newRecaptchaPref.setSummary(R.string.fourchan_prefs_new_recaptcha_summary);
        newRecaptchaPref.setKey(getSharedKey(PREF_KEY_NEW_RECAPTCHA));
        newRecaptchaPref.setDefaultValue(NEW_RECAPTCHA_DEFAULT);
        preferenceGroup.addPreference(newRecaptchaPref);
        
        final CheckBoxPreference fallbackRecaptchaPref = new LazyPreferences.CheckBoxPreference(context);
        fallbackRecaptchaPref.setTitle(R.string.fourchan_prefs_new_recaptcha_fallback);
        fallbackRecaptchaPref.setSummary(R.string.fourchan_prefs_new_recaptcha_fallback_summary);
        fallbackRecaptchaPref.setKey(getSharedKey(PREF_KEY_NEW_RECAPTCHA_FALLBACK));
        fallbackRecaptchaPref.setDefaultValue(false);
        preferenceGroup.addPreference(fallbackRecaptchaPref);
        fallbackRecaptchaPref.setDependency(getSharedKey(PREF_KEY_NEW_RECAPTCHA));
        
        addPasswordPreference(preferenceGroup);
        addHttpsPreference(preferenceGroup, true);
        addProxyPreferences(preferenceGroup);
        
        final CheckBoxPreference proxyPreference = (CheckBoxPreference) preferenceGroup.findPreference(getSharedKey(PREF_KEY_USE_PROXY));
        fallbackRecaptchaPref.setEnabled(!proxyPreference.isChecked());
        proxyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {            
            @Override
            public boolean onPreferenceClick(Preference preference) {
                fallbackRecaptchaPref.setEnabled(!proxyPreference.isChecked());
                if (proxyPreference.isChecked() && !fallbackRecaptchaPref.isChecked()) fallbackRecaptchaPref.setChecked(true);
                return false;
            }
        });
    }
    
    private boolean useHttps() {
        return useHttps(true);
    }
    
    private boolean useNewRecaptcha(boolean newThread) {
        return newThread || preferences.getBoolean(getSharedKey(PREF_KEY_NEW_RECAPTCHA), NEW_RECAPTCHA_DEFAULT);
    }
    
    private boolean newRecaptchaFallback() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_PROXY), false) ||
                preferences.getBoolean(getSharedKey(PREF_KEY_NEW_RECAPTCHA_FALLBACK), false);
    }
    
    @Override
    protected boolean cloudflareRecaptchaFallback() {
        return newRecaptchaFallback();
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        List<SimpleBoardModel> list = new ArrayList<SimpleBoardModel>();
        Map<String, BoardModel> newMap = new HashMap<String, BoardModel>();
        
        String url = (useHttps() ? "https://" : "http://") + "a.4cdn.org/boards.json";
        JSONObject boardsJson = downloadJSONObject(url, (oldBoardsList != null && boardsMap != null), listener, task);
        if (boardsJson == null) return oldBoardsList;
        JSONArray boards = boardsJson.getJSONArray("boards");
        
        for (int i=0, len=boards.length(); i<len; ++i) {
            BoardModel model = FourchanJsonMapper.mapBoardModel(boards.getJSONObject(i));
            newMap.put(model.boardName, model);
            list.add(new SimpleBoardModel(model));
        }
        
        boardsMap = newMap;
        return list.toArray(new SimpleBoardModel[list.size()]);
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap == null) {
            try {
                getBoardsList(listener, task, null);
            } catch (Exception e) {}
        }
        if (boardsMap != null && boardsMap.containsKey(shortName)) return boardsMap.get(shortName);
        return FourchanJsonMapper.getDefaultBoardModel(shortName);
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + "a.4cdn.org/" + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList; //if not modified
        List<ThreadModel> threads = new ArrayList<>();
        for (int i=0, len=response.length(); i<len; ++i) {
            JSONArray curArray = response.getJSONObject(i).getJSONArray("threads");
            for (int j=0, clen=curArray.length(); j<clen; ++j) {
                JSONObject curThreadJson = curArray.getJSONObject(j);
                ThreadModel curThread = new ThreadModel();
                curThread.threadNumber = Long.toString(curThreadJson.getLong("no"));
                curThread.postsCount = curThreadJson.optInt("replies", -2) + 1;
                curThread.attachmentsCount = curThreadJson.optInt("images", -2) + 1;
                curThread.isSticky = curThreadJson.optInt("sticky") == 1;
                curThread.isClosed = curThreadJson.optInt("closed") == 1;
                curThread.posts = new PostModel[] { FourchanJsonMapper.mapPostModel(curThreadJson, boardName) };
                threads.add(curThread);
            }
        }
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + "a.4cdn.org/" + boardName + "/" + Integer.toString(page) + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList; //if not modified
        JSONArray threads = response.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i=0, len=threads.length(); i<len; ++i) {
            JSONArray posts = threads.getJSONObject(i).getJSONArray("posts");
            JSONObject op = posts.getJSONObject(0);
            ThreadModel curThread = new ThreadModel();
            curThread.threadNumber = Long.toString(op.getLong("no"));
            curThread.postsCount = op.optInt("replies", -2) + 1;
            curThread.attachmentsCount = op.optInt("images", -2) + 1;
            curThread.isSticky = op.optInt("sticky") == 1;
            curThread.isClosed = op.optInt("closed") == 1;
            curThread.posts = new PostModel[posts.length()];
            for (int j=0, plen=posts.length(); j<plen; ++j) {
                curThread.posts[j] = FourchanJsonMapper.mapPostModel(posts.getJSONObject(j), boardName);
            }
            result[i] = curThread;
        }
        return result;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = (useHttps() ? "https://" : "http://") + "a.4cdn.org/" + boardName + "/thread/" + threadNumber + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList; //if not modified
        JSONArray posts = response.getJSONArray("posts");
        PostModel[] result = new PostModel[posts.length()];
        for (int i=0, len=posts.length(); i<len; ++i) {
            result[i] = FourchanJsonMapper.mapPostModel(posts.getJSONObject(i), boardName);
        }
        if (oldList != null) {
            result = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(result));
        }
        return result;
    }
    
    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        throw new Exception("Open this page in the browser");
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (usingPasscode) return null;
        if (useNewRecaptcha(threadNumber == null)) {
            recaptcha = null;
            return null;
        } else {
            recaptcha = Recaptcha.obtain(RECAPTCHA_KEY, task, httpClient, useHttps() ? "https" : "http");
            CaptchaModel result = new CaptchaModel();
            result.type = CaptchaModel.TYPE_NORMAL;
            result.bitmap = recaptcha.bitmap;
            return result;
        }
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String recaptcha2 = Recaptcha2solved.pop(RECAPTCHA_KEY);
        if (!usingPasscode) {
            if (useNewRecaptcha(model.threadNumber == null)) {
                if (recaptcha2 == null) throw Recaptcha2.obtain(
                        (useHttps() ? "https://" : "http://") + "4chan.org/", RECAPTCHA_KEY, null, CHAN_NAME, newRecaptchaFallback());
            } else if (recaptcha == null) throw new Exception("Invalid captcha");
        }
        String url = "https://sys.4chan.org/" + model.boardName + "/post";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : "").
                addString("sub", model.subject).
                addString("com", model.comment).
                addString("mode", "regist").
                addString("pwd", model.password);
        if (model.threadNumber != null) postEntityBuilder.addString("resto", model.threadNumber);
        if (!usingPasscode) {
            if (useNewRecaptcha(model.threadNumber == null)) {
                postEntityBuilder.addString("g-recaptcha-response", recaptcha2);
            } else {
                postEntityBuilder.addString("recaptcha_challenge_field", recaptcha.challenge).
                        addString("recaptcha_response_field", model.captchaAnswer);
            }
        }
        if (model.attachments != null && model.attachments.length != 0) postEntityBuilder.addFile("upfile", model.attachments[0]);
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        String response;
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            try {
                checkCloudflareError(e, "https://4chan.org");
            } catch (Exception cf) {
                if (recaptcha2 != null) Recaptcha2solved.push(RECAPTCHA_KEY, recaptcha2);
                throw cf;
            }
            throw e;
        }
        Matcher errorMatcher = ERROR_POSTING.matcher(response);
        if (errorMatcher.find()) {
            throw new Exception(Html.fromHtml(errorMatcher.group(1)).toString());
        }
        Matcher successMatcher = SUCCESS_POSTING.matcher(response);
        if (successMatcher.find()) {
            UrlPageModel redirect = new UrlPageModel();
            redirect.chanName = CHAN_NAME;
            redirect.type = UrlPageModel.TYPE_THREADPAGE;
            redirect.boardName = model.boardName;
            redirect.threadNumber = successMatcher.group(1);
            redirect.postNumber = successMatcher.group(2);
            if (redirect.threadNumber.equals("0")) redirect.threadNumber = redirect.postNumber;
            return buildUrl(redirect);
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = "https://sys.4chan.org/" + model.boardName + "/imgboard.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString(model.postNumber, "delete");
        if (model.onlyFiles) postEntityBuilder.addString("onlyimgdel", "on");
        postEntityBuilder.addString("mode", "usrdel").addString("pwd", model.password);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        Matcher errorMatcher = ERROR_POSTING.matcher(response);
        if (errorMatcher.find()) {
            throw new Exception(Html.fromHtml(errorMatcher.group(1)).toString());
        }
        return null;
    }
    
    @Override
    public String reportPost(final DeletePostModel model, ProgressListener listener, final CancellableTask task) throws Exception {
        if (reportCaptchaAnswer == null) {
            throw new SimpleCaptchaException() {
                private static final long serialVersionUID = 1L;
                @Override
                public String getServiceName() {
                    return "Recaptcha";
                }
                @Override
                protected Bitmap getNewCaptcha() throws Exception {
                    reportRecaptcha = Recaptcha.obtain(RECAPTCHA_KEY, task, httpClient, "https");
                    return reportRecaptcha.bitmap;
                }
                @Override
                protected void storeResponse(String response) {
                    reportCaptchaAnswer = response;
                }
            };
        } else {
            String url = "https://sys.4chan.org/" + model.boardName + "/imgboard.php?mode=report&no=" + model.postNumber;
            ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                    addString("cat", "vio").
                    addString("recaptcha_challenge_field", reportRecaptcha.challenge).
                    addString("recaptcha_response_field", reportCaptchaAnswer).
                    addString("board", model.boardName).
                    addString("no", model.postNumber);
            reportCaptchaAnswer = null;
            reportRecaptcha = null;
            HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
            String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
            if (response.contains("https://www.4chan.org/banned")) throw new Exception("You can't report posts because you are banned");
            if (response.contains("You seem to have mistyped the CAPTCHA")) throw new Exception("You seem to have mistyped the CAPTCHA");
            if (response.contains("That post doesn't exist anymore")) throw new Exception("That post doesn't exist anymore");
            if (response.contains("You forgot to solve the CAPTCHA")) throw new Exception("You forgot to solve the CAPTCHA");
            return null;
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w+")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(useHttps() ? "https://" : "http://");
        try {
            switch (model.type) {
                case UrlPageModel.TYPE_INDEXPAGE:
                    return url.append("www.4chan.org").toString();
                case UrlPageModel.TYPE_BOARDPAGE:
                    if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE || model.boardPage == 1)
                        return url.append("boards.4chan.org/").append(model.boardName).append('/').toString();
                    return url.append("boards.4chan.org/").append(model.boardName).append('/').append(model.boardPage).toString();
                case UrlPageModel.TYPE_CATALOGPAGE:
                    return url.append("boards.4chan.org/").append(model.boardName).append("/catalog").toString();
                case UrlPageModel.TYPE_THREADPAGE:
                    return url.append("boards.4chan.org/").append(model.boardName).append("/thread/").append(model.threadNumber).
                            append(model.postNumber == null || model.postNumber.length() == 0 ? "" : ("#p" + model.postNumber)).toString();
                case UrlPageModel.TYPE_SEARCHPAGE:
                    return url.append("boards.4chan.org/").append(model.boardName).append("/catalog#s=").
                            append(URLEncoder.encode(model.searchRequest, "UTF-8")).toString();
                case UrlPageModel.TYPE_OTHERPAGE:
                    return url.append(model.otherPath.startsWith("/") ? "boards.4chan.org" : "").append(model.otherPath).toString();
            }
        } catch (Exception e) {}
        throw new IllegalArgumentException("wrong page type");
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www\\.)?(.+)", Pattern.CASE_INSENSITIVE).matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        String urlPath = parseUrl.group(1);
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(urlPath);
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        
        if (domain.equals("4cdn.org") || domain.endsWith(".4cdn.org")) {
            UrlPageModel model = new UrlPageModel();
            model.chanName = CHAN_NAME;
            model.type = UrlPageModel.TYPE_OTHERPAGE;
            model.otherPath = urlPath;
            return model;
        }
        
        if (!domain.equals("4chan.org") && !domain.endsWith(".4chan.org")) throw new IllegalArgumentException("wrong chan");
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        
        if (path.length() == 0) {
            model.type = UrlPageModel.TYPE_INDEXPAGE;
            return model;
        }
        
        Matcher threadPage = Pattern.compile("([^/]+)/thread/(\\d+)[^#]*(?:#p(\\d+))?").matcher(path);
        if (threadPage.find()) {
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.boardName = threadPage.group(1);
            model.threadNumber = threadPage.group(2);
            model.postNumber = threadPage.group(3);
            return model;
        }
        
        Matcher pageCatalogSearch = Pattern.compile("([^/]+)/catalog(?:#s=(.+))?").matcher(path);
        if (pageCatalogSearch.find()) {
            model.boardName = pageCatalogSearch.group(1);
            String search = pageCatalogSearch.group(2);
            if (search != null) {
                model.type = UrlPageModel.TYPE_SEARCHPAGE;
                model.searchRequest = search;
                try {
                    model.searchRequest = URLDecoder.decode(model.searchRequest, "UTF-8");
                } catch (Exception e) {}
            } else {
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                model.catalogType = 0;
            }
            return model;
        }
        
        Matcher boardPage = Pattern.compile("([^/]+)(?:/(\\d+)?)?").matcher(path);
        if (boardPage.find()) {
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            model.boardName = boardPage.group(1);
            String page = boardPage.group(2);
            model.boardPage = page == null ? 1 : Integer.parseInt(page);
            return model;
        }
        
        throw new IllegalArgumentException("fail to parse");
    }
    
}
