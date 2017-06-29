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

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import cz.msebera.android.httpclient.Header;
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
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.CryptoUtils;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.JSONEntry;
import nya.miku.wishmaster.http.interactive.SimpleCaptchaException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.lib.base64.Base64OutputStream;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public abstract class AbstractLynxChanModule extends AbstractWakabaModule {
    private static final String TAG = "AbstractLynxChanModule";
    private static final DateFormat CHAN_DATEFORMAT;
    static {
        CHAN_DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        CHAN_DATEFORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private static final Pattern RED_TEXT_MARK_PATTERN = Pattern.compile("<span class=\"redText\">(.*?)</span>");
    private static final Pattern GREEN_TEXT_MARK_PATTERN = Pattern.compile("<span class=\"greenText\">(.*?)</span>");
    private static final Pattern REPLY_NUMBER_PATTERN = Pattern.compile("&gt&gt(\\d+)");
    protected Map<String, BoardModel> boardsMap = null;
    private Map<String, ArrayList<String>> flagsMap = null;
    private static String lastCaptchaId;
    private static String lastCaptchaAnswer;
    
    public AbstractLynxChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        String url = getUsingUrl() + "boards.js?json=1";
        List<SimpleBoardModel> list = new ArrayList<SimpleBoardModel>();
        JSONObject boardsJson = downloadJSONObject(url, (oldBoardsList != null && boardsMap != null), listener, task);
        if (boardsJson == null) return oldBoardsList;
        JSONArray boards = boardsJson.getJSONArray("boards");
        BoardModel model;
        for (int i = 0, len = boards.length(); i < len; ++i) {
            model = mapBoardModel(boards.getJSONObject(i));
            list.add(new SimpleBoardModel(model));
        }
        return list.toArray(new SimpleBoardModel[list.size()]);
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap == null) {
            boardsMap = new HashMap<String, BoardModel>();
        }
        BoardModel model;
        if (boardsMap.containsKey(shortName)) {
            model = boardsMap.get(shortName);
        } else {
            model = mapBoardModel(listener, task, getDefaultBoardModel(shortName));
            boardsMap.put(shortName, model);
        }
        return model;
    }

    private BoardModel mapBoardModel(ProgressListener listener, CancellableTask task, BoardModel model) throws Exception {
        String url = getUsingUrl() + model.boardName + "/1.json";
        JSONObject boardJson = downloadJSONObject(url, false, listener, task);
        model.boardDescription = boardJson.optString("boardName", model.boardName);
        model.attachmentsMaxCount = boardJson.optInt("maxFileCount", 5);
        model.lastPage = boardJson.optInt("pageCount", BoardModel.LAST_PAGE_UNDEFINED);
        JSONArray settingsJson = boardJson.optJSONArray("settings");
        ArrayList<String> settings = new ArrayList<String>();
        for(int i = 0, len = settingsJson.length(); i < len; ++i) settings.add(settingsJson.getString(i));
        model.allowNames = !settings.contains("forceAnonymity");
        model.allowDeletePosts = !settings.contains("blockDeletion");
        model.allowDeleteFiles = model.allowDeletePosts;
        model.requiredFileForNewThread = settings.contains("requireThreadFile");
        model.allowRandomHash = settings.contains("uniqueFiles");
        model.uniqueAttachmentNames = settings.contains("uniqueFiles");
        model.attachmentsMaxCount = settings.contains("textBoard") ? 0 : model.attachmentsMaxCount;
        try {
            JSONArray flags = boardJson.getJSONArray("flagData");
            if (flags.length() > 0) {
                String[] icons = new String[flags.length() + 1];
                icons[0] = "No flag";
                if (flagsMap == null) flagsMap = new HashMap<String, ArrayList<String>>();
                ArrayList<String> boardFlagIds = new ArrayList<String>();
                for(int i = 0, len = flags.length(); i < len; ++i) {
                    boardFlagIds.add(flags.getJSONObject(i).getString("_id"));
                    icons[i + 1] = flags.getJSONObject(i).getString("name");
                }
                flagsMap.put(model.boardName, boardFlagIds);
                model.allowIcons = true;
                model.iconDescriptions = icons;
            }
        } catch (Exception e) {}
        return model;
    }

    private BoardModel mapBoardModel(JSONObject object) {
        BoardModel model = getDefaultBoardModel(object.optString("boardUri"));
        model.boardDescription = object.optString("boardName", model.boardName);
        try {
            String settings = object.getJSONArray("specialSettings").toString();
            model.nsfw = !settings.contains("\"sfw\"");
        } catch (Exception e) {
            model.nsfw = true;
        }
        return model;
    }

    private BoardModel getDefaultBoardModel(String shortName) {
        BoardModel board = new BoardModel();
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
        board.allowCustomMark = true;
        board.customMarkDescription = "Spoiler";
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = null;
        board.markType = BoardModel.MARK_INFINITY;
        board.firstPage = 1;
        board.lastPage = BoardModel.LAST_PAGE_UNDEFINED;
        board.catalogAllowed = true;
        board.boardName = shortName;
        board.bumpLimit = 500;
        board.chan = getChanName();
        return board;
    }

    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        String url = getUsingUrl() + boardName + "/" + page + ".json";
        JSONObject response = downloadJSONObject(url, oldList != null, listener, task);
        if (response == null) return oldList;
        JSONArray threads = response.getJSONArray("threads");
        ThreadModel[] result = new ThreadModel[threads.length()];
        for (int i = 0, len = threads.length(); i < len; ++i) {
            JSONArray posts = threads.getJSONObject(i).getJSONArray("posts");
            JSONObject thread = threads.getJSONObject(i);
            ThreadModel curThread = mapThreadModel(thread);
            curThread.posts = new PostModel[posts.length() + 1];
            curThread.postsCount += posts.length();
            curThread.posts[0] = mapPostModel(thread);
            curThread.threadNumber = curThread.posts[0].number;
            curThread.posts[0].parentThread = curThread.threadNumber;
            for (int j = 0, plen = posts.length(); j < plen; ++j) {
                curThread.posts[j + 1] = mapPostModel(posts.getJSONObject(j));
                curThread.posts[j + 1].parentThread = curThread.threadNumber;
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
        PostModel[] result = new PostModel[posts.length() + 1];
        result[0] = mapPostModel(response);
        for (int i = 0, len = posts.length(); i < len; ++i) {
            result[i + 1] = mapPostModel(posts.getJSONObject(i));
            result[i + 1].parentThread = result[0].number;
        }
        if (oldList != null) {
            result = ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(result));
        }
        return result;
    }
    
    @Override
    public ThreadModel[] getCatalog(
            String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList) throws Exception {
        String url = getUsingUrl() + boardName + "/catalog.json";
        JSONArray response = downloadJSONArray(url, oldList != null, listener, task);
        if (response == null) return oldList;
        ThreadModel[] result = new ThreadModel[response.length()];
        for (int i = 0, len = response.length(); i < len; ++i) {
            result[i] = mapCatalogThreadModel(response.getJSONObject(i));
        }
        return result;
    }
    
    private ThreadModel mapCatalogThreadModel(JSONObject object) {
        ThreadModel model = mapThreadModel(object);
        model.postsCount = object.optInt("postCount");
        model.attachmentsCount = object.optInt("fileCount");
        PostModel post = mapPostModel(object);
        post.number = model.threadNumber;
        post.parentThread = model.threadNumber;
        String thumb = object.optString("thumb", "");
        if (thumb.length() > 0) {
            AttachmentModel attachment = new AttachmentModel();
            attachment.thumbnail = thumb;
            attachment.path = thumb;
            attachment.height = -1;
            attachment.width = -1;
            attachment.size = -1;
            post.attachments = new AttachmentModel[] { attachment };
        }
        model.posts = new PostModel[] { post };
        return model;
    }
    
    private ThreadModel mapThreadModel(JSONObject object) {
        ThreadModel model = new ThreadModel();
        model.threadNumber = Integer.toString(object.optInt("threadId"));
        model.isSticky = object.optBoolean("pinned", false);
        model.isClosed = object.optBoolean("locked", false);
        model.isCyclical = object.optBoolean("cyclic", false);
        model.postsCount = object.optInt("ommitedPosts", 0) + 1;
        return model;
    }

    private PostModel mapPostModel(JSONObject object) {
        PostModel model = new PostModel();
        try {
            model.timestamp = CHAN_DATEFORMAT.parse(object.optString("creation")).getTime();
        } catch (ParseException e) {
            Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
        }
        model.name = StringEscapeUtils.unescapeHtml4(object.optString("name"));
        model.email = StringEscapeUtils.unescapeHtml4(object.optString("email"));
        model.subject = StringEscapeUtils.unescapeHtml4(object.optString("subject"));
        model.comment = object.optString("markdown", object.optString("message"));
        model.comment = RegexUtils.replaceAll(model.comment, RED_TEXT_MARK_PATTERN, "<font color=\"red\"><b>$1</b></font>");
        model.comment = RegexUtils.replaceAll(model.comment, GREEN_TEXT_MARK_PATTERN, "<span class=\"quote\">$1</span>");
        model.comment = RegexUtils.replaceAll(model.comment, REPLY_NUMBER_PATTERN, "&gt;&gt;$1");
        String banMessage = object.optString("banMessage", "");
        if (!banMessage.equals(""))
            model.comment = model.comment + "<br/><em><font color=\"red\">("+banMessage+")</font></em>";
        String flag = object.optString("flag", "");
        if (!flag.equals("")) {
            BadgeIconModel icon = new BadgeIconModel();
            icon.description = object.optString("flagName");
            icon.source = flag;
            model.icons = new BadgeIconModel[] { icon };
        }
        int post_number = object.optInt("postId", -1);
        model.number = post_number == -1 ? null : Integer.toString(post_number);
        if (model.number == null) {
            int thread_number = object.optInt("threadId", -1);
            model.number = thread_number == -1 ? "" : Integer.toString(thread_number);
        }
        String signedRole = object.optString("signedRole", "");
        if (!signedRole.equals("")) model.trip = "##" + signedRole;
        String id = object.optString("id", "");
        model.sage = id.equalsIgnoreCase("Heaven") || model.email.toLowerCase(Locale.US).contains("sage");
        if (!id.equals("")) {
            model.name += (" ID:" + id);
            model.color = CryptoUtils.hashIdColor(id);
        }
        JSONArray files = object.optJSONArray("files");
        if (files != null) {
            model.attachments = new AttachmentModel[files.length()];
            for (int i = 0, len = files.length(); i < len; ++i) {
                model.attachments[i] = mapAttachment(files.getJSONObject(i));
            }
        }
        return model;
    }

    private AttachmentModel mapAttachment(JSONObject object) {
        AttachmentModel model = new AttachmentModel();
        model.originalName = object.optString("originalName");
        model.thumbnail = object.optString("thumb");
        model.path = object.optString("path");
        model.height = object.optInt("height", -1);
        model.width = object.optInt("width", -1);
        model.size = object.optInt("size", -1) / 1024;
        model.size = model.size < 0 ? -1 : model.size;
        String mime = object.optString("mime");
        if (mime.startsWith("image/")) {
            model.type = AttachmentModel.TYPE_IMAGE_STATIC;
            if (mime.contains("gif")) model.type = AttachmentModel.TYPE_IMAGE_GIF;
            if (mime.contains("svg")) model.type = AttachmentModel.TYPE_IMAGE_SVG;
        } else if (mime.startsWith("audio/")) {
            model.type = AttachmentModel.TYPE_AUDIO;
        } else if (mime.startsWith("video/")) {
            model.type = AttachmentModel.TYPE_VIDEO;
        } else {
            model.type = AttachmentModel.TYPE_OTHER_FILE;
        }
        return model;
    }

    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        if (model.type == UrlPageModel.TYPE_CATALOGPAGE)
            return getUsingUrl() + model.boardName + "/catalog.html";
        if (model.type == UrlPageModel.TYPE_BOARDPAGE && model.boardPage == 1)
            return (getUsingUrl() + model.boardName + "/");
        String url = WakabaUtils.buildUrl(model, getUsingUrl());
        return url;
    }

    //TODO: parse case-sensitive board names
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
            } catch (Exception e) {
            }
        }
        UrlPageModel model = WakabaUtils.parseUrlPath(urlPath, getChanName());
        if ((model.type == UrlPageModel.TYPE_BOARDPAGE) && (model.boardPage < 1)) {
            model.boardPage = 1;
        }
        return model;
    }

    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("?/")) url = url.substring(1);
        return super.fixRelativeUrl(url);
    }

    protected CaptchaModel downloadCaptcha(String captchaUrl, ProgressListener listener, CancellableTask task) throws Exception {
        Bitmap captchaBitmap = null;
        HttpRequestModel requestModel = HttpRequestModel.DEFAULT_GET;
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, requestModel, httpClient, listener, task);
        String captchaId = null;
        for (Header header : responseModel.headers) {
            if (header != null && "Set-Cookie".equalsIgnoreCase(header.getName())) {
                String cookie = header.getValue();
                if (cookie.contains("captchaid")) {
                    try {
                        captchaId = cookie.split(";")[0].split("=")[1];
                    } catch (Exception e) {
                    }
                }
                if (captchaId != null) break;
            }
        }
        try {
            InputStream imageStream = responseModel.stream;
            captchaBitmap = BitmapFactory.decodeStream(imageStream);
        } finally {
            responseModel.release();
        }
        CaptchaModel captchaModel = new CaptchaModel();
        captchaModel.type = CaptchaModel.TYPE_NORMAL;
        captchaModel.bitmap = captchaBitmap;
        lastCaptchaId = captchaId;
        return captchaModel;
    }

    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "captcha.js?d=" + Double.toString(Math.random());
        return downloadCaptcha(captchaUrl, listener, task);
    }

    private String computeFileMD5(File file) throws NoSuchAlgorithmException, IOException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.reset();

        FileInputStream fis = new FileInputStream(file);
        byte[] byteArray = new byte[1024];
        int bytesCount;
        while ((bytesCount = fis.read(byteArray)) != -1) {
            messageDigest.update(byteArray, 0, bytesCount);
        }
        fis.close();

        byte[] digest = messageDigest.digest();
        BigInteger bigInt = new BigInteger(1, digest);
        String md5Hex = bigInt.toString(16);
        if (md5Hex.length() < 32) {
            char[] head = new char[32 - md5Hex.length()];
            Arrays.fill(head, '0');
            md5Hex = new StringBuilder(32).append(head).append(md5Hex).toString();
        }
        return md5Hex;
    }

    private String checkFileIdentifier(File file, String mime, ProgressListener listener, CancellableTask task) {
        String hash;
        try {
            hash = computeFileMD5(file);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        String identifier = hash + "-" + mime.replace("/", "");
        String url = getUsingUrl() + "checkFileIdentifier.js?identifier=" + identifier;
        String response = "";
        try {
            response = HttpStreamer.getInstance().getStringFromUrl(url, null, httpClient, listener, task, false);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        if (response.contains("true")) return hash;
        return null;
    }

    private String base64EncodeFile(File file) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        Base64OutputStream b64os = new Base64OutputStream(os, Base64.NO_WRAP);
        FileInputStream fis = new FileInputStream(file);
        IOUtils.copyStream(fis, b64os);
        return os.toString();
    }

    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + ".api/" + (model.threadNumber == null ? "newThread" : "replyThread");

        JSONObject jsonPayload = new JSONObject();
        JSONObject jsonParameters = new JSONObject();
        jsonPayload.put("captchaId", lastCaptchaId);
        jsonParameters.put("name", model.name);
        jsonParameters.put("password", model.password);
        jsonParameters.put("subject", model.subject);
        jsonParameters.put("message", model.comment);
        jsonParameters.put("boardUri", model.boardName);
        jsonParameters.put("email", model.sage ? "sage" : model.email);
        if (model.threadNumber != null)
            jsonParameters.put("threadId", model.threadNumber);
        if (model.captchaAnswer != null && model.captchaAnswer.length() > 0)
            jsonParameters.put("captcha", model.captchaAnswer);
        if (model.icon > 0)
            jsonParameters.put("flag", flagsMap.get(model.boardName).get(model.icon - 1));
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        if (model.attachments != null && model.attachments.length > 0) {
            JSONArray files = new JSONArray();
            for (int i = 0; i < model.attachments.length; ++i) {
                String ext = MimeTypeMap.getFileExtensionFromUrl(model.attachments[i].toURI().toString());
                String mime = mimeTypeMap.getMimeTypeFromExtension(ext);
                String md5 = checkFileIdentifier(model.attachments[i], mime, listener, task);
                JSONObject file = new JSONObject();
                file.put("name", model.attachments[i].getName());
                if (md5 != null) {
                    file.put("md5", md5);
                    file.put("mime", mime);
                } else {
                    file.put("content", "data:" + mime + ";base64," + base64EncodeFile(model.attachments[i]));
                }
                file.put("spoiler", model.custommark);
                files.put(file);
            }
            jsonParameters.put("files", files);
        }
        jsonPayload.put("parameters", jsonParameters);
        JSONEntry payload = new JSONEntry(jsonPayload);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(payload).setNoRedirect(true).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        lastCaptchaId = null;
        JSONObject result = new JSONObject(response);
        String status = result.optString("status");
        if ("ok".equals(status)) {
            UrlPageModel urlPageModel = new UrlPageModel();
            urlPageModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlPageModel.chanName = getChanName();
            urlPageModel.boardName = model.boardName;
            urlPageModel.threadNumber = model.threadNumber;
            if (model.threadNumber == null) {
                urlPageModel.threadNumber = Integer.toString(result.optInt("data"));
            } else {
                urlPageModel.postNumber = Integer.toString(result.optInt("data"));
            }
            return buildUrl(urlPageModel);
        } else if (status.contains("error") || status.contains("blank")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        throw new Exception("Unknown Error");
    }

    @Override
    public String deletePost(DeletePostModel model, final ProgressListener listener, final CancellableTask task) throws Exception {
        String url = getUsingUrl() + ".api/" + "deleteContent";
        
        if (lastCaptchaAnswer == null) {
            throw new SimpleCaptchaException() {
                private static final long serialVersionUID = 1L;
                @Override
                protected Bitmap getNewCaptcha() throws Exception {
                    return AbstractLynxChanModule.this.getNewCaptcha("", "", listener, task).bitmap;
                }
                @Override
                protected void storeResponse(String response) {
                    lastCaptchaAnswer = response;
                }
            };
        }
        
        JSONObject jsonPayload = new JSONObject();
        JSONObject jsonParameters = new JSONObject();
        jsonPayload.put("captchaId", lastCaptchaId);
        jsonParameters.put("password", model.password);
        if (lastCaptchaAnswer != null && lastCaptchaAnswer.length() > 0)
            jsonParameters.put("captcha", lastCaptchaAnswer);
        jsonParameters.put("deleteMedia", true);
        if (model.onlyFiles) {
            jsonParameters.put("deleteUploads", true);
        }
        JSONArray jsonArray = new JSONArray();
        JSONObject post = new JSONObject();
        post.put("board", model.boardName);
        post.put("thread", model.threadNumber);
        if (!model.postNumber.equals(model.threadNumber)) post.put("post", model.postNumber);
        jsonParameters.put("postings", jsonArray);
        jsonPayload.put("parameters", jsonParameters);
        JSONEntry payload = new JSONEntry(jsonPayload);
        HttpRequestModel request = HttpRequestModel.builder().setPOST(payload).setNoRedirect(true).build();
        String response = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, null, task, true);
        lastCaptchaId = null;
        lastCaptchaAnswer = null;
        JSONObject result = new JSONObject(response);
        if (result.optString("status").equals("error")) {
            String errorMessage = result.optString("data");
            if (errorMessage.length() > 0) {
                throw new Exception(errorMessage);
            }
        }
        return null;
    }
    
}
