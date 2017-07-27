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

package nya.miku.wishmaster.chans.mentachnet;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

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
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class MentachnetModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "02ch.net";
    private static final String DOMAIN = "02ch.net";
    
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Анимация", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Беспорядок", null, true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Видеоигры", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hr", "Высокое разрешение", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Отделение", null, false),
    };
    
    private static final DateFormat DATE_FORMAT;
    static {
        DateFormatSymbols symbols = new DateFormatSymbols();
        symbols.setWeekdays(new String[] { "", "Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота" });
        DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd (EEEE) HH:mm:ss", symbols);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    public MentachnetModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Нольдва.ч";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_cirno, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected DateFormat getDateFormat() {
        return DATE_FORMAT;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId =  "GMT+3";
        model.defaultUserName = "Аноним";
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_NOT_ALLOWED;
        model.allowNames = false;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = true;
        model.customMarkDescription = "Спойлер";
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_WAKABAMARK;
        return model;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + boardName + "/captcha.pl" + "?key=" + (threadNumber == null ? "mainpage" : ("res" + threadNumber));
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task);
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        postEntityBuilder.
                addString("task", "post").
                addString("field1", model.name).
                addString("field2", model.email).
                addString("field3", model.subject).
                addString("field4", model.comment).
                addString("captcha", model.captchaAnswer).
                addString("password", model.password);
        if (model.custommark) postEntityBuilder.addString("img_spoiler", "on");
        if (model.attachments != null && model.attachments.length > 0) {
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        }
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 303) {
                return null;
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (!htmlResponse.contains("<blockquote")) {
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<", start + 31);
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
            } else {
                throw new Exception(response.statusCode + " - " + response.statusReason);
            }
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
                        int end = htmlResponse.indexOf("<", start + 31);
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
