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

package nya.miku.wishmaster.api;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.tuple.Pair;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import android.content.SharedPreferences;
import android.content.res.Resources;

public abstract class AbstractVichanModule extends AbstractWakabaModule {
    private static final Pattern ATTACHMENT_EMBEDDED_LINK = Pattern.compile("<a[^>]*href=\"([^\">]*)\"[^>]*>");
    private static final Pattern ATTACHMENT_EMBEDDED_THUMB = Pattern.compile("<img[^>]*src=\"([^\">]*)\"[^>]*>");
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
    
    private static final String[] ATTACHMENT_KEYS = new String[] { "file", "file2", "file3", "file4", "file5" };
    
    public AbstractVichanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.timeZoneId = "UTC";
        board.defaultUserName = "Anonymous";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = true;
        board.allowDeleteFiles = true;
        board.allowReport = BoardModel.REPORT_WITH_COMMENT;
        board.allowNames = true;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = null;
        board.markType = BoardModel.MARK_BBCODE;
        board.firstPage = 1;
        board.catalogAllowed = true;
        return board;
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/" + (page-1) + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray threads = response.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i=0, len=threads.length(); i<len; ++i) {
            JSONArray posts = threads.getJSONObject(i).getJSONArray("posts");
            JSONObject op = posts.getJSONObject(0);
            ThreadModel curThread = mapThreadModel(op, boardName);
            curThread.posts = new PostModel[posts.length()];
            for (int j=0, plen=posts.length(); j<plen; ++j) {
                curThread.posts[j] = mapPostModel(posts.getJSONObject(j), boardName);
            }
            result[i] = curThread;
        }
        return result;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/res/" + threadNumber + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray posts = response.getJSONArray("posts");
        PostModel[] result = new PostModel[posts.length()];
        for (int i=0, len=posts.length(); i<len; ++i) {
            result[i] = mapPostModel(posts.getJSONObject(i), boardName);
        }
        if (oldList != null) {
            result = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(result));
        }
        return result;
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList;
        List<ThreadModel> threads = new ArrayList<>();
        for (int i=0, len=response.length(); i<len; ++i) {
            JSONArray curArray = response.getJSONObject(i).getJSONArray("threads");
            for (int j=0, clen=curArray.length(); j<clen; ++j) {
                JSONObject curThreadJson = curArray.getJSONObject(j);
                ThreadModel curThread = mapThreadModel(curThreadJson, boardName);
                curThread.posts = new PostModel[] { mapPostModel(curThreadJson, boardName) };
                threads.add(curThread);
            }
        }
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    protected ThreadModel mapThreadModel(JSONObject opPost, String boardName) {
        ThreadModel curThread = new ThreadModel();
        curThread.threadNumber = Long.toString(opPost.getLong("no"));
        curThread.postsCount = opPost.optInt("replies", -2) + 1;
        curThread.attachmentsCount = opPost.optInt("images", -2) + 1;
        if (curThread.attachmentsCount >= 0) curThread.attachmentsCount += opPost.optInt("omitted_images", 0);
        curThread.isSticky = opPost.optInt("sticky") == 1;
        curThread.isClosed = opPost.optInt("locked") == 1;
        return curThread;
    }
    
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = new PostModel();
        model.number = Long.toString(object.getLong("no"));
        model.name = StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlSpanTags(object.optString("name", "Anonymous")));
        model.subject = StringEscapeUtils.unescapeHtml4(object.optString("sub", ""));
        model.comment = object.optString("com", "");
        model.email = object.optString("email", "");
        model.trip = object.optString("trip", "");
        String capcode = object.optString("capcode", "none");
        if (!capcode.equals("none")) model.trip += "##"+capcode;
        String countryIcon = object.optString("country", "");
        if (!countryIcon.equals("")) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.source = "/static/flags/" + countryIcon.toLowerCase(Locale.US) + ".png";
            icon.description = object.optString("country_name");
            model.icons = new BadgeIconModel[] { icon };
        }
        model.op = false;
        String id = object.optString("id", "");
        model.sage = id.equalsIgnoreCase("Heaven") || model.email.toLowerCase(Locale.US).contains("sage");
        if (!id.equals("")) model.name += (" ID:" + id);
        if (!id.equals("") && !id.equalsIgnoreCase("Heaven")) model.color = CryptoUtils.hashIdColor(id);
        model.timestamp = object.getLong("time") * 1000;
        model.parentThread = object.optString("resto", "0");
        if (model.parentThread.equals("0")) model.parentThread = model.number;
        
        List<AttachmentModel> attachments = null;
        boolean isSpoiler = object.optInt("spoiler") == 1;
        AttachmentModel rootAttachment = mapAttachment(object, boardName, isSpoiler);
        if (rootAttachment != null) {
            attachments = new ArrayList<>();
            attachments.add(rootAttachment);
            JSONArray extraFiles = object.optJSONArray("extra_files");
            if (extraFiles != null && extraFiles.length() != 0) {
                for (int i=0, len=extraFiles.length(); i<len; ++i) {
                    AttachmentModel attachment = mapAttachment(extraFiles.getJSONObject(i), boardName, isSpoiler);
                    if (attachment != null) attachments.add(attachment);
                }
            }
        }
        String embed = object.optString("embed", "");
        if (!embed.equals("")) {
            AttachmentModel embedAttachment = new AttachmentModel();
            embedAttachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            Matcher linkMatcher = ATTACHMENT_EMBEDDED_LINK.matcher(embed);
            if (linkMatcher.find()) {
                embedAttachment.path = linkMatcher.group(1);
                if (embedAttachment.path.startsWith("//")) embedAttachment.path = (useHttps() ? "https:" : "http:") + embedAttachment.path;
                Matcher thumbMatcher = ATTACHMENT_EMBEDDED_THUMB.matcher(embed);
                if (thumbMatcher.find()) {
                    embedAttachment.thumbnail = thumbMatcher.group(1);
                    if (embedAttachment.thumbnail.startsWith("//")) embedAttachment.thumbnail = (useHttps() ? "https:" : "http:") + embedAttachment.thumbnail;
                }
                embedAttachment.isSpoiler = isSpoiler;
                embedAttachment.size = -1;
                if (attachments != null) attachments.add(embedAttachment); else attachments = Collections.singletonList(embedAttachment);
            }
        }
        if (attachments != null) model.attachments = attachments.toArray(new AttachmentModel[attachments.size()]);
        return model;
    }
    
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        String ext = object.optString("ext", "");
        if (!ext.isEmpty()) {
            AttachmentModel attachment = new AttachmentModel();
            ext = ext.toLowerCase(Locale.US);
            switch (ext) {
                case ".jpeg":
                case ".jpg":
                case ".png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case ".gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case ".svg":
                case ".svgz":
                    attachment.type = AttachmentModel.TYPE_IMAGE_SVG;
                    break;
                case ".mp3":
                case ".ogg":
                    attachment.type = AttachmentModel.TYPE_AUDIO;
                    break;
                case ".webm":
                case ".mp4":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.size = object.optInt("fsize", -1);
            if (attachment.size > 0) attachment.size = Math.round(attachment.size / 1024f);
            attachment.width = object.optInt("w", -1);
            attachment.height = object.optInt("h", -1);
            attachment.originalName = object.optString("filename", "") + ext;
            attachment.isSpoiler = isSpoiler;
            String tim = object.optString("tim", "");
            if (tim.length() > 0) {
                attachment.thumbnail = isSpoiler || attachment.type == AttachmentModel.TYPE_AUDIO ? null :
                        ("/" + boardName + "/thumb/" + tim + ".jpg");
                attachment.path = "/" + boardName + "/src/" + tim + ext;
                return attachment;
            }
        }
        return null;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = getChanName();
        urlModel.boardName = model.boardName;
        if (model.threadNumber == null) {
            urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
            urlModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
        } else {
            urlModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlModel.threadNumber = model.threadNumber;
        }
        String referer = buildUrl(urlModel);
        List<Pair<String, String>> fields = VichanAntiBot.getFormValues(referer, task, httpClient);
        
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().
                setCharset(Charset.forName("UTF-8")).setDelegates(listener, task);
        for (Pair<String, String> pair : fields) {
            if (pair.getKey().equals("spoiler") && !model.custommark) continue;
            String val;
            switch (pair.getKey()) {
                case "name": val = model.name; break;
                case "email": val = getSendPostEmail(model); break;
                case "subject": val = model.subject; break;
                case "body": val = model.comment; break;
                case "password": val = model.password; break;
                case "spoiler": val = "on"; break;
                default: val = pair.getValue();
            }
            if (pair.getKey().equals("file")) {
                if (model.attachments != null && model.attachments.length > 0) {
                    for (int i=0; i<model.attachments.length && i<ATTACHMENT_KEYS.length; ++i) {
                        postEntityBuilder.addFile(ATTACHMENT_KEYS[i], model.attachments[i], model.randomHash);
                    }
                } else {
                    postEntityBuilder.addPart(pair.getKey(), new ByteArrayBody(new byte[0], ""));
                }
            } else {
                postEntityBuilder.addString(pair.getKey(), val);
            }
        }
        
        String url = getUsingUrl() + "post.php";
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, referer) };
        HttpRequestModel request =
                HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, listener, task);
            if (response.statusCode == 200 || response.statusCode == 400) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
            } else if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "post.php";
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("delete_" + model.postNumber, "on"));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("file", "on"));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("delete", getDeleteFormValue(model)));
        pairs.add(new BasicNameValuePair("reason", ""));
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = getChanName();
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (response.statusCode == 200 || response.statusCode == 400 || response.statusCode == 303) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
                return null;
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    @Override
    public String reportPost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "post.php";
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("delete_" + model.postNumber, "on"));
        pairs.add(new BasicNameValuePair("password", ""));
        pairs.add(new BasicNameValuePair("reason", model.reportReason));
        pairs.add(new BasicNameValuePair("report", getReportFormValue(model)));
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = getChanName();
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (response.statusCode == 200 || response.statusCode == 400 || response.statusCode == 303) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1));
                return null;
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
    
    protected String getSendPostEmail(SendPostModel model) {
        return model.sage ? "sage" : model.email;
    }
    
    protected String getDeleteFormValue(DeletePostModel model) {
        return "Delete";
    }
    
    protected String getReportFormValue(DeletePostModel model) {
        return "Report";
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        if (model.type == UrlPageModel.TYPE_CATALOGPAGE) return getUsingUrl() + model.boardName + "/catalog.html";
        if (model.type == UrlPageModel.TYPE_BOARDPAGE && model.boardPage == 1) return (getUsingUrl() + model.boardName + "/");
        return WakabaUtils.buildUrl(model, getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath = UrlPathUtils.getUrlPath(url, getAllDomains());
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        if (url.contains("/catalog.html")) {
            try {
                int index = url.indexOf("/catalog.html");
                String left = url.substring(0, index);
                UrlPageModel model = new UrlPageModel();
                model.chanName = getChanName();
                model.type = UrlPageModel.TYPE_CATALOGPAGE;
                model.boardName = left.substring(left.lastIndexOf('/') + 1);
                model.catalogType = 0;
                return model;
            } catch (Exception e) {}
        }
        UrlPageModel model = WakabaUtils.parseUrlPath(urlPath, getChanName());
        if (model.type == UrlPageModel.TYPE_BOARDPAGE && model.boardPage == 0) model.boardPage = 1;
        return model;
    }
    
    @Override
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            super.downloadFile(url, out, listener, task);
        } catch (HttpWrongStatusCodeException e) {
            if (e.getStatusCode() == 404 && url.contains("/thumb/")) {
                String ext = url.substring(url.lastIndexOf(".")+1).toLowerCase();
                String fileName = url.substring(0, url.lastIndexOf("."));
                // jpg -> png -> gif -> jpeg -> throw exception
                switch (ext) {
                    case "jpg":
                        downloadFile(fileName + ".png", out, listener, task);
                        break;
                    case "png":
                        downloadFile(fileName + ".gif", out, listener, task);
                        break;
                    case "gif":
                        downloadFile(fileName + ".jpeg", out, listener, task);
                        break;
                    default:
                        throw e;
                }
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("?/")) url = url.substring(1);
        return super.fixRelativeUrl(url); 
    }
    
    protected static class VichanAntiBot {
        public static List<Pair<String, String>> getFormValues(String url, CancellableTask task, HttpClient httpClient) throws Exception {
            return getFormValues(url, HttpRequestModel.DEFAULT_GET, task, httpClient, "<form name=\"post\"", "</form>");
        }
        
        public static List<Pair<String, String>> getFormValues(String url, HttpRequestModel requestModel, CancellableTask task, HttpClient client,
                String startForm, String endForm) throws Exception {
            VichanAntiBot reader = null;
            HttpRequestModel request = requestModel;
            HttpResponseModel response = null;
            try {
                response = HttpStreamer.getInstance().getFromUrl(url, request, client, null, task);
                reader = new VichanAntiBot(response.stream, startForm, endForm);
                return reader.readForm();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {}
                }
                if (response != null) response.release();
            } 
        }
        
        private StringBuilder readBuffer = new StringBuilder();
        private List<Pair<String, String>> result = null;
        
        private String currentName = null;
        private String currentValue = null;
        private boolean currentTextarea = false;
        private boolean currentReading = false;
        
        private final char[] start;
        private final char[][] filters;
        private final Reader in;
        
        private static final int FILTER_INPUT_OPEN = 0;
        private static final int FILTER_TEXTAREA_OPEN = 1;
        private static final int FILTER_NAME_OPEN = 2;
        private static final int FILTER_VALUE_OPEN = 3;
        private static final int FILTER_TAG_CLOSE = 4;
        private static final int FILTER_TAG_BEFORE_CLOSE = 5;
        
        private VichanAntiBot(InputStream in, String start, String end) {
            this.start = start.toCharArray();
            this.filters = new char[][] {
                    "<input".toCharArray(),
                    "<textarea".toCharArray(),
                    "name=\"".toCharArray(),
                    "value=\"".toCharArray(),
                    ">".toCharArray(),
                    "/".toCharArray(),
                    end.toCharArray()
            };
            this.in = new BufferedReader(new InputStreamReader(in));
        }
        
        private List<Pair<String, String>> readForm() throws IOException {
            result = new ArrayList<>();
            skipUntilSequence(start);
            int filtersCount = filters.length;
            int[] pos = new int[filtersCount];
            int[] len = new int[filtersCount];
            for (int i=0; i<filtersCount; ++i) len[i] = filters[i].length;
            
            int curChar;
            while ((curChar = in.read()) != -1) {
                for (int i=0; i<filtersCount; ++i) {
                    if (curChar == filters[i][pos[i]]) {
                        ++pos[i];
                        if (pos[i] == len[i]) {
                            if (i == filtersCount - 1) {
                                return result;
                            }
                            handleFilter(i);
                            pos[i] = 0;
                        }
                    } else {
                        if (pos[i] != 0) pos[i] = curChar == filters[i][0] ? 1 : 0;
                    }
                }
            }
            return result;
        }
        
        private void handleFilter(int i) throws IOException {
            switch (i) {
                case FILTER_INPUT_OPEN:
                    currentReading = true;
                    currentTextarea = false;
                    break;
                case FILTER_TEXTAREA_OPEN:
                    currentReading = true;
                    currentTextarea = true;
                    break;
                case FILTER_NAME_OPEN:
                    currentName = StringEscapeUtils.unescapeHtml4(readUntilSequence("\"".toCharArray()));
                    break;
                case FILTER_VALUE_OPEN:
                    currentValue = StringEscapeUtils.unescapeHtml4(readUntilSequence("\"".toCharArray()));
                    break;
                case FILTER_TAG_CLOSE:
                    if (currentTextarea) {
                        currentValue = StringEscapeUtils.unescapeHtml4(readUntilSequence("<".toCharArray()));
                    }
                    if (currentReading && currentName != null) result.add(Pair.of(currentName, currentValue != null ? currentValue : ""));
                    currentName = null;
                    currentValue = null;
                    currentReading = false;
                    currentTextarea = false;
                    break;
                case FILTER_TAG_BEFORE_CLOSE: // <textarea ..... />
                    currentTextarea = false;
                    break;
            }
        }
        
        private void skipUntilSequence(char[] sequence) throws IOException {
            int len = sequence.length;
            if (len == 0) return;
            int pos = 0;
            int curChar;
            while ((curChar = in.read()) != -1) {
                if (curChar == sequence[pos]) {
                    ++pos;
                    if (pos == len) break;
                } else {
                    if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
                }
            }
        }
        
        private String readUntilSequence(char[] sequence) throws IOException {
            int len = sequence.length;
            if (len == 0) return "";
            readBuffer.setLength(0);
            int pos = 0;
            int curChar;
            while ((curChar = in.read()) != -1) {
                readBuffer.append((char) curChar);
                if (curChar == sequence[pos]) {
                    ++pos;
                    if (pos == len) break;
                } else {
                    if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
                }
            }
            int buflen = readBuffer.length();
            if (buflen >= len) {
                readBuffer.setLength(buflen - len);
                return readBuffer.toString();
            } else {
                return "";
            }
        }
        
        public void close() throws IOException {
            in.close();
        }
    }
}
