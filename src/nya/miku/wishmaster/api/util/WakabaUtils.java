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

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.models.UrlPageModel;

public class WakabaUtils {
    private WakabaUtils() {}
    
    public static String buildUrl(UrlPageModel model, String rootUrl) {
        if (model.boardName != null && !model.boardName.matches("\\w+(/arch)?")) throw new IllegalArgumentException("wrong board name");
        StringBuilder url = new StringBuilder(rootUrl);
        switch (model.type) {
            case UrlPageModel.TYPE_INDEXPAGE:
                break;
            case UrlPageModel.TYPE_BOARDPAGE:
                url.append(model.boardName).append("/");
                if (model.boardPage == UrlPageModel.DEFAULT_FIRST_PAGE) model.boardPage = 0;
                if (model.boardPage != 0) url.append(model.boardPage).append(".html");
                break;
            case UrlPageModel.TYPE_THREADPAGE:
                url.append(model.boardName).append("/res/").append(model.threadNumber).append(".html");
                if (model.postNumber != null && model.postNumber.length() != 0) url.append("#").append(model.postNumber);
                break;
            case UrlPageModel.TYPE_OTHERPAGE:
                url.append(model.otherPath.startsWith("/") ? model.otherPath.substring(1) : model.otherPath);
                break;
            default:
                throw new IllegalArgumentException("wrong page type");
        }
        return url.toString();
    }
    
    public static UrlPageModel parseUrl(String url, String mainDomain, String... altDomains) {
        String domain;
        String path = "";
        Matcher parseUrl = Pattern.compile("https?://(?:www.)?(.+)").matcher(url);
        if (!parseUrl.find()) throw new IllegalArgumentException("incorrect url");
        Matcher parsePath = Pattern.compile("(.+?)(?:/(.*))").matcher(parseUrl.group(1));
        if (parsePath.find()) {
            domain = parsePath.group(1).toLowerCase(Locale.US);
            path = parsePath.group(2);
        } else {
            domain = parseUrl.group(1).toLowerCase(Locale.US);
        }
        
        boolean matchDomain = mainDomain.equals(domain);
        if (!matchDomain) {
            for (String d : altDomains) {
                if (d.equals(domain)) {
                    matchDomain = true;
                    break;
                }
            }
            if (!matchDomain) throw new IllegalArgumentException("wrong chan");
        }
        
        UrlPageModel model = new UrlPageModel();
        model.chanName = mainDomain;
        
        try {
            if (path.contains("/res/")) {
                model.type = UrlPageModel.TYPE_THREADPAGE;
                Matcher matcher = Pattern.compile("(.+?)/res/([0-9]+?).html(.*)").matcher(path);
                if (!matcher.find()) throw new Exception();
                model.boardName = matcher.group(1);
                model.threadNumber = matcher.group(2);
                if (matcher.group(3).startsWith("#")) {
                    String post = matcher.group(3).substring(1);
                    if (!post.equals("")) model.postNumber = post;
                }
            } else {
                model.type = UrlPageModel.TYPE_BOARDPAGE;
                
                if (path.indexOf("/") == -1) {
                    if (path.equals("")) throw new Exception();
                    model.boardName = path;
                    model.boardPage = 0;
                } else {
                    model.boardName = path.substring(0, path.lastIndexOf("/"));
                    
                    String page = path.substring(path.lastIndexOf("/") + 1);
                    if (!page.equals("")) model.boardPage = Integer.parseInt(page.substring(0, page.indexOf(".html")));
                    else model.boardPage = 0;
                }
            }
        } catch (Exception e) {
            if (path == null || path.length() == 0 || path.equals("/")) {
                model.type = UrlPageModel.TYPE_INDEXPAGE;
            } else {
                model.type = UrlPageModel.TYPE_OTHERPAGE;
                model.otherPath = path;
            }
        }
        
        return model;
    }
}
