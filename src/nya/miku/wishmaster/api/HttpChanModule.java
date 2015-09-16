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

package nya.miku.wishmaster.api;

import org.apache.http.client.HttpClient;
import org.apache.http.cookie.Cookie;

/**
 * Интерфейс модуля чана, работающего через HTTP и использующего интерфейс Apache {@link HttpClient}
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

public interface HttpChanModule extends ChanModule {
    
    /**
     * Получить HTTP клиент, используемый данным чаном
     */
    HttpClient getHttpClient();
    
    /**
     * Добавить Cookie к HTTP клиенту и сохранить его в параметрах, если это предусмотрено конкретной имиджбордой (напр. в случае Cloudflare)
     */
    void saveCookie(Cookie cookie);
}
