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

package nya.miku.wishmaster.chans.krautchan;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
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
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class KrautModule extends CloudflareChanModule {
    private static final String TAG = "KrautModule";
    
    static final String CHAN_NAME = "krautchan.net";
    private static final String CHAN_DOMAIN = "krautchan.net";
    
    private static final String PREF_KEY_KOMPTURCODE_COOKIE = "PREF_KEY_KOMPTURCODE_COOKIE";
    
    private static final String KOMTURCODE_COOKIE_NAME = "desuchan.komturcode";
    
    private static final Pattern THREADPAGE_PATTERN = Pattern.compile("([^/]+)/thread-(\\d+)\\.html[^#]*(?:#(\\d+))?");
    private static final Pattern CATALOGPAGE_PATTERN = Pattern.compile("catalog/(\\w+)");
    private static final Pattern BOARDPAGE_PATTERN = Pattern.compile("([^/]+)(?:/(\\d+)\\.html?)?");
    
    private Map<String, BoardModel> boardsMap = null;
    private String lastCaptchaId = null;
    
    public KrautModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Krautchan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_krautchan, null);
    }
    
    @Override
    protected void initHttpClient() {
        super.initHttpClient();
        setKompturcodeCookie(preferences.getString(getSharedKey(PREF_KEY_KOMPTURCODE_COOKIE), null));
    }
    
    @Override
    protected String getCloudflareCookieDomain() {
        return CHAN_DOMAIN;
    }
    
    private void setKompturcodeCookie(String kompturcodeCookie) {
        if (kompturcodeCookie != null && kompturcodeCookie.length() > 0) {
            BasicClientCookie c = new BasicClientCookie(KOMTURCODE_COOKIE_NAME, kompturcodeCookie);
            c.setDomain(CHAN_DOMAIN);
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    public void addKompturcodePreference(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        EditTextPreference kompturcodePreference = new EditTextPreference(context);
        kompturcodePreference.setTitle(R.string.kraut_prefs_kompturcode);
        kompturcodePreference.setDialogTitle(R.string.kraut_prefs_kompturcode);
        kompturcodePreference.setSummary(R.string.kraut_prefs_kompturcode_summary);
        kompturcodePreference.setKey(getSharedKey(PREF_KEY_KOMPTURCODE_COOKIE));
        kompturcodePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                setKompturcodeCookie((String) newValue);
                return true;
            }
        });
        preferenceGroup.addPreference(kompturcodePreference);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addKompturcodePreference(preferenceGroup);
        addPasswordPreference(preferenceGroup);
        addHttpsPreference(preferenceGroup, true);
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }
    
    private boolean useHttps() {
        return useHttps(true);
    }
    
    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + CHAN_DOMAIN + "/";
    }
    
    /**
     * If (url == null) returns boards list (SimpleBoardModel[]), thread/threads page (ThreadModel[]) otherwise
     */
    private Object readPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified) throws Exception {
        boolean boardsList = url == null;
        if (boardsList) url = getUsingUrl() + "nav";
        boolean catalog = boardsList ? false : url.contains("/catalog/");
        
        HttpResponseModel responseModel = null;
        Closeable in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = boardsList ? new KrautBoardsListReader(responseModel.stream) :
                    (catalog ? new KrautCatalogReader(responseModel.stream) : new KrautReader(responseModel.stream));
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return boardsList ? ((KrautBoardsListReader) in).readBoardsList() :
                    (catalog ? ((KrautCatalogReader) in).readPage() : ((KrautReader) in).readPage());
            } else {
                if (responseModel.notModified()) return null;
                byte[] html = null;
                try {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream(1024);
                    IOUtils.copyStream(responseModel.stream, byteStream);
                    html = byteStream.toByteArray();
                } catch (Exception e) {}
                if (html != null) {
                    checkCloudflareError(new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusReason, html), url);
                }
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            if (responseModel != null) HttpStreamer.getInstance().removeFromModifiedMap(url);
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        SimpleBoardModel[] boardsList = (SimpleBoardModel[]) readPage(null, listener, task, oldBoardsList != null);
        if (boardsList == null) return oldBoardsList;
        Map<String, BoardModel> newMap = new HashMap<>();
        for (SimpleBoardModel board : boardsList) {
            newMap.put(board.boardName, KrautBoardsListReader.getDefaultBoardModel(board.boardName, board.boardDescription, board.boardCategory));
        }
        boardsMap = newMap;
        return boardsList;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap == null) {
            try {
                getBoardsList(listener, task, null);
            } catch (Exception e) {
                Logger.e(TAG, "cannot get boards list", e);
            }
        }
        if (boardsMap != null && boardsMap.containsKey(shortName)) return boardsMap.get(shortName);
        return KrautBoardsListReader.getDefaultBoardModel(shortName, shortName, null);
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = (ThreadModel[]) readPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_CATALOGPAGE;
        urlModel.boardName = boardName;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = (ThreadModel[]) readPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = boardName;
        urlModel.threadNumber = threadNumber;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = (ThreadModel[]) readPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "ajax/checkpost?board=" + boardName;
        try {
            JSONObject data = HttpStreamer.getInstance().
                    getJSONObjectFromUrl(url, HttpRequestModel.DEFAULT_GET, httpClient, listener, task, true).
                    getJSONObject("data");
            if (data.optString("captchas", "").equals("always")) {
                StringBuilder captchaUrlBuilder = new StringBuilder(getUsingUrl());
                captchaUrlBuilder.append("captcha?id=");
                StringBuilder captchaIdBuilder = new StringBuilder();
                captchaIdBuilder.append(boardName);
                if (threadNumber != null) captchaIdBuilder.append(threadNumber);
                for (Cookie cookie : httpClient.getCookieStore().getCookies()) {
                    if (cookie.getName().equalsIgnoreCase("desuchan.session")) {
                        captchaIdBuilder.append('-').append(cookie.getValue());
                        break;
                    }
                }
                captchaIdBuilder.
                        append('-').append(new Date().getTime()).
                        append('-').append(Math.round(100000000 * Math.random()));
                String captchaId = captchaIdBuilder.toString();
                captchaUrlBuilder.append(captchaId);
                String captchaUrl = captchaUrlBuilder.toString();
                CaptchaModel captchaModel = downloadCaptcha(captchaUrl, listener, task);
                lastCaptchaId = captchaId;
                return captchaModel;
                
            }
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
        } catch (Exception e) {
            Logger.e(TAG, "exception while getting captcha", e);
        }
        return null;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "post";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("internal_n", model.name).
                addString("internal_s", model.subject);
        if (model.sage) postEntityBuilder.addString("sage", "1");
        postEntityBuilder.
                addString("internal_t", model.comment);
        
        if (lastCaptchaId != null) {
            postEntityBuilder.
                    addString("captcha_name", lastCaptchaId).
                    addString("captcha_secret", model.captchaAnswer);
            lastCaptchaId = null;
        }
        
        if (model.attachments != null) {
            String[] images = new String[] { "file_0", "file_1", "file_2", "file_3" };
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile(images[i], model.attachments[i], model.randomHash);
            }
        }
        
        postEntityBuilder.
                addString("forward", "thread").
                addString("password", model.password).
                addString("board", model.boardName);
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        
        
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        String location = header.getValue();
                        if (location.contains("banned")) throw new Exception("You are banned");
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                int messageErrorPos = htmlResponse.indexOf("class=\"message_error");
                if (messageErrorPos == -1) return null; //assume success
                int p2 = htmlResponse.indexOf('>', messageErrorPos);
                if (p2 != -1) {
                    String errorMessage = htmlResponse.substring(p2 + 1);
                    int p3 = errorMessage.indexOf("</tr>");
                    if (p3 != -1) errorMessage = errorMessage.substring(0, p3);
                    errorMessage = RegexUtils.trimToSpace(StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(errorMessage)).trim());
                    throw new Exception(errorMessage);
                }
            }
            
            throw new HttpWrongStatusCodeException(response.statusCode, response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "delete";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("post_" + model.postNumber, "delete"));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("board", model.boardName));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        String location = header.getValue();
                        if (location.contains("banned")) throw new Exception("You are banned");
                        break;
                    }
                }
                return null;
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                int messageNoticePos = htmlResponse.indexOf("class=\"message_notice");
                if (messageNoticePos == -1) return null;
                int p2 = htmlResponse.indexOf('>', messageNoticePos);
                if (p2 != -1) {
                    String errorMessage = htmlResponse.substring(p2 + 1);
                    int p3 = errorMessage.indexOf("</tr>");
                    if (p3 != -1) errorMessage = errorMessage.substring(0, p3);
                    errorMessage = RegexUtils.trimToSpace(StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(errorMessage)).trim());
                    throw new Exception(errorMessage);
                }
            }
            
            throw new HttpWrongStatusCodeException(response.statusCode, response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w*")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(getUsingUrl());
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                return url.toString();
            case UrlPageModel.TYPE_BOARDPAGE:
                return url.append(model.boardName).append('/').append(model.boardPage != UrlPageModel.DEFAULT_FIRST_PAGE && model.boardPage > 1 ?
                        (String.valueOf(model.boardPage - 1) + ".html") : "").toString();
            case UrlPageModel.TYPE_THREADPAGE:
                return url.append(model.boardName).append("/thread-").append(model.threadNumber).append(".html").
                        append(model.postNumber == null || model.postNumber.length() == 0 ? "" : ("#" + model.postNumber)).toString();
            case UrlPageModel.TYPE_CATALOGPAGE:
                return url.append("catalog/").append(model.boardName).toString();
            case UrlPageModel.TYPE_OTHERPAGE:
                return url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath).toString();
        }
        throw new IllegalArgumentException("wrong page type");
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath = UrlPathUtils.getUrlPath(url, CHAN_DOMAIN);
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        urlPath = urlPath.toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        
        if (urlPath.length() == 0) {
            model.type = UrlPageModel.TYPE_INDEXPAGE;
            return model;
        }
        
        Matcher threadPage = THREADPAGE_PATTERN.matcher(urlPath);
        if (threadPage.find()) {
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.boardName = threadPage.group(1);
            model.threadNumber = threadPage.group(2);
            model.postNumber = threadPage.group(3);
            return model;
        }
        
        Matcher catalogPage = CATALOGPAGE_PATTERN.matcher(urlPath);
        if (catalogPage.find()) {
            model.boardName = catalogPage.group(1);
            model.type = UrlPageModel.TYPE_CATALOGPAGE;
            model.catalogType = 0;
            return model;
        }
        
        Matcher boardPage = BOARDPAGE_PATTERN.matcher(urlPath);
        if (boardPage.find()) {
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            model.boardName = boardPage.group(1);
            String page = boardPage.group(2);
            model.boardPage = page == null ? 1 : (Integer.parseInt(page) + 1);
            return model;
        }
        
        throw new IllegalArgumentException("fail to parse");
    }

}
