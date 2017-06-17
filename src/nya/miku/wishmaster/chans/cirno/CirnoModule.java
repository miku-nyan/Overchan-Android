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

package nya.miku.wishmaster.chans.cirno;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
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
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.api.util.WakabaUtils;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.interactive.SimpleCaptchaException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputFilter;
import android.text.InputType;

/**
 * Основной модуль для iichan.hk
 * @author miku-nyan
 *
 */
public class CirnoModule extends AbstractChanModule {
    
    static final String IICHAN_NAME = "iichan.hk";
    static final String IICHAN_DOMAIN = "iichan.hk";
    private static final String HARUHIISM_DOMAIN = "boards.haruhiism.net";
    private static final String HARUHIISM_URL = "http://" + HARUHIISM_DOMAIN + "/";
    
    private static final String PREF_KEY_REPORT_THREAD = "PREF_KEY_REPORT_THREAD";
    private String lastReportCaptcha;
    
    public CirnoModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return IICHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Ычан";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_cirno, null);
    }
    
    private boolean useHttps() {
        return useHttps(false);
    }
    
    private String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + IICHAN_DOMAIN + "/";
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addHttpsPreference(preferenceGroup, false);
        
        final Context context = preferenceGroup.getContext();
        EditTextPreference passwordPref = new EditTextPreference(context);
        passwordPref.setTitle(R.string.iichan_prefs_report_thread);
        passwordPref.setDialogTitle(R.string.iichan_prefs_report_thread);
        passwordPref.setSummary(R.string.iichan_prefs_report_thread_summary);
        passwordPref.setKey(getSharedKey(PREF_KEY_REPORT_THREAD));
        passwordPref.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        passwordPref.getEditText().setSingleLine();
        passwordPref.getEditText().setFilters(new InputFilter[] { new InputFilter.LengthFilter(255) });
        preferenceGroup.addPreference(passwordPref);
        
        super.addPreferencesOnScreen(preferenceGroup);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return CirnoBoards.getBoardsList();
    }

    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return CirnoBoards.getBoard(shortName);
    }
    
    private ThreadModel[] readWakabaPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified) throws Exception {
        HttpResponseModel responseModel = null;
        WakabaReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new WakabaReader(responseModel.stream,
                        url.startsWith(HARUHIISM_URL) ? DateFormats.HARUHIISM_DATE_FORMAT : DateFormats.IICHAN_DATE_FORMAT);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readWakabaPage();
            } else {
                if (responseModel.notModified()) return null;
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
        urlModel.chanName = IICHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = readWakabaPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            return threads;
        }
    }
    
    @Override
    public ThreadModel[] getCatalog(String boardName, int catalogType, ProgressListener listener, CancellableTask task, ThreadModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = IICHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_CATALOGPAGE;
        urlModel.boardName = boardName;
        String url = buildUrl(urlModel);
        
        HttpResponseModel responseModel = null;
        CirnoCatalogReader in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(oldList != null).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new CirnoCatalogReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return in.readPage();
            } else {
                if (responseModel.notModified()) return oldList;
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
    public PostModel[] getPostsList(String boardName, String threadNumber, ProgressListener listener, CancellableTask task, PostModel[] oldList)
            throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = IICHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = boardName;
        urlModel.threadNumber = threadNumber;
        String url = buildUrl(urlModel);
        
        ThreadModel[] threads = readWakabaPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }

    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "/cgi-bin/captcha" + (boardName.equals("b") || boardName.equals("a") ? "1" : "") + ".pl/" +
                boardName + "/?key=" + (threadNumber == null ? "mainpage" : ("res" + threadNumber));
        return downloadCaptcha(captchaUrl, listener, task);        
    }

    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "cgi-bin/wakaba.pl/" + model.boardName + "/";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post");
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        postEntityBuilder.
                addString("nya1", model.name).
                addString("nya2", model.email).
                addString("nya3", model.subject).
                addString("nya4", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("postredir", "1").
                addString("password", model.password);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "1");
        if (model.custommark) postEntityBuilder.addString("spoiler", "on");
        
        UrlPageModel refererPageModel = new UrlPageModel();
        refererPageModel.chanName = IICHAN_NAME;
        refererPageModel.boardName = model.boardName;
        if (model.threadNumber == null) {
            refererPageModel.type = UrlPageModel.TYPE_BOARDPAGE;
            refererPageModel.boardPage = 0;
        } else {
            refererPageModel.type = UrlPageModel.TYPE_THREADPAGE;
            refererPageModel.threadNumber = model.threadNumber;
        }
        
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPageModel)) };
        HttpRequestModel request = HttpRequestModel.builder().
                setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
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
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br /><br />", start + 31);
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
        String url = getUsingUrl() + "cgi-bin/wakaba.pl/" + model.boardName + "/";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("delete", model.postNumber));
        pairs.add(new BasicNameValuePair("task", "delete"));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("password", model.password));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br /><br />", start + 31);
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
    
    @Override
    public String reportPost(DeletePostModel model, final ProgressListener listener, final CancellableTask task) throws Exception {
        final String dNum;
        String pref = preferences.getString(getSharedKey(PREF_KEY_REPORT_THREAD), null);
        if (pref != null && pref.length() > 0) {
            dNum = pref;
        } else {
            String url = "http://miku-nyan.github.io/Overchan-Android/data/report_thread";
            dNum = HttpStreamer.getInstance().getStringFromUrl(url, HttpRequestModel.builder().setGET().build(), httpClient, listener, task, false);
        }
        
        if (lastReportCaptcha == null) {
            throw new SimpleCaptchaException() {
                private static final long serialVersionUID = 1L;
                @Override
                protected Bitmap getNewCaptcha() throws Exception {
                    return CirnoModule.this.getNewCaptcha("d", dNum, listener, task).bitmap;
                }
                @Override
                protected void storeResponse(String response) {
                    lastReportCaptcha = response;
                }
            };
        } else {
            UrlPageModel subject = new UrlPageModel();
            subject.chanName = IICHAN_NAME;
            subject.type = UrlPageModel.TYPE_THREADPAGE;
            subject.boardName = model.boardName;
            subject.threadNumber = model.threadNumber;
            subject.postNumber = model.postNumber;
            SendPostModel sendModel = new SendPostModel();
            sendModel.chanName = IICHAN_NAME;
            sendModel.boardName = "d";
            sendModel.threadNumber = dNum;
            sendModel.name = "";
            sendModel.subject = "";
            sendModel.email = "";
            sendModel.comment = buildUrl(subject);
            if (model.reportReason != null) sendModel.comment += "\n" + model.reportReason;
            sendModel.password = getDefaultPassword();
            sendModel.captchaAnswer = lastReportCaptcha;
            lastReportCaptcha = null;
            return sendPost(sendModel, listener, task);
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(IICHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        if (model.boardName != null) {
            if (model.boardName.equals("vo")) {
                return "http://hatsune.ru/b/";
            } else if (model.boardName.equals("tu")) {
                return WakabaUtils.buildUrl(model, NowereModule.NOWERE_URL_HTTP);
            } else if (model.boardName.equals("es")) {
                return "http://owlchan.ru/es/";
            } else if (CirnoBoards.is410Board(model.boardName)) {
                return WakabaUtils.buildUrl(model, Chan410Module.CHAN410_URL);
            }
        }
        boolean haruhiism = "abe".equals(model.boardName) || (model.otherPath != null && model.otherPath.startsWith("/abe"));
        if (!haruhiism && model.type == UrlPageModel.TYPE_CATALOGPAGE) return getUsingUrl() + model.boardName + "/catalogue.html";
        return WakabaUtils.buildUrl(model, haruhiism ? HARUHIISM_URL : getUsingUrl());
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        UrlPageModel model = WakabaUtils.parseUrl(url, IICHAN_NAME, IICHAN_DOMAIN, HARUHIISM_DOMAIN);
        if (model.type == UrlPageModel.TYPE_OTHERPAGE && model.otherPath != null && model.otherPath.endsWith("/catalogue.html")) {
            model.type = UrlPageModel.TYPE_CATALOGPAGE;
            model.boardName = model.otherPath.substring(0, model.otherPath.length() - 15);
            model.otherPath = null;
        }
        return model;
    }
}
