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

package nya.miku.wishmaster.http.recaptcha;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nya.miku.wishmaster.api.HttpChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;
import nya.miku.wishmaster.lib.base64.Base64;
import nya.miku.wishmaster.ui.AppearanceUtils;
import nya.miku.wishmaster.ui.CompatibilityUtils;

import org.apache.commons.lang3.tuple.Pair;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.client.HttpClient;
import cz.msebera.android.httpclient.client.entity.UrlEncodedFormEntity;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class Recaptcha2fallback extends InteractiveException {
    private static final long serialVersionUID = 1L;
    
    private static final String TAG = "Recaptcha2fallback";
    private static Pair<String, String> lastChallenge = null;
    
    private static final String RECAPTCHA_FALLBACK_URL = "://www.google.com/recaptcha/api/fallback?k=";
    private static final String RECAPTCHA_IMAGE_URL = "://www.google.com/recaptcha/api2/payload?c=";
    
    private String chanName;
    private String scheme;
    private String baseUrl;
    private String publicKey;
    private String sToken;
    
    @Override
    public String getServiceName() {
        return "Recaptcha (fallback)";
    }
    
    /**
     * @param baseUrl URL, с которого должна открываться капча
     * @param publicKey открытый ключ
     * @param sToken Secure Token
     * @param chanName название модуля чана (модуль должен имплементировать {@link HttpChanModule})
     */
    public Recaptcha2fallback(String baseUrl, String publicKey, String sToken, String chanName) {
        this.chanName = chanName;
        this.scheme = "https";
        this.baseUrl = baseUrl;
        this.publicKey = publicKey;
        this.sToken = sToken;
    }
    
    @Override
    public void handle(final Activity activity, final CancellableTask task, final Callback callback) {
        try {
            final HttpClient httpClient = ((HttpChanModule) MainApplication.getInstance().getChanModule(chanName)).getHttpClient();
            final String usingURL = scheme + RECAPTCHA_FALLBACK_URL + publicKey +
                    (sToken != null && sToken.length() > 0 ? ("&stoken=" + sToken) : "");
            String refererURL = baseUrl != null && baseUrl.length() > 0 ? baseUrl : usingURL;
            Header[] customHeaders = new Header[] { new BasicHeader(HttpHeaders.REFERER, refererURL) };
            String htmlChallenge;
            if (lastChallenge != null && lastChallenge.getLeft().equals(usingURL)) {
                htmlChallenge = lastChallenge.getRight();
            } else {
                htmlChallenge = HttpStreamer.getInstance().getStringFromUrl(usingURL,
                        HttpRequestModel.builder().setGET().setCustomHeaders(customHeaders).build(), httpClient, null, task, false);
            }
            lastChallenge = null;
            
            Matcher challengeMatcher = Pattern.compile("name=\"c\" value=\"([\\w-]+)").matcher(htmlChallenge);
            if (challengeMatcher.find()) {
                final String challenge = challengeMatcher.group(1);
                HttpResponseModel responseModel = HttpStreamer.getInstance().getFromUrl(scheme + RECAPTCHA_IMAGE_URL + challenge + "&k=" + publicKey,
                        HttpRequestModel.builder().setGET().setCustomHeaders(customHeaders).build(), httpClient, null, task);
                try {
                    InputStream imageStream = responseModel.stream;
                    final Bitmap challengeBitmap = BitmapFactory.decodeStream(imageStream);
                    
                    final String message;
                    Matcher messageMatcher = Pattern.compile("imageselect-message(?:.*?)>(.*?)</div>").matcher(htmlChallenge);
                    if (messageMatcher.find()) message = RegexUtils.removeHtmlTags(messageMatcher.group(1)); else message = null;
                    
                    final Bitmap candidateBitmap;
                    Matcher candidateMatcher = Pattern.compile("fbc-imageselect-candidates(?:.*?)src=\"data:image/(?:.*?);base64,([^\"]*)\"").
                            matcher(htmlChallenge);
                    if (candidateMatcher.find()) {
                        Bitmap bmp = null;
                        try {
                            byte[] imgData = Base64.decode(candidateMatcher.group(1), Base64.DEFAULT);
                            bmp = BitmapFactory.decodeByteArray(imgData, 0, imgData.length);
                        } catch (Exception e) {}
                        candidateBitmap = bmp;
                    } else candidateBitmap = null;
                    
                    activity.runOnUiThread(new Runnable() {
                        final int maxX = 3;
                        final int maxY = 3;
                        final boolean[] isSelected = new boolean[maxX * maxY];
                        
                        @SuppressLint("InlinedApi")
                        @Override
                        public void run() {
                            LinearLayout rootLayout = new LinearLayout(activity);
                            rootLayout.setOrientation(LinearLayout.VERTICAL);
                            
                            if (candidateBitmap != null) {
                                ImageView candidateView = new ImageView(activity);
                                candidateView.setImageBitmap(candidateBitmap);
                                int picSize = (int) (activity.getResources().getDisplayMetrics().density * 50 + 0.5f);
                                candidateView.setLayoutParams(new LinearLayout.LayoutParams(picSize, picSize));
                                candidateView.setScaleType(ImageView.ScaleType.FIT_XY);
                                rootLayout.addView(candidateView);
                            }
                            
                            if (message != null) {
                                TextView textView = new TextView(activity);
                                textView.setText(message);
                                CompatibilityUtils.setTextAppearance(textView, android.R.style.TextAppearance);
                                textView.setLayoutParams(new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                                rootLayout.addView(textView);
                            }
                            
                            FrameLayout frame = new FrameLayout(activity);
                            frame.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            
                            final ImageView imageView = new ImageView(activity);
                            imageView.setLayoutParams(new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                            imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                            imageView.setImageBitmap(challengeBitmap);
                            frame.addView(imageView);
                            
                            final LinearLayout selector = new LinearLayout(activity);
                            selector.setLayoutParams(new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                            AppearanceUtils.callWhenLoaded(imageView, new Runnable() {
                                @Override
                                public void run() {
                                    selector.setLayoutParams(new FrameLayout.LayoutParams(imageView.getWidth(), imageView.getHeight()));
                                }
                            });
                            selector.setOrientation(LinearLayout.VERTICAL);
                            selector.setWeightSum(maxY);
                            for (int y=0; y<maxY; ++y) {
                                LinearLayout subSelector = new LinearLayout(activity);
                                subSelector.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
                                subSelector.setOrientation(LinearLayout.HORIZONTAL);
                                subSelector.setWeightSum(maxX);
                                for (int x=0; x<maxX; ++x) {
                                    FrameLayout switcher = new FrameLayout(activity);
                                    switcher.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
                                    switcher.setTag(new int[] { x, y });
                                    switcher.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            int[] coord = (int[]) v.getTag();
                                            int index = coord[1] * maxX + coord[0];
                                            isSelected[index] = !isSelected[index];
                                            v.setBackgroundColor(isSelected[index] ? Color.argb(128, 0, 255, 0) : Color.TRANSPARENT);
                                        }
                                    });
                                    subSelector.addView(switcher);
                                }
                                selector.addView(subSelector);
                            }
                            
                            frame.addView(selector);
                            rootLayout.addView(frame);
                            
                            Button checkButton = new Button(activity);
                            checkButton.setLayoutParams(new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                            checkButton.setText(android.R.string.ok);
                            rootLayout.addView(checkButton);
                            
                            ScrollView dlgView = new ScrollView(activity);
                            dlgView.addView(rootLayout);
                            
                            final Dialog dialog = new Dialog(activity);
                            dialog.setTitle("Recaptcha");
                            dialog.setContentView(dlgView);
                            dialog.setCanceledOnTouchOutside(false);
                            dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    if (!task.isCancelled()) {
                                        callback.onError("Cancelled");
                                    }
                                }
                            });
                            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            dialog.show();
                            
                            
                            checkButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    dialog.dismiss();
                                    if (task.isCancelled()) return;
                                    PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                List<NameValuePair> pairs = new ArrayList<NameValuePair>();
                                                pairs.add(new BasicNameValuePair("c", challenge));
                                                for (int i=0; i<isSelected.length; ++i)
                                                    if (isSelected[i]) pairs.add(new BasicNameValuePair("response", Integer.toString(i)));
                                                
                                                HttpRequestModel request = HttpRequestModel.builder().
                                                        setPOST(new UrlEncodedFormEntity(pairs, "UTF-8")).
                                                        setCustomHeaders(new Header[] {
                                                                new BasicHeader(HttpHeaders.REFERER, usingURL)
                                                        }).build();
                                                String response = HttpStreamer.getInstance().
                                                        getStringFromUrl(usingURL, request, httpClient, null, task, false);
                                                String hash = "";
                                                Matcher matcher = Pattern.compile("fbc-verification-token(?:.*?)<textarea[^>]*>([^<]*)<",
                                                        Pattern.DOTALL).matcher(response);
                                                if (matcher.find()) hash = matcher.group(1);
                                                
                                                if (hash.length() > 0) {
                                                    Recaptcha2solved.push(publicKey, hash);
                                                    activity.runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            callback.onSuccess();
                                                        }
                                                    });
                                                } else {
                                                    lastChallenge = Pair.of(usingURL, response);
                                                    throw new RecaptchaException("incorrect answer (hash is empty)");
                                                }
                                            } catch (final Exception e){
                                                Logger.e(TAG, e);
                                                if (task.isCancelled()) return;
                                                handle(activity, task, callback);
                                            }
                                        }
                                    }).start();
                                }
                            });
                        }
                    });
                } finally {
                    responseModel.release();
                }
            } else throw new Exception("can't parse recaptcha challenge answer");
        } catch (final Exception e) {
            Logger.e(TAG, e);
            if (!task.isCancelled()) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(e.getMessage() != null ? e.getMessage() : e.toString());
                    }
                });
            }
        }
    }
    
}
