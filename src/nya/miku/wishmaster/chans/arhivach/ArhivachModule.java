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

package nya.miku.wishmaster.chans.arhivach;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.CloudflareChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 23.06.2015.
 */

public class ArhivachModule extends CloudflareChanModule {
    //private static final String TAG = "ArhivachModule";

    private static final Pattern INDEX_PAGE_PATTERN = Pattern.compile("index/(\\d+)/?(.*)");
    static final String CHAN_NAME = "Arhivach.org";
    private static final String DEFAULT_DOMAIN = "arhivach.org";
    private static final String ONION_DOMAIN = "arhivachovtj2jrp.onion";
    private static final String[] DOMAINS = new String[] { DEFAULT_DOMAIN, ONION_DOMAIN };

    private static final String PREF_KEY_USE_ONION = "PREF_KEY_USE_ONION";

    public ArhivachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return "Архивач";
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_arhivach, null);
    }

    private String getUsingDomain() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) ? ONION_DOMAIN : DEFAULT_DOMAIN;
    }

    @Override
    protected String getCloudflareCookieDomain() {
        return DEFAULT_DOMAIN;
    }

    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + getUsingDomain() + "/";
    }

    private boolean useHttps() {
        return !preferences.getBoolean(getSharedKey(PREF_KEY_USE_ONION), false) && useHttps(false);
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference httpsPref = addHttpsPreference(preferenceGroup, false);
        CheckBoxPreference onionPref = new LazyPreferences.CheckBoxPreference(context);
        onionPref.setTitle(R.string.pref_use_onion);
        onionPref.setSummary(R.string.pref_use_onion_summary);
        onionPref.setKey(getSharedKey(PREF_KEY_USE_ONION));
        onionPref.setDefaultValue(false);
        onionPref.setDisableDependentsState(true);
        preferenceGroup.addPreference(onionPref);
        httpsPref.setDependency(getSharedKey(PREF_KEY_USE_ONION));
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }

    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return ArhivachBoards.getBoardsList();
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return ArhivachBoards.getBoard(shortName);
    }

    private ThreadModel[] readBoardPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified, boolean isThread)
            throws Exception {
        HttpResponseModel responseModel = null;
        Closeable in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = isThread ? new ArhivachThreadReader(responseModel.stream) : new ArhivachBoardReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return isThread ? ((ArhivachThreadReader) in).readPage() : ((ArhivachBoardReader) in).readPage() ;
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
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);

        ThreadModel[] threads = readBoardPage(url, listener, task, oldList != null, false);
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

        ThreadModel[] threads = readBoardPage(url, listener, task, oldList != null, true);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }

    private JSONArray tagComplete(String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        //TODO: Refactor tagComplete
        HttpResponseModel responseModel = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().build();
        StringBuilder url = new StringBuilder(getUsingUrl());
        StringBuilder callback = new StringBuilder();
        callback.append("Overchan");
        callback.append(Math.abs((new Random()).nextLong()));
        callback.append("_");
        callback.append(System.currentTimeMillis());
        url.append("ajax/?callback=");
        url.append(callback);
        url.append("&act=tagcomplete");
        url.append("&create=0");
        url.append("&nobrackets=0");
        url.append("&only_board=0");
        url.append("&q=");
        url.append(searchRequest);
        url.append("&_=");
        url.append(System.currentTimeMillis());
        BufferedReader in = null;
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url.toString(), rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                if (responseModel.stream == null) throw new HttpRequestException(new NullPointerException());
                in = new BufferedReader(new InputStreamReader(responseModel.stream));
                final int bufferSize = 1024;
                final char[] buffer = new char[bufferSize];
                final StringBuilder out = new StringBuilder();
                while (true) {
                    if (task != null && task.isCancelled()) throw new InterruptedException();
                    int rsz = in.read(buffer, 0, buffer.length);
                    if (rsz < 0)
                        break;
                    out.append(buffer, 0, rsz);
                }

                String tags = out.toString();
                tags = tags.replace(callback.toString(), "");
                tags = tags.substring(tags.indexOf("(")+1, tags.lastIndexOf(")"));
                JSONObject o = new JSONObject(tags);
                return o.getJSONArray("tags");
            } else {
                throw new HttpWrongStatusCodeException(responseModel.statusCode, responseModel.statusCode + " - " + responseModel.statusReason);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            if (responseModel != null) responseModel.release();
        }
    }

    @Override
    public PostModel[] search(String boardName, String searchRequest, int page, ProgressListener listener, CancellableTask task) throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_SEARCHPAGE;
        urlModel.boardName = boardName;
        urlModel.searchRequest = searchRequest;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);
        JSONArray tags = tagComplete(searchRequest, listener, task);
        try {
            url = url + tags.getJSONObject(0).getInt("id");
        } catch (Exception e) {
            return new PostModel[0];
        }
        ThreadModel[] threads = readBoardPage(url, listener, task, false, false);
        List<PostModel> posts = new ArrayList<PostModel>();
        for (ThreadModel thread : threads){
            posts.add(thread.posts[0]);
        }
        return posts.toArray(new PostModel[posts.size()]);
    }

    @Override
    public PostModel[] search(String boardName, String searchRequest, ProgressListener listener, CancellableTask task) throws Exception {
        return search(boardName, searchRequest, 0, listener, task);
    }

    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        StringBuilder url = new StringBuilder(getUsingUrl());
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                url.append("index").append("/");
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("index").append("/");
                if (model.boardPage > 1) url.append((model.boardPage - 1) * 25).append("/");
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("thread/").append(model.threadNumber).append("/");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            case UrlPageModel.TYPE_SEARCHPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("index").append("/");
                if (model.boardPage > 1) url.append((model.boardPage - 1) * 25).append("/");
                url.append("?q=" + model.searchRequest + "&tags=");
                break;
            default:
                throw new IllegalArgumentException("wrong page type");
        }
        return url.toString();
    }

    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath  = UrlPathUtils.getUrlPath(url, DOMAINS);
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        urlPath = urlPath.toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        try {
            if (urlPath.contains("thread/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("thread/(\\d+)/?(.*)").matcher(urlPath);
                if (!matcher.find()) throw new IllegalArgumentException("wrong thread number");
                model.boardName = "";
                model.threadNumber = matcher.group(1);
                if (matcher.group(2).startsWith("#")) {
                    String post = matcher.group(2).substring(1);
                    if (!post.equals(""))
                        model.postNumber = post;
                }
            } else if (urlPath.contains("tags=")) {
                //TODO: implement search request parser
                model.type = UrlPageModel.TYPE_SEARCHPAGE;
                model.boardName = "";
                Matcher matcher = INDEX_PAGE_PATTERN.matcher(urlPath);
                String page = "";
                if (matcher.find())
                    page = matcher.group(1);
                if (!page.equals(""))
                    model.boardPage = (Integer.parseInt(page) / 25) + 1;
                else
                    model.boardPage = 1;
                matcher = Pattern.compile("q=([^&]+)&?").matcher(urlPath.substring(urlPath.indexOf("q=")-1));
                if (matcher.find()) {
                    model.searchRequest = matcher.group(1);
                } else {
                    model.searchRequest = "";
                }
            } else if ((urlPath.contains("index") || urlPath.indexOf("/")==0 || urlPath.length()==0) && !urlPath.contains("tags=")) {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                model.boardName = "";
                Matcher matcher = INDEX_PAGE_PATTERN.matcher(urlPath);
                String page = "";
                if (matcher.find())
                    page = matcher.group(1);
                if (!page.equals(""))
                    model.boardPage = (Integer.parseInt(page) / 25) + 1;
                else
                    model.boardPage = 1;
            }
        } catch (Exception e) {
            model.type = UrlPageModel.TYPE_OTHERPAGE;
            model.otherPath = urlPath;
        }

        return model;
    }
}
