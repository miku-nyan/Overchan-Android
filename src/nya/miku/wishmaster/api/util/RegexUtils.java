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

package nya.miku.wishmaster.api.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexUtils {
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://(?:www\\.)?([^/]+)/?(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern HTML_SPAN_TAGS = Pattern.compile("</?span[^>]*?>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern LINKIFY =
            Pattern.compile("((?:^|\\s|<br/?>)(?:<p>)?)(https?://(?:[^<>\\s]*(?:<wbr/?>)?)*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WBR = Pattern.compile("<wbr/?>", Pattern.CASE_INSENSITIVE);
    
    public static String replaceAll(CharSequence source, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) return source.toString();
        
        StringBuffer buffer = new StringBuffer(source.length());
        do {
            matcher.appendReplacement(buffer, replacement);
        } while (matcher.find());
        return matcher.appendTail(buffer).toString();
    }
    
    public static String removeHtmlTags(CharSequence source) {
        return replaceAll(source, HTML_TAGS, "");
    }
    
    public static String removeHtmlSpanTags(CharSequence source) {
        return replaceAll(source, HTML_SPAN_TAGS, "");
    }
    
    public static String trimToSpace(CharSequence source) {
        return replaceAll(source, SPACES, " ");
    }
    
    public static String linkify(CharSequence source) {
        Matcher matcher = LINKIFY.matcher(source);
        if (!matcher.find()) return source.toString();
        
        StringBuffer buffer = new StringBuffer(source.length());
        do {
            matcher.appendReplacement(buffer, "$1<a href=\"" + replaceAll(matcher.group(2), WBR, "") + "\">$2</a>");
        } while (matcher.find());
        return matcher.appendTail(buffer).toString();
    }
    
    public interface DomainChecker {
        void checkDomain(String domain) throws IllegalArgumentException;
    }
    
    public static String getUrlPath(CharSequence url, DomainChecker checker) {
        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.find()) throw new IllegalArgumentException("incorrect url");
        if (checker != null) checker.checkDomain(matcher.group(1));
        return matcher.group(2);
    }
    
    public static String getUrlPath(CharSequence url, final String... possibleDomains) {
        if (possibleDomains.length == 1) return getUrlPath(url, possibleDomains[0]);
        return getUrlPath(url, new DomainChecker() {
            @Override
            public void checkDomain(String domain) throws IllegalArgumentException {
                boolean matchDomain = false;
                for (String d : possibleDomains) {
                    if (d.equalsIgnoreCase(domain)) {
                        matchDomain = true;
                        break;
                    }
                }
                if (!matchDomain) throw new IllegalArgumentException("wrong domain");
            }
        });
    }
    
    public static String getUrlPath(CharSequence url, String possibleDomain) {
        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.find()) throw new IllegalArgumentException("incorrect url");
        if (possibleDomain != null && !matcher.group(1).equalsIgnoreCase(possibleDomain))
            throw new IllegalArgumentException("wrong domain");
        return matcher.group(2);
    }
    
}
