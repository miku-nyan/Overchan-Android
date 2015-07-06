package nya.miku.wishmaster.chans.arhivach;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;

import java.io.Closeable;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.models.ThreadModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.chans.AbstractChanModule;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.http.streamer.HttpWrongStatusCodeException;

/**
 * Created by Kalaver <Kalaver@users.noreply.github.com> on 23.06.2015.
 */

public class ArhivachModule extends AbstractChanModule {
    //private static final String TAG = "ArhivachModule";
    
    static final String CHAN_NAME = "Arhivach.org";
    
    static final String CHAN_DOMAIN = "arhivach.org";
    static final String CHAN_URL = "http://" + CHAN_DOMAIN + "/";
    
    public ArhivachModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Архивач";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_arhivach, null);
    }
    
    @Override
    public SimpleBoardModel[] getBoardsList(ProgressListener listener, CancellableTask task, SimpleBoardModel[] oldBoardsList) throws Exception {
        return ArhivachBoards.getBoardsList();
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        return ArhivachBoards.getBoard(shortName);
    }
    
    private ThreadModel[] readBoardPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified, boolean isThread)
            throws Exception {
        HttpResponseModel responseModel = null;
        Closeable in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = isThread ? new ArhivachThreadReader(responseModel.stream) : new ArhivachBoardReader(responseModel.stream);
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return isThread ? ((ArhivachThreadReader) in).readPage() : ((ArhivachBoardReader) in).readPage() ;
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
        urlModel.chanName = CHAN_NAME;
        urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
        urlModel.boardName = boardName;
        urlModel.boardPage = page;
        String url = buildUrl(urlModel);

        ThreadModel[] threads = readBoardPage(url, listener, task, oldList != null, false);
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

        ThreadModel[] threads = readBoardPage(url, listener, task, oldList != null, true);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(CHAN_NAME)) throw new IllegalArgumentException("wrong chan");
        StringBuilder url = new StringBuilder(CHAN_URL);
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                url.append("index").append("/");
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("index").append("/");
                if (model.boardPage > 1) url.append((model.boardPage - 1) * 25).append("/");
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                if (!model.boardName.equals("")) throw new IllegalArgumentException("wrong board name");
                url.append("thread/").append(model.threadNumber).append("/");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            default:
                throw new IllegalArgumentException("wrong page type");
        }
        return url.toString();
    }
    
    @Override
    public UrlPageModel parseUrl(String url) throws IllegalArgumentException {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www\\.)?(.+)").matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(parseUrl.group(1));
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        
        boolean matchDomain = false;
        if (CHAN_DOMAIN.equalsIgnoreCase(domain)) {
                matchDomain = true;
        }
        if (!matchDomain) throw new IllegalArgumentException("wrong chan");
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        try {
            if (path.contains("thread/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("thread/([0-9]+?)/(.*)").matcher(path);
                if (!matcher.find()) throw new Exception();
                model.boardName = "";
                model.threadNumber = matcher.group(1);
                if (matcher.group(2).startsWith("#")) {
                    String post = matcher.group(2).substring(1);
                    if (!post.equals(""))
                        model.postNumber = post;
                }
            } else if (path.contains("index") || path.indexOf("/")==0 || path.length()==0) {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                model.boardName = "";
                Matcher matcher = Pattern.compile("index/(\\d+)/?(.*)").matcher(path);
                String page = "";
                if (matcher.find())
                    page = matcher.group(1);
                if (!page.equals(""))
                    model.boardPage = (Integer.parseInt(page) / 25) + 1;
                else
                    model.boardPage = 1;

            }
        } catch (Exception e) {
            model.type = UrlPageModel.TYPE_OTHERPAGE;
            model.otherPath = path;
        }
        
        return model;
    }
}
