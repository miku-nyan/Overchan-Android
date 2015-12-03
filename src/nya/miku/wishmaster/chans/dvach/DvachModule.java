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

package nya.miku.wishmaster.chans.dvach;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
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
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

@SuppressWarnings("deprecation") //https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class DvachModule extends AbstractWakabaModule {
    private static final String TAG = "DvachModule";
    
    static final String CHAN_NAME = "2-chru.net";
    private static final String DEFAULT_DOMAIN = "2-chru.net";
    private static final String ONION_DOMAIN = "dmirrgetyojz735v.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN, "mirror.2-chru.net", "bypass.2-chru.net", "2chru.net",
            "2chru.cafe", "2-chru.cafe" };
    private static final String[] FORMATS = new String[] { "jpg", "jpeg", "png", "gif", "webm", "mp4", "ogv", "mp3", "ogg" };
    
    private static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2>(.*?)</h2>", Pattern.DOTALL);
    private static final Pattern REDIRECT_PATTERN = Pattern.compile("url=res/(\\d+)\\.html");
    
    private final Handler handler;
    
    public DvachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
        handler = new Handler();
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Два.ч (2-chru.net)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dvach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        if (preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false)) return ONION_DOMAIN;
        String domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        return TextUtils.isEmpty(domain) ? DEFAULT_DOMAIN : domain;
    }
    
    @Override
    protected String[] getAllDomains() {
        String domain = getUsingDomain();
        for (String d : DOMAINS) if (domain.equals(d)) return DOMAINS;
        String[] domains = new String[DOMAINS.length + 1];
        for (int i=0; i<DOMAINS.length; ++i) domains[i] = DOMAINS[i];
        domains[DOMAINS.length] = domain;
        return domains;
    }
    
    @Override
    protected boolean useHttps() {
        return !preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        addPasswordPreference(preferenceGroup);
        addUnsafeSslPreference(preferenceGroup, null);
        CheckBoxPreference onionPref = new CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.dvach_prefs_domain);
        domainPref.setDialogTitle(R.string.dvach_prefs_domain);
        domainPref.setSummary(R.string.dvach_prefs_domain_summary);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        preferenceGroup.addPreference(domainPref);
        domainPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addProxyPreferences(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "menu.html";
        HttpResponseModel responseModel = null;
        DvachBoardsListReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(oldBoardsList != null).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new DvachBoardsListReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readBoardsList();
            } else {
                if (responseModel.notModified()) return null;
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw rethrow(e);
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    protected Map<String, SimpleBoardModel> getBoardsMap(ProgressListener listener, CancellableTask task) throws Exception {
        try {
            return super.getBoardsMap(listener, task);
        } catch (Exception e) {
            Logger.e(TAG, e);
            return Collections.emptyMap();
        }
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.defaultUserName = "Аноним";
        board.timeZoneId = "GMT+3";
        board.searchAllowed = true;
        
        board.readonlyBoard = false;
        board.requiredFileForNewThread = !shortName.equals("d");
        board.allowDeletePosts = true;
        board.allowDeleteFiles = false;
        board.allowNames = !shortName.equals("b");
        board.allowSubjects = true;
        board.allowSage = false;
        board.allowEmails = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = shortName.equals("d") ? 0 : 1;
        board.attachmentsFormatFilters = FORMATS;
        board.markType = BoardModel.MARK_WAKABAMARK;
        
        return board;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new DvachReader(stream);
    }
    
    @Override
    protected ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkModified, UrlPageModel urlModel)
            throws Exception {
        try {
            return super.readWakabaPage(url, listener, task, checkModified, urlModel);
        } catch (Exception e) {
            throw rethrow(e);
        }
    }
    
    private Exception rethrow(Exception e) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 &&
                e instanceof HttpRequestException &&
                ((HttpRequestException) e).isSslException() &&
                preferences.getBoolean(getSharedKey(PREF_KEY_UNSAFE_SSL), false))
            return new Exception("SSL Internal Error\nTry to use Tor/.onion domain");
        return e;
    }
    
    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + boardName + "/search?q=" + URLEncoder.encode(searchRequest, "UTF-8");
        HttpResponseModel responseModel = null;
        DvachSearchReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new DvachSearchReader(responseModel.stream, this);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readSerachPage();
            } else {
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            throw rethrow(e);
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            String checkUrl = getUsingUrl() + boardName + "/api/requires-captcha";
            if (HttpStreamer.getInstance().
                    getJSONObjectFromUrl(checkUrl, HttpRequestModel.builder().setGET().build(), httpClient, listener, task, false).
                    getString("requires-captcha").equals("0")) return null;
        } catch (Exception e) {
            Logger.e(TAG, "captcha", e);
        }
        String captchaUrl = getUsingUrl() + boardName + "/captcha?" + String.valueOf(Math.floor(Math.random() * 10000000));
        Bitmap captchaBitmap = null;
        HttpRequestModel requestModel = HttpRequestModel.builder().setGET().build();
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
        try {
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
        }
        CaptchaModel captchaModel = new CaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        return captchaModel;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/post";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("parent", model.threadNumber != null ? model.threadNumber : "0").
                addString("name", model.name).
                addString("email", model.email).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("captcha", TextUtils.isEmpty(model.captchaAnswer) ? "" : model.captchaAnswer).
                addString("password", model.password);
        if (model.threadNumber != null) postEntityBuilder.addString("noko", "on");
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        
        try {
            cssTest(model.boardName, task);
        } catch (Exception e) {
            Logger.e(TAG, "csstest failed", e);
        }
        
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("ОБНОВЛ")) {
                    if (model.threadNumber == null) {
                        Matcher redirectMatcher = REDIRECT_PATTERN.matcher(htmlResponse);
                        if (redirectMatcher.find()) {
                            UrlPageModel redirModel = new UrlPageModel();
                            redirModel.chanName = CHAN_NAME;
                            redirModel.type = UrlPageModel.TYPE_THREADPAGE;
                            redirModel.boardName = model.boardName;
                            redirModel.threadNumber = redirectMatcher.group(1);
                            return buildUrl(redirModel);
                        }
                    }
                    return null;
                }
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) {
                    throw new Exception(errorMatcher.group(1));
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/delete";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("posts[]", model.postNumber));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("deletepost", "Удалить"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Неверный пароль")) throw new Exception("Неверный пароль");
        return null;
    }
    
    private void cssTest(String boardName, final CancellableTask task) throws Exception { /* =*.*= */
        class CSSCodeHolder {
            private volatile String cssCode = null;
            public synchronized void setCode(String code) {
                Logger.d(TAG, "set CSS code: " + code);
                if (cssCode == null) cssCode = code;
            }
            public boolean isSet() {
                return cssCode != null;
            }
            public String getCode() {
                return cssCode;
            }
        }
        class WebViewHolder {
            private WebView webView = null;
        }
        
        final CSSCodeHolder holder = new CSSCodeHolder();
        final WebViewHolder wv = new WebViewHolder();
        final String cssTest = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + boardName + "/csstest.foo",
                HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
        long startTime = System.currentTimeMillis();
        
        handler.post(new Runnable() {
            @Override
            public void run() {
                wv.webView = new WebView(MainApplication.getInstance());
                wv.webView.setWebViewClient(new WebViewClient(){
                    @Override
                    public void onLoadResource(WebView view, String url) {
                        if (url.contains("?code=") && !task.isCancelled()) {
                            holder.setCode(url.substring(url.indexOf("?code=") + 6));
                        }
                    }
                });
                wv.webView.loadDataWithBaseURL("http://127.0.0.1/csstest.foo", cssTest, "text/html", "UTF-8", "");
            }
        });
        
        while (!holder.isSet()) {
            long time = System.currentTimeMillis() - startTime;
            if ((task != null && task.isCancelled()) || time > 5000) break;
            Thread.yield();
        }
        
        handler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    wv.webView.stopLoading();
                    wv.webView.clearCache(true);
                    wv.webView.destroy();
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
        });
        
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        String cssCode = holder.getCode();
        if (cssCode != null) {
            HttpStreamer.getInstance().getBytesFromUrl(getUsingUrl() + boardName + "/csstest.foo?code=" + cssCode,
                    HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        try {
            if (model.type == UrlPageModel.TYPE_SEARCHPAGE)
                return getUsingUrl() + model.boardName + "/search?q=" + URLEncoder.encode(model.searchRequest, "UTF-8");
        } catch (Exception e) {}
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        if (url.contains("/search?q=")) {
            try {
                RegexUtils.getUrlPath(url, getAllDomains());
                int index = url.indexOf("/search?q=");
                String left = url.substring(0, index);
                UrlPageModel model = new UrlPageModel();
                model.chanName = CHAN_NAME;
                model.type = UrlPageModel.TYPE_SEARCHPAGE;
                model.boardName = left.substring(left.lastIndexOf('/') + 1);
                model.searchRequest = url.substring(index + 10);
                model.searchRequest = URLDecoder.decode(model.searchRequest, "UTF-8");
                return model;
            } catch (Exception e) {}
        }
        return WakabaUtils.parseUrl(url, getChanName(), getAllDomains());
    }
}
