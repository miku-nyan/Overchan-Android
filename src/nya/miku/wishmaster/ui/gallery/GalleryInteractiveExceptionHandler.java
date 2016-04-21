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

package nya.miku.wishmaster.ui.gallery;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Toast;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;

public class GalleryInteractiveExceptionHandler extends Activity {
    public static final String EXTRA_INTERACTIVE_EXCEPTION = "InteractiveException";
    
    private CancellableTask task;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MainApplication.getInstance().settings.getTheme().setTo(this, R.style.Transparent);
        super.onCreate(savedInstanceState);
        task = new CancellableTask.BaseCancellableTask();
        
        final InteractiveException e = (InteractiveException) getIntent().getSerializableExtra(EXTRA_INTERACTIVE_EXCEPTION);
        if (e == null) {
            finish();
        } else {
            String cfMessage = getString(R.string.error_interactive_dialog_format, ((InteractiveException) e).getServiceName());
            setTitle(cfMessage);
            final ProgressDialog cfProgressDialog = ProgressDialog.show(this, null, cfMessage, true, true, new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    String message = getString(R.string.error_interactive_cancelled_format, ((InteractiveException) e).getServiceName());
                    Toast.makeText(GalleryInteractiveExceptionHandler.this, message, Toast.LENGTH_LONG).show();
                    task.cancel();
                    setResult(RESULT_CANCELED);
                    finish();
                }
            });
            
            Async.runAsync(new Runnable() {
                @Override
                public void run() {
                    e.handle(GalleryInteractiveExceptionHandler.this, task, new InteractiveException.Callback() {
                        @Override
                        public void onSuccess() {
                            if (task.isCancelled()) return;
                            cfProgressDialog.dismiss();
                            setResult(RESULT_OK);
                            finish();
                        }
                        @Override
                        public void onError(String message) {
                            if (task.isCancelled()) return;
                            cfProgressDialog.dismiss();
                            Toast.makeText(GalleryInteractiveExceptionHandler.this, message, Toast.LENGTH_LONG).show();
                            setResult(RESULT_CANCELED);
                            finish();
                        }
                    });
                }
            });
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        task.cancel();
    }
}
