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

package nya.miku.wishmaster.chans.sich;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import cz.msebera.android.httpclient.message.BasicHeader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractVichanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.BadgeIconModel;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class SichModule extends AbstractVichanModule {
    private static final String CHAN_NAME = "sich.co.ua";
    private static final String DEFAULT_DOMAIN = "sich.co.ua";
    private static final String[] ATTACHMENT_FORMATS = new String[] {
        "bmp", "gif", "jpeg", "jpg", "png", "mp4", "webm"
    };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "b", "Балачки", "Основа", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "int", "International", "Основа", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Аніме", "Тематика", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "f", "Фап", "Тематика", true),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "po", "Політика", "Тематика", false),
        ChanModels.obtainSimpleBoardModel(CHAN_NAME, "soc", "Соціум", "Тематика", false),
    };
    private static final Pattern ERROR_PATTERN = Pattern.compile("<h2 [^>]*>(.*?)</h2>");
    
    public SichModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Січ";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_sich, null);
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected String getUsingDomain() {
        return DEFAULT_DOMAIN;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected boolean useHttpsDefaultValue() {
        return true;
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.bumpLimit = 250;
        model.allowNames = false;
        model.allowSage = false;
        model.allowEmails = false;
        model.attachmentsMaxCount = 4;
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        return model;
    }
    
    @Override
    protected PostModel mapPostModel(JSONObject object, String boardName) {
        PostModel model = super.mapPostModel(object, boardName);
        model.comment = model.comment.replaceAll(
            "style=\"text-decoration:line-through;\">",
            "class=\"s\">"
        );
        model.comment = model.comment.replaceAll(
            "class=\"quote dice\">",
            "class=\"unkfunc\">"
        );
        if (boardName.equals("int")) {
            String country = object.optString("country");
            if (country != null && country.length() > 0) {
                model.icons = new BadgeIconModel[] { new BadgeIconModel() };
                model.icons[0].source = "/static/flags/" + country.toLowerCase(Locale.US) + ".png";
                model.icons[0].description = object.optString("country_name", country);
            }
        }
        return model;
    }
    
    @Override
    protected AttachmentModel mapAttachment(JSONObject object, String boardName, boolean isSpoiler) {
        String ext = object.optString("ext", "");
        if (!ext.equals("")) {
            AttachmentModel attachment = new AttachmentModel();
            switch (ext) {
                case ".jpeg":
                case ".jpg":
                case ".png":
                    attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                    break;
                case ".gif":
                    attachment.type = AttachmentModel.TYPE_IMAGE_GIF;
                    break;
                case ".webm":
                case ".mp4":
                    attachment.type = AttachmentModel.TYPE_VIDEO;
                    break;
                default:
                    attachment.type = AttachmentModel.TYPE_OTHER_FILE;
            }
            attachment.size = object.optInt("fsize", -1);
            if (attachment.size > 0) attachment.size = Math.round(attachment.size / 1024f);
            attachment.width = object.optInt("w", -1);
            attachment.height = object.optInt("h", -1);
            attachment.originalName = object.optString("filename", "") + ext;
            attachment.isSpoiler = isSpoiler;
            String tim = object.optString("tim", "");
            String _t_ext = object.optString("_t_ext", ".png");
            if (tim.length() > 0 && _t_ext != ".webm") {
                attachment.thumbnail = isSpoiler ? null : ("/" + boardName + "/thumb/" + tim + _t_ext);
                attachment.path = "/" + boardName + "/src/" + tim + ext;
                return attachment;
            }
        }
        return null;
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
                case "subject": val = model.subject; break;
                case "body": val = model.comment; break;
                case "password": val = model.password; break;
                default: val = pair.getValue();
            }
            int i = 1;
            String fileNo;
            switch (pair.getKey()) {
                case "file": case "file2": case "file3": case "file4":
                    fileNo = pair.getKey().replaceAll("[\\D]", "");
                    if (fileNo != "") {
                        i = Integer.parseInt(fileNo);
                    }
                    if (model.attachments == null || model.attachments.length < i) {
                        postEntityBuilder.addPart(pair.getKey(), new ByteArrayBody(new byte[0], ""));
                    } else {
                        postEntityBuilder.addFile(pair.getKey(), model.attachments[i - 1], model.randomHash);
                    }
                    break;
                default:
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
    protected String getDeleteFormValue(DeletePostModel model) {
        return model.boardName.equals("int") ? "Delete" : "Видалити";
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return model.boardName.equals("int") ? "Report" : "Поскаржитися";
    }
}
