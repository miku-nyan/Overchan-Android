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

package nya.miku.wishmaster.http.cloudflare;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.client.ExtendedHttpClient;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.recaptcha.Recaptcha;
import nya.miku.wishmaster.http.recaptcha.RecaptchaException;

import org.apache.http.cookie.Cookie;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;

/**
 * UI обработчик Cloudflare-исключений (статический класс)
 * @author miku-nyan
 *
 */

/* Google пометила все классы и интерфейсы пакета org.apache.http как "deprecated" в API 22 (Android 5.1)
 * На самом деле используется актуальная версия apache-hc httpclient 4.3.5.1-android
 * Подробности: https://issues.apache.org/jira/browse/HTTPCLIENT-1632 */
@SuppressWarnings("deprecation")

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
            final Recaptcha recaptcha;
            try {
                recaptcha = CloudflareChecker.getInstance().getRecaptcha(e, chan.getHttpClient(), cfTask);
            } catch (RecaptchaException recaptchaException) {
                if (!cfTask.isCancelled()) activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(activity.getString(R.string.error_cloudflare_get_captcha));
                    }
                });
                return;
            }
            
            if (!cfTask.isCancelled()) activity.runOnUiThread(new Runnable() {
                @SuppressLint("InflateParams")
                @Override
                public void run() {
                    Context dialogContext = Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ?
                            new ContextThemeWrapper(activity, R.style.Theme_Neutron) : activity;
                    View view = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_cloudflare_captcha, null);
                    ImageView captchaView = (ImageView) view.findViewById(R.id.dialog_captcha_view);
                    final EditText captchaField = (EditText) view.findViewById(R.id.dialog_captcha_field);
                    captchaView.setImageBitmap(recaptcha.bitmap);
                    
                    DialogInterface.OnClickListener process = new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (cfTask.isCancelled()) return;
                            PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                @Override
                                public void run() {
                                    String answer = captchaField.getText().toString();
                                    Cookie cfCookie = CloudflareChecker.getInstance().
                                            checkRecaptcha(e, (ExtendedHttpClient) chan.getHttpClient(), cfTask, recaptcha.challenge, answer);
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
                    };
                    
                    DialogInterface.OnCancelListener onCancel = new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            callback.onError(activity.getString(R.string.error_cloudflare_cancelled));
                        }
                    };
                    
                    if (cfTask.isCancelled()) return;
                    
                    final AlertDialog recaptchaDialog = new AlertDialog.Builder(dialogContext).setView(view).
                            setPositiveButton(R.string.dialog_cloudflare_captcha_check, process).setOnCancelListener(onCancel).create();
                    recaptchaDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    recaptchaDialog.setCanceledOnTouchOutside(false);
                    recaptchaDialog.show();
                    
                    captchaView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            recaptchaDialog.dismiss();
                            if (cfTask.isCancelled()) return;
                            PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                @Override
                                public void run() {
                                    handleCloudflare(e, chan, activity, cfTask, callback);
                                }
                            }).start();
                        }
                    });
                }
            });
        }
    }
}
