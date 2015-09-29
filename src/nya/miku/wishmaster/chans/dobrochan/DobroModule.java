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

package nya.miku.wishmaster.chans.dobrochan;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookieHC4;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;
import android.text.TextUtils;
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
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class DobroModule extends AbstractChanModule {
    private static final String TAG = "DobroModule";
    
    static final String CHAN_NAME = "dobrochan";
    
    private static final List<String> DOMAINS_LIST = Arrays.asList(new String[] { "dobrochan.ru", "dobrochan.com", "dobrochan.org" });
    
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    
    private static final String PREF_KEY_ONLY_NEW_POSTS = "PREF_KEY_ONLY_NEW_POSTS";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final String PREF_KEY_HANABIRA_COOKIE = "PREF_KEY_HANABIRA_COOKIE";
    private static final String HANABIRA_COOKIE_NAME = "hanabira";
    private static final String DEFAULT_DOMAIN = "dobrochan.com";
    
    private String domain;
    
    public DobroModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Доброчан";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dobrochan, null);
    }
    
    @Override
    protected void initHttpClient() {
        domain = preferences.getString(getSharedKey(PREF_KEY_DOMAIN), DEFAULT_DOMAIN);
        if (domain.length() == 0) domain = DEFAULT_DOMAIN;
        loadHanabiraCookie();
    }
    
    private String getDomain() {
        return domain;
    }
    
    private String getDomainUrl() {
        return "http://" + getDomain() + "/";
    }
    
    @Override
    public void saveCookie(Cookie cookie) {
        if (cookie != null) {
            httpClient.getCookieStore().addCookie(cookie);
            saveHanabiraCookie();
        }
    }
    
    private void saveHanabiraCookie() {
        for (Cookie cookie : httpClient.getCookieStore().getCookies())
            if (cookie.getName().equals(HANABIRA_COOKIE_NAME))
                preferences.edit().putString(getSharedKey(PREF_KEY_HANABIRA_COOKIE), cookie.getValue()).commit();
    }
    
    private void loadHanabiraCookie() {
        String hanabiraCookie = preferences.getString(getSharedKey(PREF_KEY_HANABIRA_COOKIE), null);
        if (hanabiraCookie != null) {
            BasicClientCookieHC4 c = new BasicClientCookieHC4(HANABIRA_COOKIE_NAME, hanabiraCookie);
            c.setDomain(getDomain());
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        JSONObject response = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        response = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, true);
        saveHanabiraCookie();
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return response;
    }
    
    private void addOnlyNewPostsPreference(PreferenceGroup group) {
        Context context = group.getContext();
        CheckBoxPreference onlyNewPostsPreference = new CheckBoxPreference(context);
        onlyNewPostsPreference.setTitle(R.string.pref_only_new_posts);
        onlyNewPostsPreference.setSummary(R.string.pref_only_new_posts_summary);
        onlyNewPostsPreference.setKey(getSharedKey(PREF_KEY_ONLY_NEW_POSTS));
        onlyNewPostsPreference.setDefaultValue(true);
        group.addItemFromInflater(onlyNewPostsPreference);
    }
    
    private boolean loadOnlyNewPosts() {
        return preferences.getBoolean(getSharedKey(PREF_KEY_ONLY_NEW_POSTS), true);
    }
    
    private void addDomainPreferences(PreferenceGroup group) {
        Context context = group.getContext();
        Preference.OnPreferenceChangeListener updateDomainListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(getSharedKey(PREF_KEY_DOMAIN))) {
                    domain = (String) newValue;
                    if (domain.length() == 0) domain = DEFAULT_DOMAIN;
                    loadHanabiraCookie();
                    return true;
                }
                return false;
            }
        };
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.dobrochan_prefs_domain);
        domainPref.setDialogTitle(R.string.dobrochan_prefs_domain);
        domainPref.setSummary(R.string.dobrochan_prefs_domain_summary);
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainPref.setOnPreferenceChangeListener(updateDomainListener);
        group.addPreference(domainPref);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addOnlyNewPostsPreference(preferenceGroup);
        addDomainPreferences(preferenceGroup);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return DobroBoards.getBoardsList();
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return DobroBoards.getBoard(shortName);
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getDomainUrl() + boardName + "/" + Integer.toString(page) + ".json?new_format";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray threads = response.getJSONObject("boards").getJSONObject(boardName).getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i=0, len=threads.length(); i<len; ++i) {
            JSONObject thread = threads.getJSONObject(i);
            ThreadModel threadModel = new ThreadModel();
            threadModel.threadNumber = Long.toString(thread.getLong("display_id"));
            threadModel.postsCount = thread.optInt("posts_count", -1);
            threadModel.attachmentsCount = thread.optInt("files_count", -1);
            JSONArray posts = thread.getJSONArray("posts");
            threadModel.posts = new PostModel[posts.length()];
            for (int j=0, postslen=posts.length(); j<postslen; ++j) {
                threadModel.posts[j] = mapPostModel(posts.getJSONObject(j), threadModel.threadNumber);
            }
            result[i] = threadModel;
        }
        return result;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        boolean loadOnlyNewPosts = loadOnlyNewPosts();
        String lastPost = (loadOnlyNewPosts && oldList != null && oldList.length > 0) ? oldList[oldList.length-1].number : null;
        String url = getDomainUrl() + "api/thread/" + boardName + "/" + threadNumber +
                (lastPost == null ? "/all.json?new_format&message_html" : ("/new.json?last_post=" + lastPost + "&new_format&message_html"));
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        try {
            JSONObject object = response.getJSONObject("result");
            if (!object.has("posts")) {
                if (oldList != null && oldList.length > 0) return oldList; else throw new Exception();
            }
            JSONArray posts = object.getJSONArray("posts");
            PostModel[] newPosts = new PostModel[posts.length()];
            for (int i=0, len=posts.length(); i<len; ++i) newPosts[i] = mapPostModel(posts.getJSONObject(i), threadNumber);
            if (oldList == null || oldList.length == 0) return newPosts;
            if (loadOnlyNewPosts) {
                ArrayList<PostModel> list = new ArrayList<PostModel>(Arrays.asList(oldList));
                for (int i=0; i<newPosts.length; ++i) list.add(newPosts[i]);
                return list.toArray(new PostModel[list.size()]);
            } else {
                return ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(newPosts));
            }
        } catch (JSONException e) {
            JSONObject error = response.getJSONObject("error");
            throw new Exception(error.getString("message"));
        }
    }
    
    private PostModel mapPostModel(JSONObject json, String parentThread) {
        PostModel model = new PostModel();
        model.number = Long.toString(json.getLong("display_id"));
        model.parentThread = parentThread;
        model.comment = json.optString("message_html", "");
        if (TextUtils.isEmpty(model.comment)) {
            model.comment = StringEscapeUtils.escapeHtml4(json.optString("message", "")).
                    replace("\r\n", "<br />").replace("\n", "<br />").
                    replaceAll("[_\\*][_\\*](.*?)[_\\*][_\\*]", "<b>$1</b>").
                    replaceAll("[_\\*](.*?)[_\\*]", "<i>$1</i>").
                    replaceAll("%%(.*?)%%", "<span class=\"spoiler\">$1</spoiler>").
                    replaceAll("`(.*?)`", "<tt>$1</tt>").
                    replaceAll("\\bhttps?://.+\\b", "<a href=\"$0\">$0</a>").
                    replaceAll("&gt;&gt;(\\d+)\\b", "<a href=\"#i$1\">&gt;&gt;$1</a>").
                    replaceAll("(^|<br />)(&gt;.*?)($|<br />)", "$1<span class=\"unkfunc\">$2</span>$3");
        } else {
            model.comment = model.comment.replaceAll("<blockquote depth=\"\\d*\">", "<blockquote class=\"unkfunc\">");
        }
        model.subject = json.optString("subject", "");
        model.name = json.optString("name", "");
        try {
            model.timestamp = DATE_FORMAT.parse(json.getString("date")).getTime();
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        try {
            JSONArray files = json.getJSONArray("files");
            int filesLen = files.length();
            if (filesLen > 0) {
                AttachmentModel[] attachments = new AttachmentModel[filesLen];
                for (int i=0; i<filesLen; ++i) {
                    attachments[i] = mapAttachmentModel(files.getJSONObject(i));
                }
                model.attachments = attachments;
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return model;
    }
    
    private AttachmentModel mapAttachmentModel(JSONObject json) {
        AttachmentModel model = new AttachmentModel();
        model.path = "/" + json.getString("src");
        model.thumbnail = json.optString("thumb", null);
        if (model.thumbnail != null) model.thumbnail = "/" + model.thumbnail;
        try {
            JSONObject metadata = json.getJSONObject("metadata");
            model.width = metadata.getInt("width");
            model.height = metadata.getInt("height");
        } catch (Exception e) {
            try {
                model.width = json.getInt("thumb_width");
                model.height = json.getInt("thumb_height");
            } catch (Exception e1) {
                model.width = -1;
                model.height = -1;
            }
        }
        String type = json.optString("type", "");
        switch (type) {
            case "image":
                model.type = model.path.toLowerCase(Locale.US).endsWith(".gif") ? AttachmentModel.TYPE_IMAGE_GIF : AttachmentModel.TYPE_IMAGE_STATIC;
                break;
            case "music":
                model.type = AttachmentModel.TYPE_AUDIO;
                break;
            case "video":
                model.type = AttachmentModel.TYPE_VIDEO;
                break;
            default:
                model.type = AttachmentModel.TYPE_OTHER_FILE;
                break;
        }
        if (model.type == AttachmentModel.TYPE_AUDIO && "/thumb/generic/sound.png".equals(model.thumbnail)) model.thumbnail = null;
        model.size = json.optInt("size", -1);
        if (model.size > 0) model.size = Math.round(model.size / 1024f);
        model.isSpoiler = json.optString("rating", "sfw").toLowerCase(Locale.US).startsWith("r-18");
        return model;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getDomainUrl() + "captcha/" + boardName + "/" + System.currentTimeMillis() + ".png";
        
        Bitmap captchaBitmap = null;
        HttpRequestModel requestModel = HttpRequestModel.builder().setGET().build();
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
        try {
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
            saveHanabiraCookie();
        }
        CaptchaModel captchaModel = new CaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        return captchaModel;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getDomainUrl() + model.boardName + "/post/new.xhtml";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("thread_id", model.threadNumber == null ? "0" : model.threadNumber).
                addString("task", "post").
                addString("name", model.name);
        if (model.sage) postEntityBuilder.addString("sage", "on");
        postEntityBuilder.
                addString("subject", model.subject).
                addString("new_post", "Отправить").
                addString("message", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("password", model.password);
        
        String rating = (model.icon >= 0 && model.icon < DobroBoards.RATINGS.length) ? DobroBoards.RATINGS[model.icon] : "SFW";
        int filesCount = model.attachments != null ? model.attachments.length : 0;
        postEntityBuilder.addString("post_files_count", Integer.toString(filesCount + 1));
        for (int i=0; i<filesCount; ++i) {
            postEntityBuilder.addFile("file_" + Integer.toString(i+1), model.attachments[i], model.randomHash);
            postEntityBuilder.addString("file_" + Integer.toString(i+1) + "_rating", rating);
        }
        postEntityBuilder.addString("goto", "thread");
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        String location = fixRelativeUrl(header.getValue());
                        if (location.contains("/error/")) {
                            String errorMessage = "";
                            String errorHtml = HttpStreamer.getInstance().
                                    getStringFromUrl(location, HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
                            Matcher errorMatcher = Pattern.compile("class='post-error'>([^<]*)<").matcher(errorHtml);
                            while (errorMatcher.find()) errorMessage += (errorMessage.equals("") ? "" : "; ") + errorMatcher.group(1);
                            if (errorMessage.equals("")) errorMessage = "";
                            throw new Exception(errorMessage);
                        }
                        return location;
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
            saveHanabiraCookie();
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getDomainUrl() + model.boardName + "/delete";
        String threadAPIUrl = getDomainUrl() + "api/thread/" + model.boardName + "/" + model.threadNumber + ".json";
        String threadId = Long.toString(downloadJSONObject(threadAPIUrl, false, null, task).getLong("thread_id"));
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair(model.postNumber, threadId));
        pairs.add(new BasicNameValuePair("task", "delete"));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = Pattern.compile("<center><h2>([^<]*)<").matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
            }
        } finally {
            if (response != null) response.release();
            saveHanabiraCookie();
        }
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w*")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(getDomainUrl());
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                url.append(model.boardName).append("/");
                if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE) model.boardPage = 0;
                if (model.boardPage != 0) url.append(model.boardPage).append(".xhtml");
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                url.append(model.boardName).append("/res/").append(model.threadNumber).append(".xhtml");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#i").append(model.postNumber);
                break;
            case UrlPageModel.TYPE_OTHERPAGE:
                url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath);
        }
        return url.toString();
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www\\.)?(.+)").matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(parseUrl.group(1));
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        
        if ((!getDomain().equals(domain)) && (DOMAINS_LIST.indexOf(domain) == -1)) {
            throw new IllegalArgumentException("wrong domain");
        }
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        try {
            if (path.contains("/res/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("(.+?)/res/([0-9]+?)\\.xhtml(.*)").matcher(path);
                if (!matcher.find()) throw new Exception();
                model.boardName = matcher.group(1);
                model.threadNumber = matcher.group(2);
                if (matcher.group(3).startsWith("#i")) {
                    String post = matcher.group(3).substring(2);
                    if (!post.equals("")) model.postNumber = post;
                }
            } else {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                
                if (path.indexOf("/") == -1) {
                    if (path.equals("")) throw new Exception();
                    model.boardName = path;
                    model.boardPage = 0;
                } else {
                    model.boardName = path.substring(0, path.indexOf("/"));
                    
                    String page = path.substring(path.lastIndexOf("/") + 1);
                    if (!page.equals("")) {
                        String pageNum = page.substring(0, page.indexOf(".xhtml"));
                        model.boardPage = pageNum.equals("index") ? 0 : Integer.parseInt(pageNum);
                    } else model.boardPage = 0;
                }
            }
        } catch (Exception e) {
            if (path == null || path.length() == 0 || path.equals("/")) {
                model.type = UrlPageModel.TYPE_INDEXPAGE;
            } else {
                model.type = UrlPageModel.TYPE_OTHERPAGE;
                model.otherPath = path;
            }
        }
        return model;
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        return sanitizeUrl(super.fixRelativeUrl(url));
    }
    
    private String sanitizeUrl(String urlStr) {
        if (urlStr == null) return null;
        try {
            URL url = new URL(urlStr);
            URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
            return uri.toURL().toString();
        } catch (Exception e) {
            Logger.e(TAG, "sanitize url", e);
            return urlStr;
        }
    }
    
}
