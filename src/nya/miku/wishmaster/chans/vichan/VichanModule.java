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

package nya.miku.wishmaster.chans.vichan;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;
import nya.miku.wishmaster.lib.org_json.JSONObject;

@SuppressWarnings("deprecation") // https://issues.apache.org/jira/browse/HTTPCLIENT-1632

public class VichanModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "pl.vichan.net";
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Radom", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cp", "Chłodne Pasty", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "id", "Inteligentne dyskusje", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "True International", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r+oc", "Prośby + Oryginalna zawartość", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "slav", "Słowianie", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "veto", "Wolne! Nie pozwalam!", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "waifu", "Waifu i husbando", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wiz", "Mizoginia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "btc", "Biznes i ekonomia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Informatyka, Elektronika, Gadżety", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c++", "Zaawansowana informatyka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fso", "Motoryzacja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hobby i zainteresowania", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "kib", "Kibice", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ku", "Kuchnia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lsd", "Substancje psychoaktywne", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psl", "Polityka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sci", "Nauka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trv", "Podróże", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "Gry video i online", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime i Manga", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ac", "Komiks i animacja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "az", "Azja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fr", "Filozofia & religia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hk", "Historia & Kultura", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Literatura", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mu", "Muzyka", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tv", "Filmy i seriale", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vp", "Pokémon", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Wróżbita Maciej", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "med", "Medyczny", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pr", "Prawny", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pro", "Problemy i protipy", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "psy", "Psychologia", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sex", "Seks i związki", " ", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "soc", "Socjalizacja i atencja", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sr", "Samorozwój", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "swag", "Styl i wygląd", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "trap", "Transseksualizm", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "chan", "Chany i ich kultura", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "meta", "Administracyjny", " ", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mit", "Mitomania", " ", false),
    };
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");

    public VichanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Polski vichan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_vichan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return "pl.vichan.net";
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
        model.timeZoneId = "GMT";
        model.readonlyBoard = false;
        model.requiredFileForNewThread = true;
        model.allowDeletePosts = true;
        model.allowDeleteFiles = true;
        model.allowReport = BoardModel.REPORT_WITH_COMMENT;
        model.allowNames = true;
        model.allowSubjects = true;
        model.allowSage = true;
        model.allowEmails = true;
        model.ignoreEmailIfSage = true;
        model.allowCustomMark = false;
        model.allowRandomHash = true;
        model.allowIcons = false;
        model.attachmentsMaxCount = 1;
        model.attachmentsFormatFilters = null;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    protected ThreadModel mapThreadModel(JSONObject opPost, String boardName) {
        ThreadModel model = super.mapThreadModel(opPost, boardName);
        if (model.attachmentsCount >= 0) model.attachmentsCount += opPost.optInt("omitted_images", 0);
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = CHAN_NAME;
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
            if (pair.getKey().equals("spoiler")) continue;
            String val;
            switch (pair.getKey()) {
                case "name": val = model.name; break;
                case "email": val = model.sage ? "sage" : model.email; break;
                case "subject": val = model.subject; break;
                case "body": val = model.comment; break;
                case "password": val = model.password; break;
                default: val = pair.getValue();
            }
            if (pair.getKey().equals("file")) {
                if (model.attachments != null && model.attachments.length > 0) {
                    postEntityBuilder.addFile(pair.getKey(), model.attachments[0], model.randomHash);
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
        pairs.add(new BasicNameValuePair("delete", "Delete"));
        pairs.add(new BasicNameValuePair("reason", ""));
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = CHAN_NAME;
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
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
        pairs.add(new BasicNameValuePair("report", "Report"));
        
        UrlPageModel refererPage = new UrlPageModel();
        refererPage.type = UrlPageModel.TYPE_THREADPAGE;
        refererPage.chanName = CHAN_NAME;
        refererPage.boardName = model.boardName;
        refererPage.threadNumber = model.threadNumber;
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, buildUrl(refererPage)) };
        HttpRequestModel rqModel = HttpRequestModel.builder().
                setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setCustomHeaders(customHeaders).setNoRedirect(true).build();
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
    public void downloadFile(String url, OutputStream out, ProgressListener listener, CancellableTask task) throws Exception {
        try {
            super.downloadFile(url, out, listener, task);
        } catch (HttpWrongStatusCodeException e) {
            if (url.contains("/thumb/") && url.endsWith(".jpg") && e.getStatusCode() == 404) {
                super.downloadFile(url.substring(0, url.length() - 3) + "gif", out, listener, task);
            } else {
                throw e;
            }
        }
    }
    
}
