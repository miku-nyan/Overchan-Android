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

package nya.miku.wishmaster.chans.dvachnet;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicNameValuePair;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.chans.AbstractWakabaModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.cloudflare.CloudflareException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;

@SuppressWarnings("deprecation") // https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class DvachnetModule extends AbstractWakabaModule {
    
    static final String CHAN_NAME = "dva-ch.net";
    private static final String DOMAIN = "dva-ch.net";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Бред", "Обсуждения", false)
    };
    
    private Map<String, BoardModel> boardsMap = new HashMap<>();
    private String captchaId = "";
    
    public DvachnetModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Два.ч (dva-ch.net)";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_dvach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    protected JSONObject downloadJSONObject(String url, boolean checkIfModidied, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModidied).build();
        JSONObject object = null;
        try {
            object = HttpStreamer.getInstance().getJSONObjectFromUrl(url, rqModel, httpClient, listener, task, canCloudflare());
        } catch (HttpWrongStatusCodeException e) {
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
            throw e;
        }
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        if (listener != null) listener.setIndeterminate();
        return object;
    }
    
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        if (boardsMap.containsKey(shortName)) return boardsMap.get(shortName);
        
        try {
            JSONObject json = downloadJSONObject(getUsingUrl() + shortName + "/index.json", false, listener, task);
            SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(shortName);
            if (simpleModel == null) simpleModel = ChanModels.obtainSimpleBoardModel(CHAN_NAME, shortName, shortName, "", false);
            BoardModel board = DvachnetJsonMapper.mapBoardModel(json, simpleModel);
            boardsMap.put(shortName, board);
            return board;
        } catch (Exception e) {
            return DvachnetJsonMapper.getDefaultBoardModel(shortName, shortName, "", false);
        }
    }
    
    @Override
    public ThreadModel[] getThreadsList(String boardName, int page, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        JSONObject json = downloadJSONObject(getUsingUrl() + boardName + "/" + (page == 0 ? "index" : Integer.toString(page)) + ".json",
                oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(boardName);
            if (simpleModel == null) simpleModel = ChanModels.obtainSimpleBoardModel(CHAN_NAME, boardName, boardName, "", false);
            BoardModel board = DvachnetJsonMapper.mapBoardModel(json, simpleModel);
            boardsMap.put(boardName, board);
        } catch (Exception e) {}
        
        JSONArray threadsJson = json.getJSONArray("threads");
        ThreadModel[] threads = new ThreadModel[threadsJson.length()];
        for (int i=0; i<threads.length; ++i) {
            JSONObject thread = threadsJson.getJSONObject(i);
            threads[i] = new ThreadModel();
            threads[i].postsCount = thread.optInt("posts_count", -1);
            threads[i].attachmentsCount = thread.optInt("files_count", -1);
            JSONArray postsJson = thread.getJSONArray("posts");
            threads[i].posts = new PostModel[postsJson.length()];
            for (int j=0; j<threads[i].posts.length; ++j) {
                threads[i].posts[j] = DvachnetJsonMapper.mapPostModel(postsJson.getJSONObject(j));
            }
            if (threads[i].postsCount != -1) threads[i].postsCount += threads[i].posts.length;
            if (threads[i].attachmentsCount != -1) {
                int attachments = 0;
                for (PostModel post : threads[i].posts) attachments += (post.attachments != null ? post.attachments.length : 0);
                threads[i].attachmentsCount += attachments;
            }
            threads[i].isSticky = postsJson.getJSONObject(0).optInt("sticky") == 1;
            threads[i].isClosed = postsJson.getJSONObject(0).optInt("closed") == 1;
        }
        return threads;
    }
    
    @Override
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        JSONObject json = downloadJSONObject(getUsingUrl() + boardName + "/res/" + threadNumber + ".json",
                oldList != null, listener, task);
        if (json == null) return oldList;
        try {
            SimpleBoardModel simpleModel = getBoardsMap(listener, task).get(boardName);
            if (simpleModel == null) simpleModel = ChanModels.obtainSimpleBoardModel(CHAN_NAME, boardName, boardName, "", false);
            BoardModel board = DvachnetJsonMapper.mapBoardModel(json, simpleModel);
            boardsMap.put(boardName, board);
        } catch (Exception e) {}
        
        JSONArray postsJson = json.getJSONArray("threads").getJSONObject(0).getJSONArray("posts");
        PostModel[] posts = new PostModel[postsJson.length()];
        for (int i=0; i<posts.length; ++i) {
            posts[i] = DvachnetJsonMapper.mapPostModel(postsJson.getJSONObject(i));
        }
        
        return oldList != null ? ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(posts)) : posts;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        HttpRequestModel get = HttpRequestModel.builder().setGET().build();
        captchaId = HttpStreamer.getInstance().getStringFromUrl(getUsingUrl() + "cgi/captcha?task=get_id", get, httpClient, listener, task, false);
        String captchaUrl = getUsingUrl() + "cgi/captcha?task=get_image&id=" + captchaId;
        Bitmap captchaBitmap = null;
        HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(captchaUrl, get, httpClient, listener, task);
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
        String url = getUsingUrl() + "cgi/posting";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post").
                addString("board", model.boardName).
                addString("parent", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("email", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("comment", model.comment).
                addString("captcha_id", captchaId).
                addString("captcha_value", model.captchaAnswer).
                addString("password", model.password);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("image", model.attachments[0], model.randomHash);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        if (header.getValue() == null || header.getValue().trim().length() == 0) return null;
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("</h1>", start + 31);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 31, end).trim());
                        }
                    }
                    start = htmlResponse.indexOf("<h1>");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("</h1>", start + 4);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 4, end).trim());
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
        String url = getUsingUrl() + "cgi/delete";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("delete_" + model.postNumber, model.postNumber));
        pairs.add(new BasicNameValuePair("task", "delete"));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                return null;
            }
            throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
    }
}
