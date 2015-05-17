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

package nya.miku.wishmaster.chans.sevenchan;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntityHC4;
import org.apache.http.message.BasicNameValuePair;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.api.util.WakabaReader;
import nya.miku.wishmaster.chans.AbstractWakabaModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.recaptcha.Recaptcha;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

@SuppressWarnings("deprecation") // https://issues.apache.org/jira/browse/HTTPCLIENT-1632

public class SevenchanModule extends AbstractWakabaModule {
    private static final String CHAN_NAME = "7chan.org";
    private static final String RECAPTCHA_KEY = "6LdVg8YSAAAAAOhqx0eFT1Pi49fOavnYgy7e-lTO";
    static final String TIMEZONE = "GMT+4"; // ?
    private static final Pattern ERROR_POSTING = Pattern.compile("<h2(?:[^>]*)>(.*?)</h2>", Pattern.DOTALL);
    
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "7ch", "Site Discussion", "7chan & Related Services", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ch7", "Channel7 & Radio7", "7chan & Related Services", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "irc", "Internet Relay Circlejerk", "7chan & Related Services", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "777", "/selfhelp/", "Premium Content", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Random", "Premium Content", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "banner", "Banners", "Premium Content", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fl", "Flash", "Premium Content", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "gfx", "Graphics Manipulation", "Premium Content", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "class", "The Finer Things", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "co", "Comics and Cartoons", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "eh", "Particularly uninteresting conversation", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fit", "Fitness & Health", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "halp", "Technical Support", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "jew", "Thrifty Living", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lit", "Literature", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "phi", "Philosophy", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pr", "Programming", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "rnb", "Rage and Baww", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sci", "Science, Technology, Engineering, and Mathematics", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "tg", "Tabletop Games", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "w", "Weapons", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "zom", "Zombies", "SFW", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime & Manga", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "grim", "Cold, Grim & Miserable", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hi", "History and Culture", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "me", "Film, Music & Television", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "rx", "Drugs", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "vg", "", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "wp", "Wallpapers", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "x", "Paranormal & Conspiracy", "General", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cake", "Delicious", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cd", "Crossdressing", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "d", "Alternative Hentai", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "di", "Sexy Beautiful Traps", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "elit", "Erotic Literature", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fag", "Men Discussion", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fur", "Furry", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "gif", "Animated GIFs", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hentai", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "men", "Sexy Beautiful Men", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pco", "Porn Comics", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Sexy Beautiful Women", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "sm", "Shotacon", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ss", "Straight Shotacon", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "unf", "Uniforms", "Porn", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "The Vineyard", "Porn", true)
    };
    
    private Recaptcha lastCaptcha = null;
    
    public SevenchanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "7chan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_7chan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return "7chan.org";
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected WakabaReader getWakabaReader(InputStream stream, UrlPageModel urlModel) {
        return new SevenchanReader(stream);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel board = super.getBoard(shortName, listener, task);
        board.timeZoneId = TIMEZONE;
        board.defaultUserName = "";
        board.readonlyBoard = false;
        board.requiredFileForNewThread = !shortName.equals("halp") && !shortName.equals("7ch");
        board.allowDeletePosts = true;
        board.allowDeleteFiles = true;
        board.allowNames = !shortName.equals("b");
        board.allowSubjects = true;
        board.allowSage = true;
        board.allowEmails = true;
        board.ignoreEmailIfSage = true;
        board.allowWatermark = false;
        board.allowOpMark = false;
        board.allowRandomHash = true;
        board.allowIcons = false;
        board.attachmentsMaxCount = 1;
        board.attachmentsFormatFilters = shortName.equals("fl") ? new String[] { "swf" } : null;
        board.markType = BoardModel.MARK_BBCODE;
        return board;
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        if (threadNumber != null) return null;
        Recaptcha recaptcha = Recaptcha.obtain(RECAPTCHA_KEY, task, httpClient, useHttps() ? "https" : "http");
        CaptchaModel model = new CaptchaModel();
        model.type = CaptchaModel.TYPE_NORMAL;
        model.bitmap = recaptcha.bitmap;
        lastCaptcha = recaptcha;
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task).
                addString("board", model.boardName).
                addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
                addString("name", model.name).
                addString("em", model.sage ? "sage" : model.email).
                addString("subject", model.subject).
                addString("message", model.comment);
        
        if (model.threadNumber == null) {
            if (lastCaptcha == null) throw new Exception("Invalid captcha");
            postEntityBuilder.
                    addString("recaptcha_challenge_field", lastCaptcha.challenge).
                    addString("recaptcha_response_field", model.captchaAnswer);
            lastCaptcha = null;
        }
        
        if (model.attachments != null && model.attachments.length > 0)
            postEntityBuilder.addFile("imagefile[]", model.attachments[0], model.randomHash);
        else if (model.threadNumber == null) postEntityBuilder.addString("nofile", "on");
        
        postEntityBuilder.addString("postpassword", model.password);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                Matcher errorMatcher = ERROR_POSTING.matcher(htmlResponse);
                if (errorMatcher.find()) throw new Exception(errorMatcher.group(1).trim());
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String deletePost(DeletePostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        pairs.add(new BasicNameValuePair("board", model.boardName));
        pairs.add(new BasicNameValuePair("post[]", model.postNumber));
        if (model.onlyFiles) pairs.add(new BasicNameValuePair("fileonly", "on"));
        pairs.add(new BasicNameValuePair("postpassword", model.password));
        pairs.add(new BasicNameValuePair("deletepost", "Delete"));
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(new UrlEncodedFormEntityHC4(pairs, "UTF-8")).setNoRedirect(true).build();
        String result = HttpStreamer.getInstance().getStringFromUrl(url, request, httpClient, listener, task, false);
        if (result.contains("Incorrect password")) throw new Exception("Incorrect password");
        return null;
    }
    
    @Override
    public String fixRelativeUrl(String url) {
        if (url.startsWith("//")) return (useHttps() ? "https:" : "http:") + url;
        return super.fixRelativeUrl(url);
    }
    
}
