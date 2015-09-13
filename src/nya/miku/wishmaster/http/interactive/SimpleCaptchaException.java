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

package nya.miku.wishmaster.http.interactive;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.cloudflare.InteractiveException;

public abstract class SimpleCaptchaException extends InteractiveException {
    private static final long serialVersionUID = 1L;
    
    @Override
    public String getServiceName() {
        return "Captcha";
    }
    
    abstract protected Bitmap getNewCaptcha() throws Exception;
    abstract protected void storeResponse(String response);
    
    protected String getCancelledMessage() {
        return "Cancelled";
    }
    
    protected String getErrorMessage(Exception e) {
        return "Couldn't get captcha";
    }
    
    @Override
    public void handle(final Activity activity, final CancellableTask task, final Callback callback) {
        try {
            final Bitmap captcha = getNewCaptcha();
            if (!task.isCancelled()) activity.runOnUiThread(new Runnable() {
                @SuppressLint("InlinedApi")
                @Override
                public void run() {
                    LinearLayout layout = new LinearLayout(activity);
                    layout.setOrientation(LinearLayout.VERTICAL);
                    ImageView captchaView = new ImageView(activity);
                    int height = (int) (activity.getResources().getDisplayMetrics().density * 90 + 0.5f);
                    captchaView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height));
                    captchaView.setImageBitmap(captcha);
                    layout.addView(captchaView);
                    final EditText responseField = new EditText(activity);
                    responseField.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    responseField.setSingleLine();
                    layout.addView(responseField);
                    final AlertDialog recaptchaDialog = new AlertDialog.Builder(activity).setView(layout).
                            setPositiveButton(R.string.dialog_cloudflare_captcha_check, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    storeResponse(responseField.getText().toString());
                                    callback.onSuccess();
                                }
                            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                                @Override
                                public void onCancel(DialogInterface dialog) {
                                    callback.onError(getCancelledMessage());
                                }
                            }).create();
                    recaptchaDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                    recaptchaDialog.setCanceledOnTouchOutside(false);
                    recaptchaDialog.setTitle(getServiceName());
                    recaptchaDialog.show();
                    captchaView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            recaptchaDialog.dismiss();
                            if (task.isCancelled()) return;
                            PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
                                @Override
                                public void run() {
                                    handle(activity, task, callback);
                                }
                            }).start();
                        }
                    });
                }
            });
        } catch (final Exception e) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    callback.onError(getErrorMessage(e));
                }
            });
        }
    }
    
}
