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

package nya.miku.wishmaster.chans.monaba;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.tuple.Pair;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.entity.mime.content.ByteArrayBody;
import cz.msebera.android.httpclient.message.BasicHeader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceGroup;
import android.text.TextUtils;

import nya.miku.wishmaster.api.CloudflareChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
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

public abstract class AbstractMonabaChan extends CloudflareChanModule {
    static final String[] RATINGS = new String[] { "SFW", "R15", "R18", "R18G" };
    
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile("<img[^>]*src=\"(.*?)\"");
    private static final Pattern ERROR_PATTERN = Pattern.compile("<div[^>]*id=\"message\"[^>]*>(.*?)</div>", Pattern.DOTALL);
    
    public AbstractMonabaChan(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    protected boolean canHttps() {
        return true;
    }
    
    protected boolean useHttps() {
        if (!canHttps()) return false;
        return useHttps(true);
    }
    
    protected abstract String getUsingDomain();
    
    protected String[] getAllDomains() {
        return new String[] { getUsingDomain() };
    }
    
    protected String getUsingUrl() {
        return (useHttps() ? "https://" : "http://") + getUsingDomain() + "/";
    }
    
    @Override
    protected boolean canCloudflare() {
        return false;
    }
    
    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        if (canHttps()) addHttpsPreference(preferenceGroup, true);
        addProxyPreferences(preferenceGroup);
    }
    
    protected ThreadModel[] readPage(String url, ProgressListener listener, CancellableTask task, boolean checkIfModified) throws Exception {
        HttpResponseModel responseModel = null;
        Closeable in = null;
        HttpRequestModel rqModel = HttpRequestModel.builder().setGET().setCheckIfModified(checkIfModified).build();
        try {
            responseModel = HttpStreamer.getInstance().getFromUrl(url, rqModel, httpClient, listener, task);
            if (responseModel.statusCode == 200) {
                in = new MonabaReader(responseModel.stream, canCloudflare());
                if (task != null && task.isCancelled()) throw new Exception("interrupted");
                return ((MonabaReader) in).readPage() ;
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
        urlModel.chanName = getChanName();
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
        urlModel.chanName = getChanName();
        urlModel.type = UrlPageModel.TYPE_THREADPAGE;
        urlModel.boardName = boardName;
        urlModel.threadNumber = threadNumber;
        String url = buildUrl(urlModel);
        ThreadModel[] threads = readPage(url, listener, task, oldList != null);
        if (threads == null) {
            return oldList;
        } else {
            if (threads.length == 0) throw new Exception("Unable to parse response");
            return oldList == null ? threads[0].posts : ChanModels.mergePostsLists(Arrays.asList(oldList), Arrays.asList(threads[0].posts));
        }
    }
    
    @Override
    public CaptchaModel getNewCaptcha(String boardName, String threadNumber, ProgressListener listener, CancellableTask task) throws Exception {
        String captchaUrl = getUsingUrl() + "/captcha";
        String html = HttpStreamer.getInstance().getStringFromUrl(captchaUrl, HttpRequestModel.DEFAULT_GET, httpClient, listener, task, false);
        Matcher matcher = IMAGE_URL_PATTERN.matcher(html);
        if (matcher.find()) {
            return downloadCaptcha(fixRelativeUrl(matcher.group(1)), listener, task);
        } else {
            throw new Exception("Captcha update failed");
        }
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        UrlPageModel urlModel = new UrlPageModel();
        urlModel.chanName = getChanName();
        urlModel.boardName = model.boardName;
        if (model.threadNumber == null) {
            urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
            urlModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
        } else {
            urlModel.type = UrlPageModel.TYPE_THREADPAGE;
            urlModel.threadNumber = model.threadNumber;
        }
        
        String referer = buildUrl(urlModel);
        List<Pair<String, String>> fields = MonabaAntibot.getFormValues(referer, task, httpClient);
        
        if (task != null && task.isCancelled()) throw new Exception("interrupted");
        
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().
                setCharset(Charset.forName("UTF-8")).setDelegates(listener, task);
        String rating = (model.icon >= 0 && model.icon < RATINGS.length) ? Integer.toString(model.icon+1) : "1";
        int fileCount = 0;
        for (Pair<String, String> pair : fields) {
            String val;
            switch (pair.getKey()) {
                case "f1": val = model.name; break;
                case "f2": val = model.subject; break;
                case "f3": val = model.comment; break;
                case "f4": val = TextUtils.isEmpty(model.password) ? getDefaultPassword() : model.password; break;
                case "f5": val = TextUtils.isEmpty(model.captchaAnswer) ? "" : model.captchaAnswer; break;
                case "f6": val = "1"; break; //noko
                case "f7": val = model.sage ? pair.getValue() : ""; break;
                default: val = pair.getValue();
            }
            
            if (pair.getValue().equals("file")) {
                if ((model.attachments != null) && (fileCount < model.attachments.length)) {
                    postEntityBuilder.addFile(pair.getKey(), model.attachments[fileCount], model.randomHash);
                    ++fileCount;
                } else {
                    postEntityBuilder.addPart(pair.getKey(), new ByteArrayBody(new byte[0], ""));
                }
            } else if (pair.getValue().equals("rating-input")) {
                postEntityBuilder.addString(pair.getKey(), rating);
            } else {
                postEntityBuilder.addString(pair.getKey(), val);
            }
        }
        
        String url = getUsingUrl() + model.boardName + (model.threadNumber != null ? "/" + model.threadNumber : "");
        
        Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, referer) };
        
        HttpRequestModel request = HttpRequestModel.builder().
                setPOST(postEntityBuilder.build()).setCustomHeaders(customHeaders).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 303) {
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        String location = header.getValue();
                        String html = HttpStreamer.getInstance().
                                getStringFromUrl(location, HttpRequestModel.DEFAULT_GET, httpClient, null, task, false);
                        if (html.contains("Post has been submitted successfully")) {
                            return location;
                        }
                        Matcher errorMatcher = ERROR_PATTERN.matcher(html);
                        if (errorMatcher.find()) {
                            throw new Exception(StringEscapeUtils.unescapeHtml4(errorMatcher.group(1)));
                        }
                        return null;
                    }
                }
            } else throw new Exception(response.statusCode + " - " + response.statusReason);
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    public String buildUrl(UrlPageModel model) throws IllegalArgumentException {
        if (!model.chanName.equals(getChanName())) throw new IllegalArgumentException("wrong chan");
        StringBuilder url = new StringBuilder(getUsingUrl());
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                if (model.boardName.isEmpty()) throw new IllegalArgumentException("wrong board name");
                url.append(model.boardName);
                if (model.boardPage > 0) url.append("/page/").append(model.boardPage);
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                if (model.boardName.isEmpty()) throw new IllegalArgumentException("wrong board name");
                url.append(model.boardName).append("/").append(model.threadNumber);
                if (model.postNumber != null && model.postNumber.length() > 0) url.append("#").append(model.postNumber);
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
        String path = UrlPathUtils.getUrlPath(url, getAllDomains());
        if (path == null) throw new IllegalArgumentException("wrong domain");
        UrlPageModel model = new UrlPageModel();
        model.chanName = getChanName();
        
        if (path.length() == 0) {
            model.type = UrlPageModel.TYPE_INDEXPAGE;
            return model;
        }
        
        Matcher threadPage = Pattern.compile("^([^/]+)/(\\d+)(?:#(\\d+))?").matcher(path);
        if (threadPage.find()) {
            model.type = UrlPageModel.TYPE_THREADPAGE;
            model.boardName = threadPage.group(1);
            model.threadNumber = threadPage.group(2);
            model.postNumber = threadPage.group(3);
            return model;
        }
        
        Matcher boardPage = Pattern.compile("^([^/]+)(?:/page/(\\d+))?").matcher(path);
        if (boardPage.find()) {
            model.type = UrlPageModel.TYPE_BOARDPAGE;
            model.boardName = boardPage.group(1);
            String page = boardPage.group(2);
            model.boardPage = (page == null) ? 0 : Integer.parseInt(page);
            return model;
        }
        
        model.type = UrlPageModel.TYPE_OTHERPAGE;
        model.otherPath = path;
        return model;
    }
    
    protected static class MonabaAntibot {
        public static List<Pair<String, String>> getFormValues(String url, CancellableTask task, HttpClient httpClient) throws Exception {
            return getFormValues(url, HttpRequestModel.DEFAULT_GET, task, httpClient, "<form class=\"plain-post-form\"", "<div id=\"board-info\"");
        }
        
        public static List<Pair<String, String>> getFormValues(String url, HttpRequestModel requestModel, CancellableTask task, HttpClient client,
                String startForm, String endForm) throws Exception {
            MonabaAntibot reader = null;
            HttpRequestModel request = requestModel;
            HttpResponseModel response = null;
            try {
                response = HttpStreamer.getInstance().getFromUrl(url, request, client, null, task);
                reader = new MonabaAntibot(response.stream, startForm, endForm);
                return reader.readForm();
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (Exception e) {}
                }
                if (response != null) response.release();
            } 
        }
        
        private StringBuilder readBuffer = new StringBuilder();
        private List<Pair<String, String>> result = null;
        
        private String currentName = null;
        private String currentValue = null;
        private String currentType = null;
        private boolean currentTextarea = false;
        private boolean currentReading = false;
        
        private final char[] start;
        private final char[][] filters;
        private final Reader in;
        
        private static final int FILTER_INPUT_OPEN = 0;
        private static final int FILTER_TEXTAREA_OPEN = 1;
        private static final int FILTER_SELECT_OPEN = 2;
        private static final int FILTER_NAME_OPEN = 3;
        private static final int FILTER_VALUE_OPEN = 4;
        private static final int FILTER_TYPE_OPEN = 5;
        private static final int FILTER_CLASS_OPEN = 6;
        private static final int FILTER_TAG_CLOSE = 7;
        
        private MonabaAntibot(InputStream in, String start, String end) {
            this.start = start.toCharArray();
            this.filters = new char[][] {
                    "<input".toCharArray(),
                    "<textarea".toCharArray(),
                    "<select".toCharArray(),
                    "name=\"".toCharArray(),
                    "value=\"".toCharArray(),
                    "type=\"".toCharArray(),
                    "class=\"".toCharArray(),
                    ">".toCharArray(),
                    end.toCharArray()
            };
            this.in = new BufferedReader(new InputStreamReader(in));
        }
        
        private List<Pair<String, String>> readForm() throws IOException {
            result = new ArrayList<>();
            skipUntilSequence(start);
            int filtersCount = filters.length;
            int[] pos = new int[filtersCount];
            int[] len = new int[filtersCount];
            for (int i=0; i<filtersCount; ++i) len[i] = filters[i].length;
            
            int curChar;
            while ((curChar = in.read()) != -1) {
                for (int i=0; i<filtersCount; ++i) {
                    if (curChar == filters[i][pos[i]]) {
                        ++pos[i];
                        if (pos[i] == len[i]) {
                            if (i == filtersCount - 1) {
                                return result;
                            }
                            handleFilter(i);
                            pos[i] = 0;
                        }
                    } else {
                        if (pos[i] != 0) pos[i] = curChar == filters[i][0] ? 1 : 0;
                    }
                }
            }
            return result;
        }
        
        private void handleFilter(int i) throws IOException {
            switch (i) {
                case FILTER_INPUT_OPEN:
                case FILTER_SELECT_OPEN:
                    currentReading = true;
                    currentTextarea = false;
                    break;
                case FILTER_TEXTAREA_OPEN:
                    currentReading = true;
                    currentTextarea = true;
                    break;
                case FILTER_NAME_OPEN:
                    currentName = StringEscapeUtils.unescapeHtml4(readUntilSequence("\"".toCharArray()));
                    break;
                case FILTER_VALUE_OPEN:
                    currentValue = StringEscapeUtils.unescapeHtml4(readUntilSequence("\"".toCharArray()));
                    break;
                case FILTER_CLASS_OPEN:
                case FILTER_TYPE_OPEN:
                    currentType = StringEscapeUtils.unescapeHtml4(readUntilSequence("\"".toCharArray()));
                    break;
                case FILTER_TAG_CLOSE:
                    if (currentTextarea) {
                        currentValue = StringEscapeUtils.unescapeHtml4(readUntilSequence("<".toCharArray()));
                    }
                    if (currentReading && currentName != null) {
                        result.add(Pair.of(currentName, currentValue != null ? currentValue :
                            currentType != null ? currentType : ""));
                    }
                    currentName = null;
                    currentValue = null;
                    currentType = null;
                    currentReading = false;
                    currentTextarea = false;
                    break;
            }
        }
        
        private void skipUntilSequence(char[] sequence) throws IOException {
            int len = sequence.length;
            if (len == 0) return;
            int pos = 0;
            int curChar;
            while ((curChar = in.read()) != -1) {
                if (curChar == sequence[pos]) {
                    ++pos;
                    if (pos == len) break;
                } else {
                    if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
                }
            }
        }
        
        private String readUntilSequence(char[] sequence) throws IOException {
            int len = sequence.length;
            if (len == 0) return "";
            readBuffer.setLength(0);
            int pos = 0;
            int curChar;
            while ((curChar = in.read()) != -1) {
                readBuffer.append((char) curChar);
                if (curChar == sequence[pos]) {
                    ++pos;
                    if (pos == len) break;
                } else {
                    if (pos != 0) pos = curChar == sequence[0] ? 1 : 0;
                }
            }
            int buflen = readBuffer.length();
            if (buflen >= len) {
                readBuffer.setLength(buflen - len);
                return readBuffer.toString();
            } else {
                return "";
            }
        }
        
        public void close() throws IOException {
            in.close();
        }
    }
}
