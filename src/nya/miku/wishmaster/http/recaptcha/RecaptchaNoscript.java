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

package nya.miku.wishmaster.http.recaptcha;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.client.HttpClient;

import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

public class RecaptchaNoscript {
    private static final String TAG = "RecaptchaNoscript";
    
    // сюда передавать открытый ключ
    private static final String RECAPTCHA_CHALLENGE_URL = "://www.google.com/recaptcha/api/challenge?k=";
    // сюда передавать значение Challenge и открытый ключ, чтобы получить новый Challenge с более простой капчей
    private static final String RECAPTCHA_RELOAD_URL = "://www.google.com/recaptcha/api/reload?type=image&c=";
    
    private static final Pattern CHALLENGE_FIRST_PATTERN = Pattern.compile("challenge.?:.?'([\\w-]+)'");
    private static final Pattern CHALLENGE_RELOAD_PATTERN = Pattern.compile("Recaptcha\\.finish_reload\\('(.*?)'");
    
    static String getChallenge(String publicKey, CancellableTask task, HttpClient httpClient, String scheme) throws Exception {
        if (scheme == null) scheme = "http";
        String response = HttpStreamer.getInstance().getStringFromUrl(scheme + RECAPTCHA_CHALLENGE_URL + publicKey,
                HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
        Matcher matcher = CHALLENGE_FIRST_PATTERN.matcher(response);
        if (matcher.find()) {
            String challenge = matcher.group(1);
            try {
                response = HttpStreamer.getInstance().getStringFromUrl(scheme + RECAPTCHA_RELOAD_URL + challenge + "&k=" + publicKey,
                        HttpRequestModel.builder().setGET().build(), httpClient, null, task, false);
                matcher = CHALLENGE_RELOAD_PATTERN.matcher(response);
                if (matcher.find()) {
                    String newChallenge = matcher.group(1);
                    if (newChallenge != null && newChallenge.length() > 0) return newChallenge;
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
            
            return challenge;
        }
        throw new RecaptchaException("can't parse recaptcha challenge answer");
    }
}
