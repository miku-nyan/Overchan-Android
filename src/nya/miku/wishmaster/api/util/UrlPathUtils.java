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

package nya.miku.wishmaster.api.util;

public class UrlPathUtils {
    private UrlPathUtils() {}
    
    public static String getUrlPath(String url, String[] domains) {
        return getUrlPath(url, null, domains, null);
    }
    
    public static String getUrlPath(String url, Iterable<String> domains) {
        return getUrlPath(url, null, null, domains);
    }
    
    public static String getUrlPath(String url, String firstDomain, String... domains) {
        return getUrlPath(url, firstDomain, domains, null);
    }
    
    public static String getUrlPath(String url, String firstDomain, Iterable<String> domains) {
        return getUrlPath(url, firstDomain, null, domains);
    }
    
    private static String getUrlPath(String url, String firstDomain, String[] domainsArray, Iterable<String> domainsIterable) {
        if (url.length() < 7) return null;
        if (!url.regionMatches(true, 0, "http", 0, 4)) return null;
        int index = Character.toLowerCase(url.charAt(4)) == 's' ? 5 : 4;
        if (!url.startsWith("://", index)) return null;
        index += 3;
        if (url.regionMatches(true, index, "www.", 0, 4)) index += 4;
        if (firstDomain != null) {
            String match = matchDomain(url, index, firstDomain);
            if (match != null) return match;
        }
        if (domainsArray != null) {
            for (String domain : domainsArray) {
                if (domain.equals(firstDomain)) continue;
                String match = matchDomain(url, index, domain);
                if (match != null) return match;
            }
        }
        if (domainsIterable != null) {
            for (String domain : domainsIterable) {
                if (domain.equals(firstDomain)) continue;
                String match = matchDomain(url, index, domain);
                if (match != null) return match;
            }
        }
        return null;
    }
    
    private static String matchDomain(String url, int index, String domain) {
        int domainIndex = domain.startsWith("www.") ? 4 : 0;
        int domainLength = domain.length() - domainIndex;
        if (!url.regionMatches(true, index, domain, domainIndex, domainLength)) return null;
        index += domainLength;
        if (url.length() == index) return "";
        if (url.charAt(index) == '/') return url.substring(index + 1);
        return null;
    }
}
