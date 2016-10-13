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

package nya.miku.wishmaster.chans.dobrochan;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
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
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
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
import nya.miku.wishmaster.api.util.LazyPreferences;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class DobroModule extends AbstractChanModule {
    private static final String TAG = "DobroModule";
    
    static final String CHAN_NAME = "dobrochan";
    
    private static final Pattern URL_THREADPAGE_PATTERN = Pattern.compile("(.+?)/res/([0-9]+?)\\.xhtml(.*)");
    
    private static final String DOMAINS_HINT = "dobrochan.com, dobrochan.org, dobrochan.ru";
    private static final List<String> DOMAINS_LIST = Arrays.asList(new String[] { "dobrochan.ru", "dobrochan.com", "dobrochan.org" });
    
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
    }
    
    private static final String PREF_KEY_MAX_RATING = "PREF_KEY_MAX_RATING";
    private static final String PREF_KEY_SHOW_CAPTCHA = "PREF_KEY_SHOW_CAPTCHA";
    private static final String PREF_KEY_DOMAIN = "PREF_KEY_DOMAIN";
    private static final String PREF_KEY_HANABIRA_COOKIE = "PREF_KEY_HANABIRA_COOKIE";
    private static final String HANABIRA_COOKIE_NAME = "hanabira_temp";
    private static final String DEFAULT_DOMAIN = "dobrochan.com";
    
    static final String[] RATINGS = new String[] { "SFW", "R-15", "R-18", "R-18G" };
    private static final int MAXRATING_SFW = 0;
    private static final int MAXRATING_R15 = 1;
    private static final int MAXRATING_R18 = 2;
    private static final int MAXRATING_R18G = 3;
    
    private String domain;
    
    private boolean postingError = false;
    
    private int maxRating;
    
    public DobroModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
        setMaxRating();
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
            BasicClientCookie c = new BasicClientCookie(HANABIRA_COOKIE_NAME, hanabiraCookie);
            c.setDomain(getDomain());
            httpClient.getCookieStore().addCookie(c);
        }
    }
    
    @Override
    protected JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        JSONObject response = super.downloadJSONObject(url, checkIfModidied, listener, task);
        saveHanabiraCookie();
        return response;
    }
    
    private boolean loadOnlyNewPosts() {
        return loadOnlyNewPosts(true);
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
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainPref.setOnPreferenceChangeListener(updateDomainListener);
        group.addPreference(domainPref);
    }
    
    private void addRatingPreference(PreferenceGroup group) {
        Context context = group.getContext();
        Preference.OnPreferenceChangeListener updateRatingListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(getSharedKey(PREF_KEY_MAX_RATING))) {
                    setMaxRating((String) newValue);
                    return true;
                }
                return false;
            }
        };
        ListPreference ratingPref = new LazyPreferences.ListPreference(context);
        ratingPref.setTitle(R.string.dobrochan_prefs_max_rating);
        ratingPref.setSummary(preferences.getString(getSharedKey(PREF_KEY_MAX_RATING), "R-15"));
        ratingPref.setEntries(RATINGS);
        ratingPref.setEntryValues(RATINGS);
        ratingPref.setDefaultValue("R-15");
        ratingPref.setKey(getSharedKey(PREF_KEY_MAX_RATING));
        ratingPref.setOnPreferenceChangeListener(updateRatingListener);
        group.addPreference(ratingPref);
    }
    
    private void setMaxRating() {
        setMaxRating(preferences.getString(getSharedKey(PREF_KEY_MAX_RATING), "R-15"));
    }
    
    private void setMaxRating(String key) {
        switch (key) {
            case "SFW": maxRating = MAXRATING_SFW; break;
            case "R-15": maxRating = MAXRATING_R15; break;
            case "R-18": maxRating = MAXRATING_R18; break;
            case "R-18G": maxRating = MAXRATING_R18G; break;
        }
    }
    
    private void addCaptchaPreference(PreferenceGroup group) {
        Context context = group.getContext();
        CheckBoxPreference showCaptchaPreference = new LazyPreferences.CheckBoxPreference(context);
        showCaptchaPreference.setTitle(R.string.dobrochan_prefs_show_captcha);
        showCaptchaPreference.setSummary(R.string.dobrochan_prefs_show_captcha_summary);
        showCaptchaPreference.setKey(getSharedKey(PREF_KEY_SHOW_CAPTCHA));
        showCaptchaPreference.setDefaultValue(false);
        group.addPreference(showCaptchaPreference);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addOnlyNewPostsPreference(preferenceGroup, true);
        addRatingPreference(preferenceGroup);
        addCaptchaPreference(preferenceGroup);
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
        return getPostsList(boardName, threadNumber, listener, task, oldList, true);
    }
    
    private PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList,
            boolean allowLoadOnlyNewPosts) throws Exception {
        boolean loadOnlyNewPosts = allowLoadOnlyNewPosts && loadOnlyNewPosts();
        String lastPost = (loadOnlyNewPosts && oldList != null && oldList.length > 0) ? oldList[oldList.length-1].number : null;
        String url = getDomainUrl() + "api/thread/" + boardName + "/" + threadNumber +
                (lastPost == null ? "/all.json?new_format&message_html" : ("/new.json?last_post=" + lastPost + "&new_format&message_html"));
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        try {
            JSONObject object = response.getJSONObject("result");
            if (lastPost != null) {
                try {
                    int oldPosts = oldList.length;
                    for (PostModel post : oldList) if (post.deleted) --oldPosts;
                    int newPosts = object.has("posts") ? object.getJSONArray("posts").length() : 0;
                    int postsInThread = object.getInt("posts_count");
                    boolean assertion = oldPosts + newPosts == postsInThread;
                    //Logger.d(TAG, "assert: (" + oldList.length + " + " + newPosts + " == " + postsInThread + ") == " + assertion);
                    if (!assertion) return getPostsList(boardName, threadNumber, listener, task, oldList, false);
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
            }
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
            model.comment = RegexUtils.linkify(StringEscapeUtils.escapeHtml4(json.optString("message", "")).
                    replace("\r\n", "<br />").replace("\n", "<br />").
                    replaceAll("[_\\*][_\\*](.*?)[_\\*][_\\*]", "<b>$1</b>").
                    replaceAll("[_\\*](.*?)[_\\*]", "<i>$1</i>").
                    replaceAll("%%(.*?)%%", "<span class=\"spoiler\">$1</span>").
                    replaceAll("`(.*?)`", "<tt>$1</tt>").
                    replaceAll("&gt;&gt;(\\d+)\\b", "<a href=\"#i$1\">&gt;&gt;$1</a>").
                    replaceAll("(^|<br />)(&gt;.*?)($|<br />)", "$1<span class=\"unkfunc\">$2</span>$3"). //<br />&gt;...<br />&gt;...
                    replaceAll("(^|<br />)(&gt;.*?)($|<br />)", "$1<span class=\"unkfunc\">$2</span>$3"));
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
        String rating = json.optString("rating", "sfw").toLowerCase(Locale.US);
        switch (maxRating) {
            case MAXRATING_SFW: model.isSpoiler = rating.startsWith("r-1"); break;
            case MAXRATING_R15: model.isSpoiler = rating.startsWith("r-18"); break;
            case MAXRATING_R18: model.isSpoiler = rating.startsWith("r-18g"); break;
        }
        return model;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (!postingError && !preferences.getBoolean(getSharedKey(PREF_KEY_SHOW_CAPTCHA), false)) {
            try {
                String userJsonUrl = getDomainUrl() + "api/user.json?new_format";
                JSONObject userJson = downloadJSONObject(userJsonUrl, false, null, task);
                JSONArray tokens = userJson.getJSONObject("result").getJSONArray("tokens");
                for (int i=0; i<tokens.length(); ++i) {
                    JSONObject token = tokens.getJSONObject(i);
                    if (token.getString("token").equals("no_user_captcha")) return null;
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        
        try {
            String captchaUrl = getDomainUrl() + "captcha/" + boardName + "/" + System.currentTimeMillis() + ".png";
            return downloadCaptcha(captchaUrl, listener, task);
        } finally {
            saveHanabiraCookie();
        }
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
                addString("captcha", TextUtils.isEmpty(model.captchaAnswer) ? "" : model.captchaAnswer).
                addString("password", model.password);
        
        String rating = (model.icon >= 0 && model.icon < RATINGS.length) ? RATINGS[model.icon] : "SFW";
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
                            postingError = true;
                            String errorMessage = "";
                            String errorHtml = HttpStreamer.getInstance().
                                    getStringFromUrl(location, HttpRequestModel.DEFAULT_GET, httpClient, null, task, false);
                            Matcher errorMatcher = Pattern.compile("class='post-error'>([^<]*)<").matcher(errorHtml);
                            while (errorMatcher.find()) errorMessage += (errorMessage.equals("") ? "" : "; ") + errorMatcher.group(1);
                            if (errorMessage.equals("")) errorMessage = RegexUtils.removeHtmlTags(errorHtml).trim();
                            throw new Exception(errorMessage);
                        }
                        postingError = false;
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
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
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
        String path = UrlPathUtils.getUrlPath(url, getDomain(), DOMAINS_LIST);
        if (path == null) throw new IllegalArgumentException("wrong domain");
        path = path.toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = CHAN_NAME;
        try {
            if (path.contains("/res/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = URL_THREADPAGE_PATTERN.matcher(path);
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
