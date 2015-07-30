package nya.miku.wishmaster.chans.krautchan;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;

import nya.miku.wishmaster.api.models.AttachmentModel;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.ThreadModel;

public class KrautCatalogReader implements Closeable {
    private static final char[] CATALOG_START = "<article".toCharArray();
    
    private static final char[] SECTION_OPEN = "<section>".toCharArray();
    private static final char[] SECTION_CLOSE = "</section>".toCharArray();
    
    private static final int FILTER_THREAD_NUMBER = 0;
    private static final int FILTER_THREAD_TITLE = 1;
    private static final int FILTER_THUMBNAIL = 2;
    private static final int FILTER_OMITTED = 3;
    private static final int FILTER_POST = 4;
    private static final int FILTER_THREAD_END = 5;
    
    public static final char[][] FILTERS_OPEN = {
        "class=\"thread_OP\" id=\"".toCharArray(),
        "<header>".toCharArray(),
        "<img src=\"/thumbnails/".toCharArray(),
        "<span class=\"omitted_text\">".toCharArray(),
        "<div class=\"post_text\">".toCharArray(),
        "</article>".toCharArray()
    };
    
    private static final char[][] FILTERS_CLOSE = {
        "\"".toCharArray(),
        "</header>".toCharArray(),
        "\"".toCharArray(),
        "</span>".toCharArray(),
        null,
        null
    };
    
    private final Reader _in;
    
    private StringBuilder readBuffer = new StringBuilder();
    private List<ThreadModel> threads;
    private ThreadModel currentThread;
    private StringBuilder omittedDigitsBuffer = new StringBuilder();
    
    public KrautCatalogReader(Reader reader) {
        _in = reader;
    }
    
    public KrautCatalogReader(InputStream in) {
        this(new BufferedReader(new InputStreamReader(in)));
    }
    
    public ThreadModel[] readPage() throws IOException {
        threads = new ArrayList<ThreadModel>();
        initThreadModel();
        skipUntilSequence(CATALOG_START);
        readData();
        return threads.toArray(new ThreadModel[threads.size()]);
    }
    
    private void readData() throws IOException {
        int filtersCount = FILTERS_OPEN.length;
        int[] pos = new int[filtersCount];
        int[] len = new int[filtersCount];
        for (int i=0; i<filtersCount; ++i) len[i] = FILTERS_OPEN[i].length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            for (int i=0; i<filtersCount; ++i) {
                if (curChar == FILTERS_OPEN[i][pos[i]]) {
                    ++pos[i];
                    if (pos[i] == len[i]) {
                        handleFilter(i);
                        pos[i] = 0;
                    }
                } else {
                    if (pos[i] != 0) pos[i] = curChar == FILTERS_OPEN[i][0] ? 1 : 0;
                }
            }
        }
        finalizeThread();
    }
    
    private void initThreadModel() {
        currentThread = new ThreadModel();
        currentThread.postsCount = 0;
        currentThread.attachmentsCount = 0;
        currentThread.posts = new PostModel[1];
        currentThread.posts[0] = new PostModel();
        currentThread.posts[0].email = "";
        currentThread.posts[0].trip = "";
        currentThread.posts[0].name = "";
    }
    
    private void finalizeThread() {
        if (currentThread.posts[0].number != null && currentThread.posts[0].number.length() > 0) {
            currentThread.threadNumber = currentThread.posts[0].number;
            currentThread.posts[0].parentThread = currentThread.posts[0].number;
            if (currentThread.posts[0].subject == null) currentThread.posts[0].subject = "";
            if (currentThread.posts[0].comment == null) currentThread.posts[0].comment = "";
            if (currentThread.posts[0].attachments == null) currentThread.posts[0].attachments = new AttachmentModel[0];
            threads.add(currentThread);
        }
        initThreadModel();
    }
    
    private void handleFilter(int filterIndex) throws IOException {
        switch (filterIndex) {
            case FILTER_THREAD_NUMBER:
                currentThread.posts[0].number = readUntilSequence(FILTERS_CLOSE[filterIndex]);
                break;
            case FILTER_THREAD_TITLE:
                currentThread.posts[0].subject = StringEscapeUtils.unescapeHtml4(
                        readUntilSequence(FILTERS_CLOSE[filterIndex]).replaceAll("<[^>]*>", "")).trim();
                break;
            case FILTER_THUMBNAIL:
                AttachmentModel attachment = new AttachmentModel();
                attachment.type = AttachmentModel.TYPE_IMAGE_STATIC;
                attachment.size = -1;
                attachment.width = -1;
                attachment.height = -1;
                attachment.thumbnail = "/thumbnails/" + readUntilSequence(FILTERS_CLOSE[filterIndex]);
                attachment.path = attachment.thumbnail;
                currentThread.posts[0].attachments = new AttachmentModel[] { attachment };
                break;
            case FILTER_OMITTED:
                parseOmittedString(readUntilSequence(FILTERS_CLOSE[filterIndex]));
                break;
            case FILTER_POST:
                skipUntilSequence(SECTION_OPEN);
                currentThread.posts[0].comment = readUntilSequence(SECTION_CLOSE);
                break;
            case FILTER_THREAD_END:
                finalizeThread();
                break;
        }
    }
    
    private void parseOmittedString(String omitted) {
        int postsOmitted = -1;
        try {
            int len = omitted.length();
            for (int i=0; i<=len; ++i) {
                char ch = i == len ? ' ' : omitted.charAt(i);
                if (ch >= '0' && ch <= '9') {
                    omittedDigitsBuffer.append(ch);
                } else {
                    if (omittedDigitsBuffer.length() > 0) {
                        int parsedValue = Integer.parseInt(omittedDigitsBuffer.toString());
                        omittedDigitsBuffer.setLength(0);
                        if (postsOmitted == -1) {
                            postsOmitted = parsedValue;
                            break;
                        }
                    }
                }
            }
        } catch (NumberFormatException e) {}
        if (postsOmitted > 0) currentThread.postsCount = 1 + postsOmitted;
    }
    
    private void skipUntilSequence(char[] sequence) throws IOException {
        int len = sequence.length;
        if (len == 0) return;
        int pos = 0;
        int curChar;
        while ((curChar = _in.read()) != -1) {
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
        while ((curChar = _in.read()) != -1) {
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
    
    @Override
    public void close() throws IOException {
        _in.close();
    }
}
