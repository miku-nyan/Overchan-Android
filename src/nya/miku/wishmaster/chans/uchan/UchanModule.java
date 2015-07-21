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

package nya.miku.wishmaster.chans.uchan;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicNameValuePair;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.chans.AbstractWakabaModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

@SuppressWarnings("deprecation") //https://issues.apache.org/jira/browse/HTTPCLIENT-1632
public class UchanModule extends AbstractWakabaModule {
    
    private static final String CHAN_NAME = "uchan.to";
    private static final String DOMAIN = "uchan.to";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аніме та Манга", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Безлад", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cos", "Косплей", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ero", "Еротика, Секс", "", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "exp", "Цікаві досліди", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ffd", "Фофудюшка (ФСБ+МП)", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ig", "Комп'ютерні та інші ігри", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "International", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Література, Освіта, Наука", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "muz", "Музика", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pk", "Психологічний крах", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pr", "Програмування, комп`ютери, ОС", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sho", "Про Учан (пропозиції, скарги, стукацтво)", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tr", "Транспорт", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Відео, Кіно, Телебачення, Мультфільми", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ukr", "Українізація, Політика", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Вагон з митцями Леся Подерв'янського", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "war", "Війна, Армія, Зброя", "", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Прірва", "", true)
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] {
            "7z", "bz2", "flv", "gif", "gz", "jpg", "mp3", "ogg", "pdf", "png", "psd", "rar", "swf", "zip"
    };
    
    private static final DateFormat DATE_FORMAT;
    static {
        DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd о HH:mm", Locale.US);
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("Europe/Kiev"));
    }
    
    public UchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Учан";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_uchan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    protected String[] getAllDomains() {
        return new String[] { DOMAIN, "uchan.org.ua" };
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean useHttpsDefaultValue() {
        return false;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.timeZoneId = "Europe/Kiev";
        model.defaultUserName = "Anonymous";
        model.readonlyBoard = false;
        model.requiredFileForNewThread = false;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowWatermark = false;
        model.allowOpMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_WAKABAMARK;
        return model;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new WakabaReader(stream, DATE_FORMAT) {
            @Override
            protected void parseDate(String date) {
                date = date.substring(date.indexOf(')') + 1).trim();
                super.parseDate(date);
            }
            @Override
            protected void parseOmittedString(String omitted) {
                if (omitted.indexOf('<') != -1) omitted = omitted.substring(0, omitted.indexOf('<'));
                super.parseOmittedString(omitted);
            }
            @Override
            protected void parseThumbnail(String imgTag) {
                if (imgTag.startsWith(" src=\"/prapory/")) {
                    int srcEnd = imgTag.indexOf('\"', 7);
                    if (srcEnd != -1) {
                        BadgeIconModel iconModel = new BadgeIconModel();
                        iconModel.source = imgTag.substring(7, srcEnd);
                        int start, end;
                        if ((start = imgTag.indexOf("title=\"", srcEnd)) != -1 && (end = imgTag.indexOf('\"', start + 7)) != -1) {
                            iconModel.description = imgTag.substring(start + 7, end);
                        }
                        int currentIconsCount = currentPost.icons == null ? 0 : currentPost.icons.length;
                        BadgeIconModel[] newIconsArray = new BadgeIconModel[currentIconsCount + 1];
                        for (int i=0; i<currentIconsCount; ++i) newIconsArray[i] = currentPost.icons[i];
                        newIconsArray[currentIconsCount] = iconModel;
                        currentPost.icons = newIconsArray;
                    }
                } else super.parseThumbnail(imgTag);
            }
        };
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/wakaba.pl";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("task", "post");
        if (model.threadNumber != null) postEntityBuilder.addString("parent", model.threadNumber);
        postEntityBuilder.
                addString("field1", model.name).
                addString("field2", model.sage ? "sage" : model.email).
                addString("field3", model.subject).
                addString("field4", model.comment);
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        else postEntityBuilder.addString("nofile", "on");
        postEntityBuilder.
                addString("noko", "on").
                addString("password", model.password);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            System.out.println(response.statusCode + " - " + response.statusReason);
            if (response.statusCode == 303) {
                return null;
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
                        end = htmlResponse.indexOf("</h1>", start + 31);
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
            } else if (response.statusCode == 403) {
                throw new Exception("Доступ заборонено");
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
                    int start = htmlResponse.indexOf("<h1 style=\"text-align: center\">");
                    if (start != -1) {
                        int end = htmlResponse.indexOf("<br /><br />", start + 31);
                        if (end != -1) {
                            throw new Exception(htmlResponse.substring(start + 31, end).trim());
                        }
                    }
                }
            } else if (response.statusCode == 403) {
                throw new Exception("Доступ заборонено");
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        return super.parseUrl(url.replace("/chan.html", "/index.html"));
    }
    
}
