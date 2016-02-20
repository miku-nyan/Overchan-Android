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

package nya.miku.wishmaster.chans.dvach;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.RegexUtils;

public class DvachSearchReader implements Closeable {
    private final ChanModule _chan;
    private final Reader _in;
    private StringBuilder readBuffer = new StringBuilder();
    private List<PostModel> result;
    
    private static final char[] FILTER_OPEN = "<li class=\"found\">".toCharArray();
    private static final char[] FILTER_CLOSE = "</li>".toCharArray();
    
    private static final Pattern HREF_PATTERN = Pattern.compile("<a href=\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern SUBJ_PATTERN = Pattern.compile("<a(?:[^>]*)>(.*?)</a>", Pattern.DOTALL);
    
    public DvachSearchReader(Reader reader, ChanModule chan) {
        _in = reader;
        _chan = chan;
    }
    
    public DvachSearchReader(InputStream in, ChanModule chan) {
        this(new BufferedReader(new InputStreamReader(in)), chan);
    }
    
    public PostModel[] readSerachPage() throws IOException {
        result = new ArrayList<>();
        
        int pos = 0;
        int len = FILTER_OPEN.length;
        
        int curChar;
        while ((curChar = _in.read()) != -1) {
            if (curChar == FILTER_OPEN[pos]) {
                ++pos;
                if (pos == len) {
                    handlePost(readUntilSequence(FILTER_CLOSE));
                    pos = 0;
                }
            } else {
                if (pos != 0) pos = curChar == FILTER_OPEN[0] ? 1 : 0;
            }
        }
        return result.toArray(new PostModel[result.size()]);
    }
    
    private void handlePost(String post) {
        Matcher mHref = HREF_PATTERN.matcher(post);
        if (mHref.find()) {
            try {
                PostModel postModel = new PostModel();
                UrlPageModel url = _chan.parseUrl(_chan.fixRelativeUrl(mHref.group(1)));
                postModel.number = url.postNumber == null ? url.threadNumber : url.postNumber;
                postModel.parentThread = url.threadNumber;
                postModel.name = "";
                Matcher mSubj = SUBJ_PATTERN.matcher(post);
                if (mSubj.find()) postModel.subject = RegexUtils.trimToSpace(mSubj.group(1)); else postModel.subject = "";
                if (post.contains("<p>")) postModel.comment = post.substring(post.indexOf("<p>")); else postModel.comment = "";
                result.add(postModel);
            } catch (Exception e) {}
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
