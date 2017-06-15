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

package nya.miku.wishmaster.chans.nullchan;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.SequenceInputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceGroup;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.message.BasicNameValuePair;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public abstract class AbstractInstant0chan extends AbstractKusabaModule {
    public AbstractInstant0chan(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected boolean loadOnlyNewPosts() {
        return loadOnlyNewPosts(true);
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addOnlyNewPostsPreference(preferenceGroup, true);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "boards10.json";
        try {
            JSONArray json = downloadJSONArray(url, oldBoardsList != null, listener, task);
            if (json == null) return oldBoardsList;
            List<SimpleBoardModel> list = new ArrayList<>();
            for (int i=0; i<json.length(); ++i) {
                String currentCategory = json.getJSONObject(i).optString("name");
                JSONArray boards = json.getJSONObject(i).getJSONArray("boards");
                for (int j=0; j<boards.length(); ++j) {
                    SimpleBoardModel model = new SimpleBoardModel();
                    model.chan = getChanName();
                    model.boardName = boards.getJSONObject(j).getString("dir");
                    model.boardDescription = boards.getJSONObject(j).optString("desc", model.boardName);
                    model.boardCategory = currentCategory;
                    model.nsfw = model.boardName.equals("b") || currentCategory.equalsIgnoreCase("adult");
                    list.add(model);
                }
            }
            return list.toArray(new SimpleBoardModel[list.size()]);
        } catch (JSONException e) {
            return new SimpleBoardModel[0];
        }
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "GMT+3";
        model.defaultUserName = "Anonymous";
        model.requiredFileForNewThread = !shortName.equals("0");
        model.allowReport = BoardModel.REPORT_SIMPLE;
        model.allowNames = !shortName.equals("b");
        model.allowEmails = false;
        model.catalogAllowed = true;
        return model;
    }
    
    @Override
    protected final DateFormat getDateFormat() {
        return null;
    }
    
    @Override
    protected WakabaReader getKusabaReader(InputStream stream, UrlPageModel urlModel) {
        if (urlModel != null && urlModel.chanName != null && urlModel.chanName.equals("expand")) {
            stream = new SequenceInputStream(new ByteArrayInputStream("<form id=\"delform\">".getBytes()), stream);
        }
        return new Instant0chanReader(stream, canCloudflare());
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList;
        ThreadModel[] threads = new ThreadModel[response.length()];
        for (int i = 0; i < threads.length; ++i) {
            threads[i] = mapCatalogThreadModel(response.getJSONObject(i), boardName);
        }
        return threads;
    }
    
    private ThreadModel mapCatalogThreadModel(JSONObject json, String boardName) {
        ThreadModel model = new ThreadModel();
        model.threadNumber = json.optString("id", null);
        if (model.threadNumber == null) throw new RuntimeException();
        model.postsCount = json.optInt("reply_count", -2) + 1;
        model.attachmentsCount = json.optInt("images", -2) + 1;
        model.isClosed = json.optInt("locked", 0) != 0;
        model.isSticky = json.optInt("stickied", 0) != 0;
        
        PostModel opPost = new PostModel();
        opPost.number = model.threadNumber;
        opPost.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlSpanTags(json.optString("name")));
        opPost.subject = StringEscapeUtils.unescapeHtml4(json.optString("subject"));
        opPost.comment = json.optString("message");
        opPost.trip = json.optString("tripcode");
        opPost.timestamp = json.optLong("timestamp") * 1000;
        opPost.parentThread = model.threadNumber;
        
        String ext = json.optString("file_type", "");
        if (!ext.isEmpty()) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case "jpg":
                case "jpeg":
                case "png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case "gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case "mp3":
                case "ogg":
                    attachment.type = AttachmentModel.TYPE_AUDIO;
                    break;
                case "webm":
                case "mp4":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                case "you":
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.width = json.optInt("image_w", -1);
            attachment.height = json.optInt("image_h", -1);
            attachment.size = -1;
            String fileName = json.optString("file", "");
            if (!fileName.isEmpty()) {
                if (ext.equals("you")) {
                    attachment.thumbnail = (useHttps() ? "https" : "http")
                            + "://img.youtube.com/vi/" + fileName + "/default.jpg";
                    attachment.path = (useHttps() ? "https" : "http")
                            + "://youtube.com/watch?v=" + fileName;
                } else {
                    attachment.thumbnail = "/" + boardName + "/thumb/" + fileName + "s." + ext;
                    attachment.path = "/" + boardName + "/src/" + fileName + "." + ext;
                }
                opPost.attachments = new AttachmentModel[] { attachment };
            }
        }
        model.posts = new PostModel[] { opPost };
        return model;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        if (loadOnlyNewPosts() && oldList != null && oldList.length > 0) {
            String url = getUsingUrl() + "expand.php?after=" + oldList[oldList.length-1].number + "&board=" + boardName + "&threadid=" + threadNumber;
            UrlPageModel object = new UrlPageModel();
            object.chanName = "expand";
            ThreadModel[] page = readWakabaPage(url, listener, task, true, object);
            if (page != null && page.length > 0) {
                PostModel[] posts = new PostModel[oldList.length + page[0].posts.length];
                for (int i=0; i<oldList.length; ++i) posts[i] = oldList[i];
                for (int i=0; i<page[0].posts.length; ++i) posts[oldList.length + i] = page[0].posts[i];
                return posts;
            } else {
                return oldList;
            }
        }
        return super.getPostsList(boardName, threadNumber, listener, task, oldList);
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "captcha.php?" + Math.random();
        CaptchaModel captchaModel = downloadCaptcha(captchaUrl, listener, task);
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        return captchaModel;
    }
    
    @Override
    protected void setSendPostEntity(SendPostModel model, ExtendedMultipartBuilder postEntityBuilder) throws Exception {
        postEntityBuilder.
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name);
        if (model.sage) postEntityBuilder.addString("em", "sage");
        postEntityBuilder.
                addString("captcha", model.captchaAnswer).
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("postpassword", model.password);
        setSendPostEntityAttachments(model, postEntityBuilder);
        postEntityBuilder.addString("embed", "");
        
        postEntityBuilder.addString("redirecttothread", "1");
    }
    
    @Override
    protected List<? extends NameValuePair> getReportFormAllValues(DeletePostModel model) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        pairs.add(new BasicNameValuePair("reportpost", "Отправить"));
        return pairs;
    }
    
    @Override
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Удалить";
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        if (model.type == UrlPageModel.TYPE_CATALOGPAGE) return getUsingUrl() + model.boardName + "/catalog.html";
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath = UrlPathUtils.getUrlPath(url, getAllDomains());
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        int catalogIndex = url.indexOf("/catalog.html");
        if (catalogIndex > 0) {
            try {
                String path = url.substring(0, catalogIndex);
                UrlPageModel model = new UrlPageModel();
                model.chanName = getChanName();
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                model.boardName = path.substring(path.lastIndexOf('/') + 1);
                model.catalogType = 0;
                return model;
            } catch (Exception e) {}
        }
        return WakabaUtils.parseUrlPath(urlPath, getChanName());
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("//")) return (useHttps() ? "https:" : "http:") + url;
        return super.fixRelativeUrl(url);
    }
    
    @SuppressLint("SimpleDateFormat")
    protected static class Instant0chanReader extends KusabaReader {
        private static final Pattern PATTERN_EMBEDDED = Pattern.compile("<div (?:[^>]*)data-id=\"([^\"]*)\"(?:[^>]*)>", Pattern.DOTALL);
        private static final Pattern PATTERN_TABULATION = Pattern.compile("^\\t{2,}", Pattern.MULTILINE);
        private static final DateFormat DATE_FORMAT;
        static {
            DateFormatSymbols symbols = new DateFormatSymbols();
            symbols.setShortMonths(new String[] { "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"});
            DATE_FORMAT = new SimpleDateFormat("yyyy MMM dd HH:mm:ss", symbols);
            DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+3"));
        }
        
        public Instant0chanReader(InputStream in, boolean canCloudflare) {
            super(in, DATE_FORMAT, canCloudflare, ~FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS);
        }
        
        public Instant0chanReader(Reader reader, boolean canCloudflare) {
            super(reader, DATE_FORMAT, canCloudflare, ~FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS);
        }
        
        @Override
        protected void parseDate(String date) {
            date = date.replace("&#35;", "");
            date = date.replaceAll("(?:[^\\d]*)(\\d(?:.*))", "$1");
            super.parseDate(date);
        }
        
        @Override
        protected void postprocessPost(PostModel post) {
            super.postprocessPost(post);
            post.comment = RegexUtils.replaceAll(post.comment, PATTERN_TABULATION , "");
            
            Matcher matcher = PATTERN_EMBEDDED.matcher(post.comment);
            while (matcher.find()) {
                String id = matcher.group(1);
                String div = matcher.group(0).toLowerCase(Locale.US);
                String url = null;
                if (div.contains("youtube")) {
                    url = "http://www.youtube.com/watch?v=" + id;
                } else if (div.contains("vimeo")) {
                    url = "http://vimeo.com/" + id;
                } else if (div.contains("coub")) {
                    url = "http://coub.com/view/" + id;
                }
                if (url != null) {
                    AttachmentModel attachment = new AttachmentModel();
                    attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
                    attachment.size = -1;
                    attachment.path = url;
                    attachment.thumbnail = div.contains("youtube") ? ("http://img.youtube.com/vi/" + id + "/default.jpg") : null;
                    int oldCount = post.attachments != null ? post.attachments.length : 0;
                    AttachmentModel[] attachments = new AttachmentModel[oldCount + 1];
                    for (int i=0; i<oldCount; ++i) attachments[i] = post.attachments[i];
                    attachments[oldCount] = attachment;
                    post.attachments = attachments;
                }
            }
        }
        
        @Override
        protected void parseThumbnail(String imgTag) {
            if (imgTag.contains("class=\"_country_\"")) {
                int start, end;
                if ((start = imgTag.indexOf("src=\"")) != -1 && (end = imgTag.indexOf('\"', start + 5)) != -1) {
                    BadgeIconModel iconModel = new BadgeIconModel();
                    iconModel.source = imgTag.substring(start + 5, end);
                    int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
                    BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
                    for (int i=0; i<currentIconsCount; ++i) newIconsArray[i] = currentPost.icons[i];
                    newIconsArray[currentIconsCount] = iconModel;
                    currentPost.icons = newIconsArray;
                }
            } else {
                super.parseThumbnail(imgTag);
            }
        }
    }
    
}
