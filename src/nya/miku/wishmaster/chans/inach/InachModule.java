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

package nya.miku.wishmaster.chans.inach;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public class InachModule extends AbstractWakabaModule {
    private static final String TAG = "InachReader";
    
    private static final String CHAN_NAME = "inach.org";
    private static final String DOMAIN_NAME = "inach.org";
    
    private static final String PREF_AJAX_UPDATE = "PREF_AJAX_UPDATE";
    
    private static final SimpleBoardModel[] BOARDS;
    private static final String[] ATTACHMENT_FORMATS = new String[] {
        "gif", "jpg", "png", "pdf", "odf", "zip", "rar", "tar", "bz2", "7z", "doc", "odt", "mp3", "mp4", "mpeg", "flv", "swf", "avi"
    };
    static {
        BOARDS = new SimpleBoardModel[] {
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", null, true),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "2d", "Animation", null, false),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cu", "Culture", null, false),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "kn", "Knowledge", null, false),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ad", "Adult", null, true),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "in", "Inach", null, true),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pl", "Playing Games", null, false),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "Inach", null, true),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Music", null, false),
                ChanModels.obtainSimpleBoardModel(CHAN_NAME, "bl", "Blogs", null, true)
        };
    }
    
    private StringBuilder buffer = null;
    
    public InachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Inach.org";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_inach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN_NAME;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        Context context = preferenceGroup.getContext();
        CheckBoxPreference ajaxPref = new CheckBoxPreference(context);
        ajaxPref.setTitle(R.string.inach_prefs_ajax_update);
        ajaxPref.setSummary(R.string.inach_prefs_ajax_update_summary);
        ajaxPref.setKey(getSharedKey(PREF_AJAX_UPDATE));
        ajaxPref.setDefaultValue(true);
        preferenceGroup.addPreference(ajaxPref);
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    private boolean useAjax() {
        return preferences.getBoolean(getSharedKey(PREF_AJAX_UPDATE), true);
    }
    
    private JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONObject object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, false);
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return object;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new InachReader(stream);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.defaultUserName = "Аноним";
        board.timeZoneId = "GMT+3";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = true;
        board.allowDeleteFiles = true;
        board.allowNames = false;
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        board.markType = BoardModel.MARK_WAKABAMARK;
        return board;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        if (useAjax() && oldList != null) {
            int postsCount = 0;
            for (PostModel post : oldList) if (!post.deleted) ++postsCount;
            if (postsCount > 1) {
                String ajaxUrl = getUsingUrl() + "cached.pl?task=updatethread&board=" + boardName + "&thread=" + threadNumber + "&posts=" +
                        Integer.toString(postsCount - 2);
                try {
                    JSONObject json = downloadJSONObject(ajaxUrl, true, listener, task);
                    if (json == null) return oldList;
                    JSONArray array = json.getJSONArray("newposts");
                    if (array.length() == 0) throw new Exception();
                    PostModel[] newPosts = new PostModel[array.length()];
                    for (int i=0, len=array.length(); i<len; ++i)
                        newPosts[i] = mapAjaxModel(array.getJSONObject(i).getJSONObject("data"), boardName, threadNumber);
                    Arrays.sort(newPosts, new Comparator<PostModel>() {
                        @Override
                        public int compare(PostModel lhs, PostModel rhs) {
                            return Long.valueOf(Long.parseLong(lhs.number)).compareTo(Long.parseLong(rhs.number));
                        }
                    });
                    long lastPostNum = Long.parseLong(oldList[oldList.length-1].number);
                    ArrayList<PostModel> list = new ArrayList<PostModel>(Arrays.asList(oldList));
                    for (int i=0; i<newPosts.length; ++i) {
                        if (Long.parseLong(newPosts[i].number) > lastPostNum) {
                            list.add(newPosts[i]);
                        }
                    }
                    return list.toArray(new PostModel[list.size()]);
                } catch (Exception e) {
                    Logger.e(TAG, "no ajax", e);
                } finally {
                    buffer = null;
                }
            }
        }
        return super.getPostsList(boardName, threadNumber, listener, task, oldList);
    }
    
    private PostModel mapAjaxModel(JSONObject json, String boardName, String threadNum) {
        PostModel model = new PostModel();
        model.number = Long.toString(json.getLong("num"));
        model.name = json.optString("name", "");
        model.subject = json.optString("subject", "");
        model.comment = fixString(json.optString("comment", ""));
        model.email = json.optString("email", "");
        if (model.email.startsWith("mailto:")) model.email = model.email.substring(7);
        model.trip = "";
        model.sage = model.email.toLowerCase(Locale.US).contains("sage");
        try {
            model.timestamp = InachReader.DATE_FORMAT.parse(json.getString("date")).getTime();
        } catch (Exception e) {
            Logger.e(TAG, "cannot parse date; make sure you choose the right DateFormat for this chan", e);
        }
        model.parentThread = threadNum;
        
        String imagePath = json.optString("image", "");
        if (imagePath.length() != 0) {
            AttachmentModel attachment = new AttachmentModel();
            attachment.path = "/" + boardName + "/" + imagePath;
            attachment.thumbnail = json.optString("thumbnail", null);
            if (attachment.thumbnail != null) attachment.thumbnail = "/" + boardName + "/" + attachment.thumbnail;
            try {
                String size = json.optString("size");
                if (size.length() == 0) throw new Exception();
                attachment.size = Math.round(Math.round(Integer.parseInt(size) * 100 / 1024.0f) / 100.0f);
            } catch (Exception e) {
                attachment.size = -1;
            }
            try {
                String width = json.optString("width", "");
                String height = json.optString("height", "");
                if (width.length() == 0 || height.length() == 0) throw new Exception();
                attachment.width = Integer.parseInt(width);
                attachment.height = Integer.parseInt(height);
            } catch (Exception e) {
                attachment.width = -1;
                attachment.height = -1;
            }
            String pathLower = attachment.path.toLowerCase(Locale.US);
            if (pathLower.endsWith(".jpg") || pathLower.endsWith(".jpeg") || pathLower.endsWith(".png"))
                attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
            else if (pathLower.endsWith(".gif"))
                attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
            else if (pathLower.endsWith(".webm"))
                attachment.type = AttachmentModel.TYPE_VIDEO;
            else if (pathLower.endsWith(".mp3") || pathLower.endsWith(".ogg"))
                attachment.type = AttachmentModel.TYPE_AUDIO;
            else
                attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            model.attachments = new AttachmentModel[] { attachment };
        }
        
        String youtubeId = json.optString("youtube", "");
        if (youtubeId.length() != 0) {
            AttachmentModel attachment = new AttachmentModel();
            attachment.size = -1;
            attachment.type = AttachmentModel.TYPE_OTHER_NOTFILE;
            attachment.path = "http://youtube.com/watch?v=" + youtubeId;
            attachment.thumbnail = "http://img.youtube.com/vi/" + youtubeId + "/default.jpg";
            if (model.attachments == null || model.attachments.length == 0) {
                model.attachments = new AttachmentModel[] { attachment };
            } else {
                model.attachments = new AttachmentModel[] { model.attachments[0], attachment };
            }
        }
        return model;
    }
    
    private String fixString(String comment) {
        boolean inTag = false;
        if (buffer == null) buffer = new StringBuilder(); else buffer.setLength(0);
        comment = comment.replace("' style='display: table-cell; vertical-align: middle;'", "'").
                replace("<span style='display: table-cell; vertical-align: middle;' ", "<span ");
        for (int i=0; i<comment.length(); ++i) {
            char ch = comment.charAt(i);
            switch (ch) {
                case '<': inTag = true; break;
                case '>': inTag = false; break;
                case '\'': if (inTag) { buffer.append('\"'); continue; }
            }
            buffer.append(ch);
        }
        return buffer.toString();
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() +  "captcha.pl?key=" + (threadNumber == null ? "mainpage" : ("res" + threadNumber)) +
                "&dummy=" + Long.toString(Math.round(Math.random()*1000000)) + "&update=1";
        
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
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post");
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        if (model.sage) postEntityBuilder.addString("fieldsage", "on");
        postEntityBuilder.
                addString("fieldnoko", "on").
                addString("field2", model.sage ? "sage" : model.email).
                addString("field3", model.subject).
                addString("field4", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("password", model.password);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style='text-align: center'>");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br><br>", start + 31);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 31, end).trim());
                        }
                    }
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("delete", model.postNumber));
        pairs.add(new BasicNameValuePair("parent", model.threadNumber));
        pairs.add(new BasicNameValuePair("task", "delete"));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style='text-align: center'>");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br><br>", start + 31);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 31, end).trim());
                        }
                    }
                }
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
}
