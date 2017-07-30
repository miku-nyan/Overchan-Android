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

package nya.miku.wishmaster.ui.posting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractChanModule;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.common.Async;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.interactive.InteractiveException;
import nya.miku.wishmaster.lib.FileDialogActivity;
import nya.miku.wishmaster.lib.UriFileUtils;
import nya.miku.wishmaster.ui.CompatibilityImpl;
import nya.miku.wishmaster.ui.CompatibilityUtils;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import nya.miku.wishmaster.ui.theme.ThemeUtils;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class PostFormActivity extends Activity implements View.OnClickListener {
    protected static final String TAG = "PostFormActivity";
    
    private static final int REQUEST_CODE_ATTACH_FILE = 1;
    private static final int REQUEST_CODE_ATTACH_GALLERY = 2;
    
    private ApplicationSettings settings;
    
    private View nameLayout;
    private EditText nameField;
    private EditText emailField;
    private View passwordLayout;
    private EditText passwordField;
    private View chkboxLayout;
    private CheckBox sageChkbox;
    private CheckBox custommarkChkbox;
    private LinearLayout attachmentsLayout;
    private Spinner spinner;
    private EditText subjectField;
    private EditText commentField;
    private LinearLayout markLayout;
    private View captchaLayout;
    private ImageView captchaView;
    private View captchaLoading;
    private EditText captchaField;
    private Button sendButton;
    
    private String hash;
    private BoardModel boardModel;
    private SendPostModel sendPostModel;
    
    private ChanModule chan;
    private volatile CancellableTask currentTask;
    
    private ArrayList<File> attachments;
    private String currentPath;
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.postform_sage_checkbox:
                emailField.setEnabled(!(sageChkbox.isChecked() && boardModel.ignoreEmailIfSage));
                break;
            case R.id.postform_captcha_view:
                updateCaptcha();
                break;
            case R.id.postform_send_button:
                send();
                break;
            case R.id.postform_mark_bold:
            case R.id.postform_mark_italic:
            case R.id.postform_mark_underline:
            case R.id.postform_mark_strike:
            case R.id.postform_mark_spoiler:
            case R.id.postform_mark_quote:
            case R.id.postform_mark_code:
                try {
                    switch (v.getId()) {
                        case R.id.postform_mark_bold:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_BOLD);
                            break;
                        case R.id.postform_mark_italic:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_ITALIC);
                            break;
                        case R.id.postform_mark_underline:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_UNDERLINE);
                            break;
                        case R.id.postform_mark_strike:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_STRIKE);
                            break;
                        case R.id.postform_mark_spoiler:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_SPOILER);
                            break;
                        case R.id.postform_mark_quote:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_QUOTE);
                            break;
                        case R.id.postform_mark_code:
                            PostFormMarkup.markup(boardModel.markType, commentField, PostFormMarkup.FEATURE_CODE);
                            break;
                    }
                } catch (Exception e) {
                    Logger.e(TAG, e);
                }
                break;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = MainApplication.getInstance().settings;
        settings.getTheme().setTo(this);
        super.onCreate(savedInstanceState);
        attachments = new ArrayList<File>();
        currentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(PostingService.POSTING_NOTIFICATION_ID);
        
        hash = getIntent().getStringExtra(PostingService.EXTRA_PAGE_HASH);
        boardModel = (BoardModel) getIntent().getSerializableExtra(PostingService.EXTRA_BOARD_MODEL);
        sendPostModel = (SendPostModel) getIntent().getSerializableExtra(PostingService.EXTRA_SEND_POST_MODEL);
        if (sendPostModel == null) {
            finish();
            return;
        }
        chan = MainApplication.getInstance().getChanModule(sendPostModel.chanName);
        setTitle(sendPostModel.threadNumber == null ? R.string.postform_title_thread : R.string.postform_title_post);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH && chan != null)
            CompatibilityImpl.setActionBarCustomFavicon(this, chan.getChanFavicon());
        setViews();
        readSendPostModel();
        
        if (getIntent().getBooleanExtra(PostingService.EXTRA_RETURN_FROM_SERVICE, false)) {
            int reason = getIntent().getIntExtra(PostingService.EXTRA_RETURN_REASON, 0);
            switch (reason) {
                case PostingService.REASON_ERROR:
                    Toast.makeText(this, getIntent().getStringExtra(PostingService.EXTRA_RETURN_REASON_ERROR), Toast.LENGTH_LONG).show();
                    break;
                case PostingService.REASON_INTERACTIVE_EXCEPTION:
                    handleInteract((InteractiveException) getIntent().getSerializableExtra(PostingService.EXTRA_RETURN_REASON_INTERACTIVE_EXCEPTION));
                    return;
            }
        }
        
        setCaptcha();
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.postform_captcha_view) {
            if (captchaField != null && captchaField.isEnabled()) {
                menu.add(Menu.NONE, R.id.context_menu_save_captcha, 1, R.string.context_menu_save_captcha);
            }
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.context_menu_save_captcha) {
            try {
                Drawable drawable = captchaView.getDrawable();
                if (Math.min(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()) <= 0) throw new Exception("null drawable size"); 
                Bitmap bmp = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
                drawable.draw(new Canvas(bmp));
                
                File dir = new File(settings.getDownloadDirectory(), chan.getChanName());
                if (!dir.mkdirs() && !dir.isDirectory()) throw new Exception("Couldn't create directory");
                int i = 0;
                File file = null;
                do file = new File(dir, "captcha-" + (++i) + ".png"); while (file.exists() || file.isDirectory());
                OutputStream stream = null;
                try {
                    stream = new FileOutputStream(file);
                    if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)) throw new Exception("Couldn't compress bitmap");
                    Toast.makeText(this, "captcha-" + i + ".png", Toast.LENGTH_LONG).show();
                    return true;
                } finally {
                    if (stream != null) stream.close();
                }
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_LONG).show();
                Logger.e(TAG, e);
            }
        }
        return false;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem itemAttach = menu.add(Menu.NONE, R.id.menu_attach_file, 1, R.string.menu_attach_file);
        MenuItem itemGallery = menu.add(Menu.NONE, R.id.menu_attach_gallery, 2, R.string.menu_attach_gallery);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            itemAttach.setIcon(ThemeUtils.getActionbarIcon(getTheme(), getResources(), R.attr.actionAddAttachment));
            itemGallery.setIcon(ThemeUtils.getActionbarIcon(getTheme(), getResources(), R.attr.actionAddGallery));
            CompatibilityImpl.setShowAsActionIfRoom(itemAttach);
            CompatibilityImpl.setShowAsActionIfRoom(itemGallery);
        } else {
            itemAttach.setIcon(R.drawable.ic_menu_attachment);
            itemGallery.setIcon(android.R.drawable.ic_menu_gallery);
        }
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onMenuItemSelected(int featureId, MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.menu_attach_file:
                if (!canAttachOneMore()) {
                    Toast.makeText(this, getString(R.string.postform_max_attachments), Toast.LENGTH_LONG).show();
                    return true;
                }
                if (!CompatibilityUtils.hasAccessStorage(this)) return true;
                Intent selectFile = new Intent(this, FileDialogActivity.class);
                selectFile.putExtra(FileDialogActivity.CAN_SELECT_DIR, false);
                selectFile.putExtra(FileDialogActivity.START_PATH, currentPath);
                selectFile.putExtra(FileDialogActivity.SELECTION_MODE, FileDialogActivity.SELECTION_MODE_OPEN);
                if (boardModel.attachmentsFormatFilters != null) {
                    selectFile.putExtra(FileDialogActivity.FORMAT_FILTER, boardModel.attachmentsFormatFilters);
                }
                startActivityForResult(selectFile, REQUEST_CODE_ATTACH_FILE);
                return true;
            case R.id.menu_attach_gallery:
                if (!canAttachOneMore()) {
                    Toast.makeText(this, getString(R.string.postform_max_attachments), Toast.LENGTH_LONG).show();
                    return true;
                }
                if (!CompatibilityUtils.hasAccessStorage(this)) return true;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");
                startActivityForResult(i, REQUEST_CODE_ATTACH_GALLERY);
                return true;
        }
        return super.onMenuItemSelected(featureId, item);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_ATTACH_FILE:
                    String path = data.getStringExtra(FileDialogActivity.RESULT_PATH);
                    File file = null;
                    if (path != null) {
                        file = new File(path);
                        currentPath = file.getParent();
                    }
                    handleFile(file);
                    break;
                case REQUEST_CODE_ATTACH_GALLERY:
                    Uri imageUri = data.getData();
                    handleFile(UriFileUtils.getFile(this, imageUri));
                    break;
            }
            saveSendPostModel();
        }
    }
    
    private void send() {
        saveSendPostModel();
        if (boardModel.requiredFileForNewThread && sendPostModel.threadNumber == null && sendPostModel.attachments.length == 0) {
            Toast.makeText(this, R.string.postform_required_file, Toast.LENGTH_LONG).show();
        } else if (sendPostModel.comment.length() == 0 && sendPostModel.attachments.length == 0) {
            Toast.makeText(this, R.string.postform_empty_comment, Toast.LENGTH_LONG).show();
        } else {
            MainApplication.getInstance().draftsCache.clearLastCaptcha();
            Intent startPostingService = new Intent(PostFormActivity.this, PostingService.class);
            startPostingService.putExtra(PostingService.EXTRA_PAGE_HASH, hash);
            startPostingService.putExtra(PostingService.EXTRA_SEND_POST_MODEL, sendPostModel);
            startPostingService.putExtra(PostingService.EXTRA_BOARD_MODEL, boardModel);
            finish();
            startService(startPostingService);
        }
    }
    
    private void handleFile(File file) {
        if (!canAttachOneMore()) {
            Toast.makeText(this, getString(R.string.postform_max_attachments), Toast.LENGTH_LONG).show();
            return;
        }
        if (file == null || !file.exists()) {
            Toast.makeText(this, getString(R.string.postform_cannot_attach), Toast.LENGTH_LONG).show();
            return;
        }
        attachments.add(file);
        
        View view = LayoutInflater.from(this).inflate(R.layout.postform_attachment, attachmentsLayout, false);
        ImageView thumbView = (ImageView)view.findViewById(R.id.postform_attachment_thumbnail);
        TextView tvFilename = (TextView)view.findViewById(R.id.postform_attachment_filename);
        TextView tvFileSize = (TextView)view.findViewById(R.id.postform_attachment_size);
        View removeBtn = view.findViewById(R.id.postform_attachment_remove);
        Bitmap thumb = getBitmap(file.getAbsolutePath());
        if (thumb == null) {
            thumbView.setImageResource(FileDialogActivity.getDefaultIconResId(file.getName()));
        } else {
            thumbView.setImageBitmap(thumb);
        }
        tvFilename.setText(file.getName());
        tvFileSize.setText(getImageSizeString(file));
        removeBtn.setTag(view);
        removeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int position = attachmentsLayout.indexOfChild((View)v.getTag());
                attachments.remove(position);
                attachmentsLayout.removeViewAt(position);
            }
        });
        
        attachmentsLayout.addView(view);
    }
    
    private boolean canAttachOneMore() {
        return attachments.size() < boardModel.attachmentsMaxCount;
    }
    
    private Bitmap getBitmap(String filename) {
        int scale = 1;
        int maxDimension = getResources().getDimensionPixelSize(R.dimen.attachment_thumbnail_size);
        
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filename, options);
        
        if (options.outWidth > maxDimension || options.outHeight > maxDimension) {
            double realScale = Math.max(options.outWidth, options.outHeight) / (double)maxDimension;
            double roundedScale = Math.pow(2, Math.ceil(Math.log(realScale) / Math.log(2)));
            scale = (int) roundedScale; // 2, 4, 8, 16
        }

        // Decode with inSampleSize
        options = new BitmapFactory.Options();
        options.inSampleSize = scale;
        
        return BitmapFactory.decodeFile(filename, options);
    }
    
    private String getImageSizeString(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        
        if (options.outWidth == -1 || options.outHeight == -1) {
            return getString(R.string.postform_attachment_size_format_no_image,
                    (int)Math.round(file.length() / 1024.0));
        }
        return getString(R.string.postform_attachment_size_format,
                (int)Math.round(file.length() / 1024.0),
                options.outWidth,
                options.outHeight);
    }
    
    private void handleInteract(final InteractiveException e) {
        if (currentTask != null) currentTask.cancel();
        currentTask = new CancellableTask.BaseCancellableTask();
        final ProgressDialog cfDialog = new ProgressDialog(this, ProgressDialog.STYLE_SPINNER);
        cfDialog.setMessage(getString(R.string.error_interactive_dialog_format, e.getServiceName()));
        cfDialog.setCanceledOnTouchOutside(false);
        cfDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (currentTask != null) currentTask.cancel();
                switchToErrorCaptcha(getString(R.string.error_interactive_cancelled_format, e.getServiceName()));
            }
        });
        cfDialog.show();
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                e.handle(PostFormActivity.this, currentTask, new InteractiveException.Callback() {
                    @Override public void onSuccess() { cfDialog.dismiss(); send(); }
                    @Override public void onError(String message) { cfDialog.dismiss(); switchToErrorCaptcha(message); }
                });
            }
        });
    }

    private void setViews() {
        setContentView(settings.isPinnedMarkup() ? R.layout.postform_layout_pinned_markup : R.layout.postform_layout);
        nameLayout = findViewById(R.id.postform_name_email_layout);
        nameField = (EditText) findViewById(R.id.postform_name_field);
        emailField = (EditText) findViewById(R.id.postform_email_field);
        passwordLayout = findViewById(R.id.postform_password_layout);
        passwordField = (EditText) findViewById(R.id.postform_password_field);
        chkboxLayout = findViewById(R.id.postform_checkbox_layout);
        sageChkbox = (CheckBox) findViewById(R.id.postform_sage_checkbox);
        sageChkbox.setOnClickListener(this);
        custommarkChkbox = (CheckBox) findViewById(R.id.postform_custommark_checkbox);
        attachmentsLayout = (LinearLayout) findViewById(R.id.postform_attachments_layout);
        spinner = (Spinner) findViewById(R.id.postform_spinner);
        subjectField = (EditText) findViewById(R.id.postform_subject_field);
        commentField = (EditText) findViewById(R.id.postform_comment_field);
        markLayout = (LinearLayout) findViewById(R.id.postform_mark_layout);
        for (int i=0, len=markLayout.getChildCount(); i<len; ++i) markLayout.getChildAt(i).setOnClickListener(this);
        captchaLayout = findViewById(R.id.postform_captcha_layout);
        captchaView = (ImageView) findViewById(R.id.postform_captcha_view);
        captchaView.setOnClickListener(this);
        captchaView.setOnCreateContextMenuListener(this);
        captchaLoading = findViewById(R.id.postform_captcha_loading);
        captchaField = (EditText) findViewById(R.id.postform_captcha_field);
        captchaField.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    send();
                    return true;
                }
                return false;
            }
        });
        captchaField.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && chan instanceof AbstractChanModule && ((AbstractChanModule) chan).getCaptchaAutoUpdatePreference()) {
                    if (captchaField.getText().length() == 0)
                        updateCaptcha(false);
                }
            }
        });

        sendButton = (Button) findViewById(R.id.postform_send_button);
        sendButton.setOnClickListener(this);
        
        if (settings.isHidePersonalData()) {
            nameLayout.setVisibility(View.GONE);
            passwordLayout.setVisibility(View.GONE);
        } else {
            nameLayout.setVisibility(boardModel.allowNames || boardModel.allowEmails ? View.VISIBLE : View.GONE);
            nameField.setVisibility(boardModel.allowNames ? View.VISIBLE : View.GONE);
            emailField.setVisibility(boardModel.allowEmails ? View.VISIBLE : View.GONE);
            passwordLayout.setVisibility((boardModel.allowDeletePosts || boardModel.allowDeleteFiles) ? View.VISIBLE : View.GONE);
            
            if (boardModel.allowNames && !boardModel.allowEmails) nameField.setLayoutParams(getWideLayoutParams());
            else if (!boardModel.allowNames && boardModel.allowEmails) emailField.setLayoutParams(getWideLayoutParams());
        }
        
        boolean[] markupEnabled = {
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_QUOTE),
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_BOLD),
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_ITALIC),
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_UNDERLINE),
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_STRIKE),
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_SPOILER),
                PostFormMarkup.hasMarkupFeature(boardModel.markType, PostFormMarkup.FEATURE_CODE),
        };
        if (markupEnabled[0] || markupEnabled[1] || markupEnabled[2] || markupEnabled[3] || markupEnabled[4] || markupEnabled[5]) {
            markLayout.setVisibility(View.VISIBLE);
            if (!markupEnabled[0]) markLayout.findViewById(R.id.postform_mark_quote).setVisibility(View.GONE);
            if (!markupEnabled[1]) markLayout.findViewById(R.id.postform_mark_bold).setVisibility(View.GONE);
            if (!markupEnabled[2]) markLayout.findViewById(R.id.postform_mark_italic).setVisibility(View.GONE);
            if (!markupEnabled[3]) markLayout.findViewById(R.id.postform_mark_underline).setVisibility(View.GONE);
            if (!markupEnabled[4]) markLayout.findViewById(R.id.postform_mark_strike).setVisibility(View.GONE);
            if (!markupEnabled[5]) markLayout.findViewById(R.id.postform_mark_spoiler).setVisibility(View.GONE);
            if (!markupEnabled[6]) markLayout.findViewById(R.id.postform_mark_code).setVisibility(View.GONE);
        } else {
            markLayout.setVisibility(View.GONE);
        }
        
        subjectField.setVisibility(boardModel.allowSubjects ? View.VISIBLE : View.GONE);
        chkboxLayout.setVisibility(boardModel.allowSage || boardModel.allowCustomMark ? View.VISIBLE : View.GONE);
        sageChkbox.setVisibility(boardModel.allowSage ? View.VISIBLE : View.GONE);
        custommarkChkbox.setVisibility(boardModel.allowCustomMark ? View.VISIBLE : View.GONE);
        if (boardModel.customMarkDescription != null) custommarkChkbox.setText(boardModel.customMarkDescription);
        spinner.setVisibility(boardModel.allowIcons ? View.VISIBLE : View.GONE);
        
        if (boardModel.allowIcons) {
            spinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, boardModel.iconDescriptions));
        }
    }
    
    @SuppressLint("InlinedApi")
    private LinearLayout.LayoutParams getWideLayoutParams() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    
    private void readSendPostModel() {
        if (boardModel.allowNames) nameField.setText(sendPostModel.name != null ? sendPostModel.name : "");
        if (boardModel.allowSubjects) subjectField.setText(sendPostModel.subject != null ? sendPostModel.subject : "");
        if (boardModel.allowEmails) emailField.setText(sendPostModel.email != null ? sendPostModel.email : "");
        commentField.setText(sendPostModel.comment != null ? sendPostModel.comment : "");
        if (commentField.getText() != null) {
            int commentPosition = sendPostModel.commentPosition;
            if (commentPosition > commentField.getText().length()) commentPosition = -1;
            if (commentPosition < 0) commentPosition = commentField.getText().length();
            commentField.setSelection(commentPosition);
        }
        if (boardModel.allowDeletePosts || boardModel.allowDeleteFiles)
            passwordField.setText(sendPostModel.password != null ? sendPostModel.password : "");
        if (boardModel.allowIcons) spinner.setSelection(sendPostModel.icon != -1 ? sendPostModel.icon : 0);
        if (boardModel.allowSage) sageChkbox.setChecked(sendPostModel.sage);
        if (boardModel.ignoreEmailIfSage && boardModel.allowSage && sendPostModel.sage) emailField.setEnabled(false);
        if (boardModel.allowCustomMark) custommarkChkbox.setChecked(sendPostModel.custommark);
        captchaField.setText(sendPostModel.captchaAnswer != null ? sendPostModel.captchaAnswer : "");
        if (sendPostModel.attachments != null) {
            attachmentsLayout.removeAllViews();
            attachments.clear();
            for (File attachment : sendPostModel.attachments) {
                handleFile(attachment);
            }
        }
    }
    
    private void saveSendPostModel() {
        boolean hidePersonalData = settings.isHidePersonalData();
        sendPostModel.name = hidePersonalData && boardModel.allowNames ? settings.getDefaultName() : nameField.getText().toString();
        sendPostModel.subject = subjectField.getText().toString();
        sendPostModel.email = hidePersonalData && boardModel.allowEmails ? settings.getDefaultEmail() : emailField.getText().toString();
        sendPostModel.comment = commentField.getText().toString();
        sendPostModel.commentPosition = commentField.getSelectionStart();
        sendPostModel.password = hidePersonalData && (boardModel.allowDeletePosts || boardModel.allowDeleteFiles) ?
                chan.getDefaultPassword() : passwordField.getText().toString();
        sendPostModel.icon = boardModel.allowIcons ? spinner.getSelectedItemPosition() : -1;
        sendPostModel.sage = sageChkbox.isChecked();
        sendPostModel.custommark = custommarkChkbox.isChecked();
        sendPostModel.captchaAnswer = captchaField.getText().toString();
        sendPostModel.attachments = attachments.toArray(new File[attachments.size()]);
        sendPostModel.randomHash = boardModel.allowRandomHash && settings.isRandomHash();
        MainApplication.getInstance().draftsCache.put(hash, sendPostModel);
    }
    
    private void setCaptcha() {
        String lastCaptchaCachedHash = MainApplication.getInstance().draftsCache.getLastCaptchaHash();
        if (lastCaptchaCachedHash != null && lastCaptchaCachedHash.equals(hash)) {
            switchToCaptcha(MainApplication.getInstance().draftsCache.getLastCaptcha(), false);
        } else {
            updateCaptcha();
        }
    }
    
    private void switchToLoadingCaptcha(boolean disableCaptchaField) {
        captchaLoading.setVisibility(View.VISIBLE);
        captchaView.setVisibility(View.GONE);
        captchaField.setEnabled(!disableCaptchaField);
        sendButton.setEnabled(false);
    }
    
    private void switchToCaptcha(CaptchaModel captchaModel) {
        switchToCaptcha(captchaModel, true);
    }
    
    private void switchToCaptcha(CaptchaModel captchaModel, boolean clearField) {
        if (clearField) captchaField.setText("");
        sendButton.setEnabled(true);
        if (captchaModel != null) {
            captchaLoading.setVisibility(View.GONE);
            captchaView.setVisibility(View.VISIBLE);
            captchaView.setImageBitmap(captchaModel.bitmap);
            captchaField.setEnabled(true);
            captchaField.setInputType(
                    captchaModel.type == CaptchaModel.TYPE_NORMAL ?
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD :
                    InputType.TYPE_CLASS_NUMBER );
        } else {
            captchaLayout.setVisibility(View.GONE);
            captchaField.setVisibility(View.GONE);
            sendButton.setLayoutParams(getWideLayoutParams());
        }
    }
    
    private void switchToErrorCaptcha() {
        captchaLoading.setVisibility(View.GONE);
        captchaView.setVisibility(View.VISIBLE);
        captchaView.setImageResource(android.R.drawable.ic_dialog_alert);
        captchaField.setEnabled(false);
        sendButton.setEnabled(false);
    }
    
    private void switchToErrorCaptcha(String message) {
        switchToErrorCaptcha();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void updateCaptcha() {
        updateCaptcha(true);
    }

    private void updateCaptcha(boolean disableCaptchaField) {
        switchToLoadingCaptcha(disableCaptchaField);
        if (currentTask != null) currentTask.cancel();
        MainApplication.getInstance().draftsCache.clearLastCaptcha();
        Async.runAsync(new Runnable() {
            @Override
            public void run() {
                try {
                    currentTask = new CancellableTask.BaseCancellableTask();
                    final CaptchaModel bmp = chan.getNewCaptcha(sendPostModel.boardName, sendPostModel.threadNumber, null, currentTask);
                    if (currentTask != null && currentTask.isCancelled()) return;
                    Async.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            switchToCaptcha(bmp);
                            MainApplication.getInstance().draftsCache.setLastCaptcha(hash, bmp);
                        }
                    });
                } catch (final Exception e) {
                    Logger.e(TAG, e);
                    if (currentTask != null && currentTask.isCancelled()) return;
                    if (e instanceof InteractiveException) {
                        ((InteractiveException) e).handle(PostFormActivity.this, currentTask, new InteractiveException.Callback() {
                            @Override public void onSuccess() { updateCaptcha(); }
                            @Override public void onError(String message) { switchToErrorCaptcha(message); }
                        });
                    } else {
                        final String message = e.getMessage() == null ? "" : e.getMessage();
                        if (currentTask != null && currentTask.isCancelled()) return;
                        Async.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                switchToErrorCaptcha(message);
                            }
                        });
                    }
                }
            }
        });
        
    }
    
    @Override
    public void onPause() {
        saveSendPostModel();
        super.onPause();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        readSendPostModel();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentTask != null) currentTask.cancel();
    }
    
}
