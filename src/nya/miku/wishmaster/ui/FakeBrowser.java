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

package nya.miku.wishmaster.ui;

import java.lang.ref.WeakReference;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import nya.miku.wishmaster.ui.tabs.UrlHandler;

@SuppressLint("InlinedApi")
public class FakeBrowser {
    private static WeakReference<Dialog> reference;
    
    public static void dismiss() {
        Dialog oldDialog = reference == null ? null : reference.get();
        if (oldDialog != null) oldDialog.dismiss();
    }
    
    public static void openFakeBrowser(final Context context, String url) {
        dismiss();
        if (Uri.parse(url).getScheme() == null) url = "http://" + url;
        
        final Dialog dialog = new Dialog(context);
        WebView webView = new WebView(context);
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (context instanceof MainActivity && UrlHandler.getPageModel(url) != null) {
                    dismiss();
                    UrlHandler.open(url, (MainActivity)context, true);
                } else {
                    view.loadUrl(url);
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (title != null) dialog.setTitle(title);
            }
        });
        
        dialog.setContentView(webView);
        dialog.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        webView.loadUrl(url);
        reference = new WeakReference<>(dialog);
    }
}
