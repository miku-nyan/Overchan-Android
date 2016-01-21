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

package nya.miku.wishmaster.chans.horochan;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.StatusLine;
import cz.msebera.android.httpclient.client.methods.HttpUriRequest;
import cz.msebera.android.httpclient.client.methods.RequestBuilder;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.cookie.BasicClientCookie;
import cz.msebera.android.httpclient.util.EntityUtils;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
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
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2js;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import nya.miku.wishmaster.lib.org_json.JSONTokener;

public class HorochanModule extends AbstractChanModule {
    private static final String CHAN_NAME = "horochan.ru";
    private static final String DOMAIN = "horochan.ru";
    
    private static final String RECAPTCHA_PUBLIC_KEY = "6LerWhMTAAAAABCXYL2CEv-YyPeM5WbUTx3CknKD";
    
    private static final Pattern URL_PATH_BOARDPAGE_PATTERN = Pattern.compile("([^/]+)(?:/(\\d+)?)?");
    private static final Pattern URL_PATH_THREADPAGE_PATTERN = Pattern.compile("([^/]+)/thread/(\\d+)");
    
    private static final String[] ATTACHMENT_FORMATS = new String[] { "gif", "jpg", "jpeg", "png", "bmp", "webm" };
    
    private static final String PREF_KEY_ONLY_NEW_POSTS = "PREF_KEY_ONLY_NEW_POSTS";
    private static final String PREF_KEY_USE_HTTPS = "PREF_KEY_USE_HTTPS";
    private static final String PREF_KEY_CLOUDFLARE_COOKIE = "PREF_KEY_CLOUDFLARE_COOKIE";
    
    private static final String CLOUDFLARE_COOKIE_NAME = "cf_clearance";
    private static final String CLOUDFLARE_RECAPTCHA_KEY = "6LeT6gcAAAAAAAZ_yDmTMqPH57dJQZdQcu6VFqog"; 
    private static final String CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT = "cdn-cgi/l/chk_captcha?recaptcha_challenge_field=%s&recaptcha_response_field=%s";
    
    private Map<String, String> boardNames = null;
    private Map<String, Integer> boardPagesCount = null;
    
    public HorochanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Horochan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_horochan, null);
    }
    
    private boolean useHttps() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_USE_HTTPS), true);
    }
    
    private String getUsingUrl() {
        return getUsingUrl(false);
    }
    
    private String getUsingUrl(boolean api) {
        return (useHttps() ? "https://" : "http://") + (api ? "api." : "") + DOMAIN + "/";
    }
    
    private String getStaticUrl() {
        return (useHttps() ? "https://" : "http://") + "static." + DOMAIN + "/";
    }
    
    @Override
    protected void initHttpClient() {
        String cloudflareCookie = preferences.getString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), null);
        if (cloudflareCookie != null) {
            BasicClientCookie c = new BasicClientCookie(CLOUDFLARE_COOKIE_NAME, cloudflareCookie);
            c.setDomain(DOMAIN);
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
            if (cookie.getName().equals(CLOUDFLARE_COOKIE_NAME)) {
                preferences.edit().putString(getSharedKey(PREF_KEY_CLOUDFLARE_COOKIE), cookie.getValue()).commit();
            }
        }
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference onlyNewPostsPreference = new CheckBoxPreference(context); //only_new_posts
        onlyNewPostsPreference.setTitle(R.string.pref_only_new_posts);
        onlyNewPostsPreference.setSummary(R.string.pref_only_new_posts_summary);
        onlyNewPostsPreference.setKey(getSharedKey(PREF_KEY_ONLY_NEW_POSTS));
        onlyNewPostsPreference.setDefaultValue(true);
        preferenceGroup.addItemFromInflater(onlyNewPostsPreference);
        CheckBoxPreference httpsPref = new CheckBoxPreference(context); //https
        httpsPref.setTitle(R.string.pref_use_https);
        httpsPref.setSummary(R.string.pref_use_https_summary);
        httpsPref.setKey(getSharedKey(PREF_KEY_USE_HTTPS));
        httpsPref.setDefaultValue(true);
        preferenceGroup.addPreference(httpsPref);
        addUnsafeSslPreference(preferenceGroup, getSharedKey(PREF_KEY_USE_HTTPS));
        addProxyPreferences(preferenceGroup);
    }
    
    private boolean loadOnlyNewPosts() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_ONLY_NEW_POSTS), true);
    }
    
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        JSONObject response = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        try {
            response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, true);
        } catch (HttpWrongStatusCodeException e) {
            checkCloudflareError(e, url);
            throw e;
        }
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return response;
    }
    
    private void checkCloudflareError(HttpWrongStatusCodeException e, String url) throws CloudflareException {
        if (e.getStatusCode() == 403) {
            if (e.getHtmlString() != null && e.getHtmlString().contains("CAPTCHA")) {
                throw CloudflareException.withRecaptcha(CLOUDFLARE_RECAPTCHA_KEY,
                        getUsingUrl() + CLOUDFLARE_RECAPTCHA_CHECK_URL_FMT, CLOUDFLARE_COOKIE_NAME, getChanName());
            }
        } else if (e.getStatusCode() == 503) {
            if (e.getHtmlString() != null && e.getHtmlString().contains("Just a moment...")) {
                throw CloudflareException.antiDDOS(url, CLOUDFLARE_COOKIE_NAME, getChanName());
            }
        }
    }

    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) {
        if (boardNames == null) boardNames = new HashMap<>();
        boardNames.put("b", "Random"); // Virtual board
        return new SimpleBoardModel[]{ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", null, false)};
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardNames == null || boardNames.isEmpty()) getBoardsList(listener, task, null);
        BoardModel board = new BoardModel();
        board.chan = CHAN_NAME;
        board.boardName = shortName;
        board.boardDescription = boardNames != null ? boardNames.get(shortName) : shortName;
        if (board.boardDescription == null) board.boardDescription = shortName;
        board.boardCategory = "";
        board.nsfw = false;
        board.uniqueAttachmentNames = true;
        board.timeZoneId = "GMT+3";
        board.defaultUserName = "Anonymous";
        board.bumpLimit = 250;
        
        board.readonlyBoard = false;
        board.requiredFileForNewThread = false;
        board.allowDeletePosts = true;
        board.allowDeleteFiles = false;
        board.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        board.allowNames = false;
        board.allowSubjects = true;
        board.allowSage = false;
        board.allowEmails = false;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 4;
        board.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        board.markType = BoardModel.MARK_WAKABAMARK;
        
        board.firstPage = 1;
        board.lastPage = (boardPagesCount != null && boardPagesCount.containsKey(shortName)) ?
                boardPagesCount.get(shortName) : BoardModel.LAST_PAGE_UNDEFINED;
        board.searchAllowed = false;
        board.catalogAllowed = false;
        return board;
    }
    
    private PostModel mapPostModel(JSONObject json) {
        PostModel model = new PostModel();
        model.number = Long.toString(json.getLong("id"));
        model.name = "Anonymous";
        model.comment = json.optString("message");
        model.timestamp = json.optLong("timestamp") * 1000;
        model.parentThread = Long.toString(json.optLong("parent", 0));
        // Is OP
        if (model.parentThread.equals("0")) {
            model.subject = json.optString("subject");
            model.parentThread = model.number;
        }
        JSONArray files = json.optJSONArray("files");
        if (files != null) {
            model.attachments = new AttachmentModel[files.length()];
            for (int i=0; i<files.length(); ++i) {
                JSONObject file = files.getJSONObject(i);
                String name = file.optString("name");
                String ext = file.optString("ext");
                model.attachments[i] = new AttachmentModel();
                model.attachments[i].path = getStaticUrl() + "src/" + name + "." + ext;
                model.attachments[i].thumbnail = getStaticUrl() + "thumb/t" + name + ".jpeg";
                model.attachments[i].size = file.optInt("size", -1);
                if (model.attachments[i].size > 0) model.attachments[i].size = Math.round(model.attachments[i].size / 1024f);
                model.attachments[i].width = file.optInt("width", -1);
                model.attachments[i].height = file.optInt("height", -1);
                if (ext.equalsIgnoreCase("gif")) {
                    model.attachments[i].type = AttachmentModel.TYPE_IMAGE_GIF;
                }
                else if (ext.equalsIgnoreCase("webm")) {
                    model.attachments[i].type = AttachmentModel.TYPE_VIDEO;
                }
                else {
                    model.attachments[i].type = AttachmentModel.TYPE_IMAGE_STATIC;
                }
            }
        }
        String embed = json.optString("embed");
        if (embed != null && embed.length() > 0) {
            AttachmentModel[] attachments = new AttachmentModel[1 + (model.attachments == null ? 0 : model.attachments.length)];
            for (int i=0; i<attachments.length-1; ++i) attachments[i] = model.attachments[i];
            AttachmentModel embedded = new AttachmentModel();
            embedded.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            embedded.path = "http://youtube.com/watch?v=" + embed;
            embedded.thumbnail = "http://img.youtube.com/vi/" + embed + "/default.jpg";
            attachments[attachments.length - 1] = embedded;
            model.attachments = attachments;
        }
        return model;
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl(true) + "v1/boards/" + Integer.toString(page);
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        int totalPages = json.optInt("totalPages", -1);
        if (totalPages != -1) {
            if (boardPagesCount == null) boardPagesCount = new HashMap<>();
            boardPagesCount.put(boardName, totalPages);
        }
        try {
            JSONArray data = json.getJSONArray("data");
            ThreadModel[] threads = new ThreadModel[data.length()];
            for (int i=0; i<data.length(); ++i) {
                JSONObject current = data.getJSONObject(i);
                JSONArray replies = current.optJSONArray("replies");
                threads[i] = new ThreadModel();
                threads[i].posts = new PostModel[1 + (replies != null ? replies.length() : 0)];
                threads[i].posts[0] = mapPostModel(current);
                threads[i].threadNumber = threads[i].posts[0].number;
                threads[i].postsCount = current.optInt("replies_count", -2) + 1;
                for (int j=1; j<threads[i].posts.length; ++j) threads[i].posts[j] = mapPostModel(replies.getJSONObject(j - 1));
            }
            return threads;
        } catch (JSONException e) {
            if (json.has("message")) throw new Exception(json.getString("message"));
            throw e;
        }
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = getUsingUrl(true) + "v1/threads/" + threadNumber;
        boolean onlyNewPosts = loadOnlyNewPosts() && oldList != null && oldList.length > 0;
        if (onlyNewPosts) url += "/after/" + oldList[oldList.length - 1].number;
        JSONObject json = downloadJSONObject(url, oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            if (onlyNewPosts) {
                JSONArray data = json.getJSONArray("data");
                if (data.length() == 0) return oldList;
                PostModel[] result = new PostModel[oldList.length + data.length()];
                for (int i=0; i<oldList.length; ++i) result[i] = oldList[i];
                for (int i=0; i<data.length(); ++i) result[i + oldList.length] = mapPostModel(data.getJSONObject(i));
                return result;
            } else {
                JSONObject data = json.getJSONObject("data");
                JSONArray replies = data.optJSONArray("replies");
                PostModel[] posts = new PostModel[1 + (replies != null ? replies.length() : 0)];
                posts[0] = mapPostModel(data);
                for (int i=1; i<posts.length; ++i) posts[i] = mapPostModel(replies.getJSONObject(i - 1));
                if (oldList == null) return posts;
                return ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(posts));
            }
        } catch (JSONException e) {
            if (json.has("message")) throw new Exception(json.getString("message"));
            throw e;
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        return null;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        boolean isThread = model.threadNumber == null;
        String url = getUsingUrl(true) + (isThread ? "v1/threads" : "v1/posts");
        
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task);
        if (isThread) {
            postEntityBuilder.addString("subject", model.subject);
        }
        else {
            postEntityBuilder.addString("parent", model.threadNumber);
        }
        postEntityBuilder.
                addString("message", model.comment).
                addString("password", model.password);
        
        if (model.attachments != null && model.attachments.length > 0) {
            for (File attachment : model.attachments) {
                postEntityBuilder.addFile("files[]", attachment, model.randomHash);
            }
        }
        
        if (isThread) {
            String response = Recaptcha2solved.pop(RECAPTCHA_PUBLIC_KEY);
            if (response == null) {
                throw new Recaptcha2js(RECAPTCHA_PUBLIC_KEY);
            }
            postEntityBuilder.addString("g-recaptcha-response", response);
        }
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).build();
        HttpResponseModel response = null;
        BufferedReader in = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            in = new BufferedReader(new InputStreamReader(response.stream));
            JSONObject json = new JSONObject(new JSONTokener(in));
            if (response.statusCode == 200) return null;
            if (response.statusCode == 400) {
                JSONArray errors = json.getJSONArray("message");
                throw new Exception(errors.getJSONObject(0).getJSONArray("errors").getString(0));
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        }
        finally {
            IOUtils.closeQuietly(in);
            if (response != null) response.release();
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl(true) + "v1/posts/" + model.postNumber;
        HttpEntity entity = ExtendedMultipartBuilder.create().setDelegates(listener, task).addString("password", model.password).build();
        HttpUriRequest request = null;
        HttpResponse response = null;
        HttpEntity responseEntity = null;
        try {
            request = RequestBuilder.delete().setUri(url).setEntity(entity).build();
            response = httpClient.execute(request);
            StatusLine status = response.getStatusLine();
            switch (status.getStatusCode()) {
                case 200:
                    return null;
                case 400:
                    responseEntity = response.getEntity();
                    InputStream stream = IOUtils.modifyInputStream(responseEntity.getContent(), null, task);
                    JSONObject json = new JSONObject(new JSONTokener(new BufferedReader(new InputStreamReader(stream))));
                    throw new Exception(json.getString("message"));
                default:
                    throw new Exception(status.getStatusCode() + " - " + status.getReasonPhrase());
            }
        } finally {
            try { if (request != null) request.abort(); } catch (Exception e) {}
            EntityUtils.consumeQuietly(responseEntity);
            if (response != null && response instanceof Closeable) IOUtils.closeQuietly((Closeable) response);
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w+")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(getUsingUrl());
        try {
            switch (model.type) {
                case UrlPageModel.TYPE_INDEXPAGE:
                    return url.toString();
                case UrlPageModel.TYPE_BOARDPAGE:
                    if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE || model.boardPage == 1)
                        return url.append(model.boardName).append('/').toString();
                    return url.append(model.boardName).append('/').append(model.boardPage).toString();
                case UrlPageModel.TYPE_THREADPAGE:
                    return url.append(model.boardName).append("/thread/").append(model.threadNumber).
                            append(model.postNumber == null || model.postNumber.length() == 0 ? "" : ("/#" + model.postNumber)).toString();
                case UrlPageModel.TYPE_OTHERPAGE:
                    return url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath).toString();
            }
        } catch (Exception e) {}
        throw new IllegalArgumentException("wrong page type");
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String path = RegexUtils.getUrlPath(url, DOMAIN).toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        
        if (path.length() == 0 || path.equals("index.html")) {
            model.type = UrlPageModel.TYPE_INDEXPAGE;
            return model;
        }
        
        Matcher threadPage = URL_PATH_THREADPAGE_PATTERN.matcher(path);
        if (threadPage.find()) {
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.boardName = threadPage.group(1);
            model.threadNumber = threadPage.group(2);
            model.postNumber = null;
            return model;
        }
        
        Matcher boardPage = URL_PATH_BOARDPAGE_PATTERN.matcher(path);
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