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

package nya.miku.wishmaster.http.cloudflare;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2;
import nya.miku.wishmaster.http.recaptcha.Recaptcha2solved;
import cz.msebera.android.httpclient.cookie.Cookie;

import java.util.Locale;

import android.app.Activity;

/**
 * UI обработчик Cloudflare-исключений (статический класс)
 * @author miku-nyan
 *
 */

/*package*/ class CloudflareUIHandler {
    private CloudflareUIHandler() {}
    
    /**
     * Обработать исключение-запрос проверки Cloudflare.
     * Вызывать из фонового потока
     * @param e исключение {@link CloudflareException}
     * @param chan модуль чана
     * @param activity активность, в которой будет создан диалог (в случае проверки с капчей),
     * или в контексте которой будет создан WebView для Anti DDOS проверки с javascript.
     * Используется как доступ к UI потоку ({@link Activity#runOnUiThread(Runnable)})
     * @param cfTask отменяемая задача
     * @param callback интерфейс {@link Callback}
     */
    static void handleCloudflare(final CloudflareException e, final HttpChanModule chan, final Activity activity, final CancellableTask cfTask,
            final InteractiveException.Callback callback) {
        if (cfTask.isCancelled()) return;
        
        if (!e.isRecaptcha()) {  // обычная anti DDOS проверка
            if (!CloudflareChecker.getInstance().isAvaibleAntiDDOS()) {
                //если анти ддос проверка уже проводится другим потоком, тогда подождем завершения и объявим успех
                //в случае, если проверка была по тому же ChanModule, проверка уже будет пройдена
                //в противном случае на следующей попытке (закачки) cloudflare выкинет исключение снова
                //и мы сможем обработать исключение для этого чана на свободном CloudflareChecker
                while (!CloudflareChecker.getInstance().isAvaibleAntiDDOS()) Thread.yield();
                if (!cfTask.isCancelled()) activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
                return;
            }
            
            Cookie cfCookie = CloudflareChecker.getInstance().checkAntiDDOS(e, chan.getHttpClient(), cfTask, activity);
            if (cfCookie != null) {
                chan.saveCookie(cfCookie);
                if (!cfTask.isCancelled()) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess();
                        }
                    });
                }
            } else if (!cfTask.isCancelled()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(activity.getString(R.string.error_cloudflare_antiddos));
                    }
                });
            }
        } else {  // проверка с рекапчей
            Recaptcha2.obtain(e.getCheckUrl(), e.getRecaptchaPublicKey(), e.getRecaptchaSecureToken(), chan.getChanName(), e.isRecaptchaFallback()).
                    handle(activity, cfTask, new InteractiveException.Callback() {
                @Override
                public void onSuccess() {
                    PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                        @Override
                        public void run() {
                            String url = String.format(Locale.US, e.getCheckCaptchaUrlFormat(), Recaptcha2solved.pop(e.getRecaptchaPublicKey()));
                            Cookie cfCookie = CloudflareChecker.getInstance().
                                    checkRecaptcha(e, (ExtendedHttpClient) chan.getHttpClient(), cfTask, url);
                            if (cfCookie != null) {
                                chan.saveCookie(cfCookie);
                                if (!cfTask.isCancelled()) {
                                    activity.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onSuccess();
                                        }
                                    });
                                }
                            } else {
                                //печенька не получена (вероятно, ответ неверный, загружаем капчу еще раз)
                                handleCloudflare(e, chan, activity, cfTask, callback);
                            }
                        }
                    }).start();
                }
                @Override
                public void onError(String message) {
                    if (!cfTask.isCancelled()) callback.onError(message);
                }
            });
        }
    }
}
