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

package nya.miku.wishmaster.chans.ernstchan;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractWakabaModule;
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
import nya.miku.wishmaster.api.util.UrlPathUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

public class ErnstModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "ernstchan.com";
    private static final String CHAN_DOMAIN = "ernstchan.com";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Passierschein A38", null, true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "No shittings during wörktime", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Computer & Programmieren", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime & Manga", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fefe", "Fefes Blog", null, true),
    };
    
    private static final Pattern THREADPAGE_PATTERN = Pattern.compile("([^/]+)/thread/(\\d+)[^#]*(?:#(\\d+))?");
    private static final Pattern BOARDPAGE_PATTERN = Pattern.compile("([^/]+)(?:/page/(\\d+))?");
    
    public ErnstModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Ernstchan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_phutaba, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return CHAN_DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "Europe/Berlin";
        model.defaultUserName = "Ernst";
        model.readonlyBoard = false;
        model.firstPage = 1;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = false;
        model.allowNames = !shortName.equals("b") && !shortName.equals("int") && !shortName.equals("fefe");
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = false;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 4;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
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
        
        ThreadModel[] threads = readPage(url, listener, task, oldList != null);
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
    
    private ThreadModel[] readPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified) throws Exception {
        HttpResponseModel responseModel = null;
        ErnstReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new ErnstReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readPage();
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
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() +  "captcha.pl?key=" + (threadNumber == null ? "mainpage" : ("res" + threadNumber)) +
                "&dummy=" + (threadNumber == null ? "" : Long.toString(Math.round(Math.random()*1000000))) + "&board=" + boardName;
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "/wakaba.pl";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post").
                addString("board", model.boardName);
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        if (model.sage) postEntityBuilder.addString("field2", "sage");
        postEntityBuilder.
                addString("gb2", "thread").
                addString("field1", model.name).
                addString("field3", model.subject).
                addString("field4", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("password", model.password);
        if (model.attachments != null && model.attachments.length > 0) {
            for (int i=0; i<model.attachments.length; ++i) {
                postEntityBuilder.addFile("file", model.attachments[i], model.randomHash);
            }
        }
        
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
                if (htmlResponse.contains("<div class=\"error\">")) {
                    int start = htmlResponse.indexOf("<div class=\"info\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("</div>", start + 18);
                        if (end != -1) {
                            throw new Exception(StringEscapeUtils.unescapeHtml4(htmlResponse.substring(start + 18, end).trim()));
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
        String url = getUsingUrl() + "/wakaba.pl";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("delete", model.postNumber));
        pairs.add(new BasicNameValuePair("task", "delete"));
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("<div class=\"error\">")) {
                    int start = htmlResponse.indexOf("<div class=\"info\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("</div>", start + 18);
                        if (end != -1) {
                            throw new Exception(StringEscapeUtils.unescapeHtml4(htmlResponse.substring(start + 18, end).trim()));
                        }
                    }
                }
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null && !model.boardName.matches("\\w*")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(getUsingUrl());
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                url.append(model.boardName).append('/');
                if (model.boardPage != UrlPageModel.DEFAULT_FIRST_PAGE && model.boardPage > 1) {
                    url.append("page/").append(model.boardPage);
                }
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                url.append(model.boardName).append("/thread/").append(model.threadNumber);
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            case UrlPageModel.TYPE_OTHERPAGE:
                url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath);
                break;
            default:
                throw new IllegalArgumentException("wrong page type");
        }
        return url.toString();
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String urlPath  = UrlPathUtils.getUrlPath(url, getUsingDomain());
        if (urlPath == null) throw new IllegalArgumentException("wrong domain");
        urlPath = urlPath.toLowerCase(Locale.US);
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        
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
        
        Matcher boardPage = BOARDPAGE_PATTERN.matcher(urlPath);
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
