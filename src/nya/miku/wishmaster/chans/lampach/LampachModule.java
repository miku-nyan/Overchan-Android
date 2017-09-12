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

package nya.miku.wishmaster.chans.lampach;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class LampachModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "lampach.net";
    private static final String DOMAIN = "lampach.net";
    private static final String[] FORMATS = new String[] { "jpg", "jpeg", "png", "gif" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "r", "Lampa chan", null, false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ku", "Kuroki", null, false)
    };
    private static final String RECAPTCHA_KEY = "6LcauyUTAAAAAKsJWyOBVOEXEjs5fVnrse_507E8";
    private static final String RECAPTCHA_BOARD = "r";
    
    private static final Pattern ERROR_PATTERN = Pattern.compile("<div style=[^>]*>(.*?)</div>");
    private static final Pattern REDIRECT_PATTERN = Pattern.compile("url=res/(\\d+)\\.html#(\\d+)");
    
    public LampachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Lampa chan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_lampach, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAIN;
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.timeZoneId = "GMT+3";
        board.readonlyBoard = false;
        board.bumpLimit = 303;
        board.requiredFileForNewThread = true;
        board.allowDeletePosts = true;
        board.allowDeleteFiles = false;
        board.allowNames = false;
        board.allowSubjects = true;
        board.allowSage = true;
        board.ignoreEmailIfSage = true;
        board.allowEmails = false;
        board.allowCustomMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = FORMATS;
        board.markType = BoardModel.MARK_BBCODE;
        return board;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new LampachReader(stream, urlModel.boardName);
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (RECAPTCHA_BOARD.equals(boardName)) return null;
        String captchaUrl = getUsingUrl() + boardName + "/inc/captcha.php";
        return downloadCaptcha(captchaUrl, listener, task);
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/imgboard.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("parent", model.threadNumber != null ? model.threadNumber : "0").
                addString("email", "noko").
                addString("subject", model.subject).
                addString("message", model.comment).
                addString("password", model.password);
        if (model.sage) postEntityBuilder.addString("sage", "sage");
        if (RECAPTCHA_BOARD.equals(model.boardName)) {
            String response = Recaptcha2solved.pop(RECAPTCHA_KEY);
            if (response == null) {
                throw Recaptcha2.obtain(getUsingUrl(), RECAPTCHA_KEY, null, CHAN_NAME, false);
            }
            postEntityBuilder.addString("g-recaptcha-response", response);
        } else {
            postEntityBuilder.addString("captcha", model.captchaAnswer);
        }
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("file", model.attachments[0], model.randomHash);
        
        if (task != null && task.isCancelled()) throw new InterruptedException("interrupted");
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("Updat")) {
                    Matcher redirectMatcher = REDIRECT_PATTERN.matcher(htmlResponse);
                    if (redirectMatcher.find()) {
                        UrlPageModel redirModel = new UrlPageModel();
                        redirModel.chanName = CHAN_NAME;
                        redirModel.type = UrlPageModel.TYPE_THREADPAGE;
                        redirModel.boardName = model.boardName;
                        redirModel.threadNumber = redirectMatcher.group(1);
                        redirModel.postNumber = redirectMatcher.group(2);
                        return buildUrl(redirModel);
                    }
                    return null;
                }
                Matcher errorMatcher = ERROR_PATTERN.matcher(htmlResponse);
                if (errorMatcher.find()) {
                    throw new Exception(errorMatcher.group(1));
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + model.boardName + "/imgboard.php?delete";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", "b"));
        pairs.add(new BasicNameValuePair("delete", model.postNumber));
        pairs.add(new BasicNameValuePair("password", model.password));
        pairs.add(new BasicNameValuePair("deletepost", "Delete"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Invalid password")) throw new Exception("Invalid password");
        return null;
    }
    
}
