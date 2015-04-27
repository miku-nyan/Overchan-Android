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

package nya.miku.wishmaster.chans.fourchan;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.http.SslError;
import android.os.Build;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.Html;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import nya.miku.wishmaster.R;
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
import nya.miku.wishmaster.chans.AbstractChanModule;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.InteractiveException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class FourchanModule extends AbstractChanModule {
    
    static final String CHAN_NAME = "4chan.org";
    
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_NEW_RECAPTCHA = "PREF_KEY_NEW_RECAPTCHA";
    
    private static final String RECAPTCHA_KEY = "6Ldp2bsSAAAAAAJ5uyx_lx34lJeEpTLVkP5k04qc";
    
    private Map<String, BoardModel> boardsMap = null;
    
    private Recaptcha2 recaptcha = null;
    private String recaptcha2 = null;
    
    private static final Pattern ERROR_POSTING = Pattern.compile("<span id=\"errmsg\"(?:[^>]*)>(.*?)(?:</span>|<br)");
    private static final Pattern SUCCESS_POSTING = Pattern.compile("<!-- thread:(\\d+),no:(?:\\d+) -->");
    
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
    
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return object;
    }
    
    private JSONArray downloadJSONArray(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONArray array = HttpStreamer.getInstance().getJSONArrayFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return array;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final CheckBoxPreference newRecaptchaPref = new CheckBoxPreference(context);
            newRecaptchaPref.setTitle(R.string.fourchan_prefs_new_recaptcha);
            newRecaptchaPref.setSummary(R.string.fourchan_prefs_new_recaptcha_summary);
            newRecaptchaPref.setKey(getSharedKey(PREF_KEY_NEW_RECAPTCHA));
            newRecaptchaPref.setDefaultValue(false);
            preferenceGroup.addPreference(newRecaptchaPref);
        }
        addPasswordPreference(preferenceGroup);
        CheckBoxPreference httpsPref = new CheckBoxPreference(context);
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(true);
        preferenceGroup.addPreference(httpsPref);
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        addProxyPreferences(preferenceGroup);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            final CheckBoxPreference proxyPreference = (CheckBoxPreference) preferenceGroup.findPreference(getSharedKey(PREF_KEY_USE_PROXY));
            final Preference newRecaptchaPref = preferenceGroup.findPreference(getSharedKey(PREF_KEY_NEW_RECAPTCHA));
            newRecaptchaPref.setEnabled(!proxyPreference.isChecked());
            proxyPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {            
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    newRecaptchaPref.setEnabled(!proxyPreference.isChecked());
                    return false;
                }
            });
        }
    }
    
    private boolean useHttps() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true);
    }
    
    private boolean useNewRecaptcha() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB &&
                !preferences.getBoolean(getSharedKey(PREF_KEY_USE_PROXY), false) &&
                preferences.getBoolean(getSharedKey(PREF_KEY_NEW_RECAPTCHA), false);
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
        if (useNewRecaptcha()) {
            recaptcha = null;
            return null;
        } else {
            recaptcha = Recaptcha2.obtain(RECAPTCHA_KEY, task, httpClient, useHttps() ? "https" : "http");
            CaptchaModel result = new CaptchaModel();
            result.type = CaptchaModel.TYPE_NORMAL;
            result.bitmap = recaptcha.bitmap;
            return result;
        }
    }
    
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static class Recaptcha2Exception extends InteractiveException {
        private static final long serialVersionUID = 1L;
        
        private static final String INTERCEPT = "_intercept?";
        
        private static final String RECAPTCHA_HTML =
                "<script type=\"text/javascript\">" +
                    "window.globalOnCaptchaEntered = function(res) { " +
                        "location.href = \"" + INTERCEPT + "\" + res; " +
                    "}" +
                "</script>" +
                "<script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>" +
                "<div class=\"g-recaptcha\" data-sitekey=\"" + RECAPTCHA_KEY + "\" data-callback=\"globalOnCaptchaEntered\"></div>";
        
        @Override
        public String getServiceName() {
            return "Recaptcha";
        }
        
        @Override
        public void handle(final Activity activity, final CancellableTask task, final Callback callback) {
            if (task.isCancelled()) return;
            activity.runOnUiThread(new Runnable() {
                @SuppressLint("SetJavaScriptEnabled")
                @Override
                public void run() {
                    final Dialog dialog = new Dialog(activity);
                    WebView webView = new WebView(activity);
                    webView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    webView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                            handler.proceed();
                        }
                        @SuppressWarnings("deprecation")
                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                            System.out.println(url);
                            if (url.contains(INTERCEPT)) {
                                String hash = url.substring(url.indexOf(INTERCEPT) + INTERCEPT.length());
                                ((FourchanModule) MainApplication.getInstance().getChanModule(CHAN_NAME)).recaptcha2 = hash;
                                activity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess();
                                        dialog.dismiss();
                                    }
                                });
                            }
                            return super.shouldInterceptRequest(view, url);
                        }
                    });
                    //webView.getSettings().setUserAgentString(HttpConstants.USER_AGENT_STRING);
                    webView.getSettings().setUserAgentString("Mozilla/5.0"); //should get easier captcha
                    webView.getSettings().setJavaScriptEnabled(true);
                    dialog.setTitle("Recaptcha");
                    dialog.setContentView(webView);
                    dialog.setCanceledOnTouchOutside(false);
                    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            callback.onError("Cancelled");
                        }
                    });
                    dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    dialog.show();
                    webView.loadDataWithBaseURL("https://127.0.0.1/", RECAPTCHA_HTML, "text/html", "UTF-8", null);
                }
            });
        }
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        if (useNewRecaptcha()) {
            if (recaptcha2 == null) throw new Recaptcha2Exception();
        } else if (recaptcha == null) throw new Exception("Invalid captcha");
        String url = (useHttps() ? "https://" : "http://") + "sys.4chan.org/" + model.boardName + "/post";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : "").
                addString("sub", model.subject).
                addString("com", model.comment).
                addString("mode", "regist").
                addString("pwd", model.password);
        if (model.threadNumber != null) postEntityBuilder.addString("resto", model.threadNumber);
        postEntityBuilder.addString("g-recaptcha-response", useNewRecaptcha() ? recaptcha2 : recaptcha.checkCaptcha(model.captchaAnswer, task));
        recaptcha2 = null;
        if (model.attachments != null && model.attachments.length != 0) postEntityBuilder.addFile("upfile", model.attachments[0]);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
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
            return buildUrl(redirect);
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = (useHttps() ? "https://" : "http://") + "sys.4chan.org/" + model.boardName + "/imgboard.php";
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
