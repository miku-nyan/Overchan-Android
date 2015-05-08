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

package nya.miku.wishmaster.ui.posting;

import java.io.File;
import java.util.ArrayList;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.CaptchaModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.common.PriorityThreadFactory;
import nya.miku.wishmaster.http.cloudflare.InteractiveException;
import nya.miku.wishmaster.http.streamer.HttpRequestException;
import nya.miku.wishmaster.lib.FileDialogActivity;
import nya.miku.wishmaster.lib.UriFileUtils;
import nya.miku.wishmaster.ui.presentation.ThemeUtils;
import nya.miku.wishmaster.ui.settings.ApplicationSettings;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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
    private CheckBox watermarkChkbox;
    private CheckBox opChkbox;
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
    private Handler handler;
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
            //TODO выделить операции с разметкой в отдельный класс
            case R.id.postform_mark_bold:
            case R.id.postform_mark_italic:
            case R.id.postform_mark_underline:
            case R.id.postform_mark_strike:
            case R.id.postform_mark_spoiler:
            case R.id.postform_mark_quote:
                Editable comment = commentField.getEditableText();
                String text = comment.toString();
                int selectionStart = Math.max(0, commentField.getSelectionStart());
                int selectionEnd = Math.min(text.length(), commentField.getSelectionEnd());
                text = text.substring(selectionStart, selectionEnd);
                
                try {
                    if (boardModel.markType == BoardModel.MARK_WAKABAMARK) {
                        switch (v.getId()) {
                            case R.id.postform_mark_bold:
                                comment.replace(selectionStart, selectionEnd, "**" + text.replace("\n", "**\n**") + "**");
                                commentField.setSelection(selectionStart + 2);
                                break;
                            case R.id.postform_mark_italic:
                                comment.replace(selectionStart, selectionEnd, "*" + text.replace("\n", "*\n*") + "*");
                                commentField.setSelection(selectionStart + 1);
                                break;
                            case R.id.postform_mark_strike:
                                StringBuilder strike = new StringBuilder();
                                for (String s : text.split("\n")) {
                                    strike.append(s);
                                    for (int i=0; i<s.length(); ++i) strike.append("^H");
                                    strike.append('\n');
                                }
                                comment.replace(selectionStart, selectionEnd, strike.substring(0, strike.length() - 1));
                                commentField.setSelection(selectionStart);
                                break;
                            case R.id.postform_mark_spoiler:
                                comment.replace(selectionStart, selectionEnd, "%%" + text.replace("\n", "%%\n%%") + "%%");
                                commentField.setSelection(selectionStart + 2);
                                break;
                            case R.id.postform_mark_quote:
                                comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                                break;
                        }
                    } else if (boardModel.markType == BoardModel.MARK_BBCODE) {
                        switch (v.getId()) {
                            case R.id.postform_mark_bold:
                                comment.replace(selectionStart, selectionEnd, "[b]" + text + "[/b]");
                                commentField.setSelection(selectionStart + 3);
                                break;
                            case R.id.postform_mark_italic:
                                comment.replace(selectionStart, selectionEnd, "[i]" + text + "[/i]");
                                commentField.setSelection(selectionStart + 3);
                                break;
                            case R.id.postform_mark_underline:
                                comment.replace(selectionStart, selectionEnd, "[u]" + text + "[/u]");
                                commentField.setSelection(selectionStart + 3);
                                break;
                            case R.id.postform_mark_strike:
                                comment.replace(selectionStart, selectionEnd, "[s]" + text + "[/s]");
                                commentField.setSelection(selectionStart + 3);
                                break;
                            case R.id.postform_mark_spoiler:
                                comment.replace(selectionStart, selectionEnd, "[spoiler]" + text + "[/spoiler]");
                                commentField.setSelection(selectionStart + 9);
                                break;
                            case R.id.postform_mark_quote:
                                comment.replace(selectionStart, selectionEnd, ">" + text.replace("\n", "\n>"));
                                break;
                        }
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
        setTheme(settings.getTheme());
        super.onCreate(savedInstanceState);
        handler = new Handler();
        attachments = new ArrayList<File>();
        currentPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(PostingService.POSTING_NOTIFICATION_ID);
        
        hash = getIntent().getStringExtra(PostingService.EXTRA_PAGE_HASH);
        boardModel = (BoardModel) getIntent().getSerializableExtra(PostingService.EXTRA_BOARD_MODEL);
        sendPostModel = (SendPostModel) getIntent().getSerializableExtra(PostingService.EXTRA_SEND_POST_MODEL);
        chan = MainApplication.getInstance().getChanModule(sendPostModel.chanName);
        setTitle(sendPostModel.threadNumber == null ? R.string.postform_title_thread : R.string.postform_title_post);
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
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem itemAttach = menu.add(Menu.NONE, R.id.menu_attach_file, 1, R.string.menu_attach_file);
        MenuItem itemGallery = menu.add(Menu.NONE, R.id.menu_attach_gallery, 2, R.string.menu_attach_gallery);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            itemAttach.setIcon(ThemeUtils.getThemeResId(getTheme(), R.attr.actionAddAttachment));
            itemGallery.setIcon(ThemeUtils.getThemeResId(getTheme(), R.attr.actionAddGallery));
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
        PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
            @Override
            public void run() {
                e.handle(PostFormActivity.this, currentTask, new InteractiveException.Callback() {
                    @Override public void onSuccess() { cfDialog.dismiss(); send(); }
                    @Override public void onError(String message) { cfDialog.dismiss(); switchToErrorCaptcha(message); }
                });
            }
        }).start();
    }

    private void setViews() {
        setContentView(R.layout.postform_layout);
        nameLayout = findViewById(R.id.postform_name_email_layout);
        nameField = (EditText) findViewById(R.id.postform_name_field);
        emailField = (EditText) findViewById(R.id.postform_email_field);
        passwordLayout = findViewById(R.id.postform_password_layout);
        passwordField = (EditText) findViewById(R.id.postform_password_field);
        chkboxLayout = findViewById(R.id.postform_sage_op_watermark_layout);
        sageChkbox = (CheckBox) findViewById(R.id.postform_sage_checkbox);
        sageChkbox.setOnClickListener(this);
        watermarkChkbox = (CheckBox) findViewById(R.id.postform_watermark_checkbox);
        opChkbox = (CheckBox) findViewById(R.id.postform_op_checkbox);
        attachmentsLayout = (LinearLayout) findViewById(R.id.postform_attachments_layout);
        spinner = (Spinner) findViewById(R.id.postform_spinner);
        subjectField = (EditText) findViewById(R.id.postform_subject_field);
        commentField = (EditText) findViewById(R.id.postform_comment_field);
        markLayout = (LinearLayout) findViewById(R.id.postform_mark_layout);
        for (int i=0, len=markLayout.getChildCount(); i<len; ++i) markLayout.getChildAt(i).setOnClickListener(this);
        captchaLayout = findViewById(R.id.postform_captcha_layout);
        captchaView = (ImageView) findViewById(R.id.postform_captcha_view);
        captchaView.setOnClickListener(this);
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
        
        markLayout.setVisibility(boardModel.markType != BoardModel.MARK_NOMARK ? View.VISIBLE : View.GONE);
        if (boardModel.markType == BoardModel.MARK_WAKABAMARK) markLayout.findViewById(R.id.postform_mark_underline).setVisibility(View.GONE);
        subjectField.setVisibility(boardModel.allowSubjects ? View.VISIBLE : View.GONE);
        chkboxLayout.setVisibility(boardModel.allowSage || boardModel.allowWatermark || boardModel.allowOpMark ? View.VISIBLE : View.GONE);
        sageChkbox.setVisibility(boardModel.allowSage ? View.VISIBLE : View.GONE);
        watermarkChkbox.setVisibility(boardModel.allowWatermark ? View.VISIBLE : View.GONE);
        opChkbox.setVisibility(boardModel.allowOpMark ? View.VISIBLE : View.GONE);
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
        if (commentField.getText() != null) commentField.setSelection(commentField.getText().length());
        if (boardModel.allowDeletePosts || boardModel.allowDeleteFiles)
            passwordField.setText(sendPostModel.password != null ? sendPostModel.password : "");
        if (boardModel.allowIcons) spinner.setSelection(sendPostModel.icon != -1 ? sendPostModel.icon : 0);
        if (boardModel.allowSage) sageChkbox.setChecked(sendPostModel.sage);
        if (boardModel.ignoreEmailIfSage && boardModel.allowSage && sendPostModel.sage) emailField.setEnabled(false);
        if (boardModel.allowWatermark) watermarkChkbox.setChecked(sendPostModel.watermark);
        if (boardModel.allowOpMark) opChkbox.setChecked(sendPostModel.opmark);
        captchaField.setText(sendPostModel.captchaAnswer != null ? sendPostModel.captchaAnswer : "");
        if (sendPostModel.attachments != null) {
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
        sendPostModel.password = hidePersonalData && (boardModel.allowDeletePosts || boardModel.allowDeleteFiles) ?
                chan.getDefaultPassword() : passwordField.getText().toString();
        sendPostModel.icon = boardModel.allowIcons ? spinner.getSelectedItemPosition() : -1;
        sendPostModel.sage = sageChkbox.isChecked();
        sendPostModel.watermark = watermarkChkbox.isChecked();
        sendPostModel.opmark = opChkbox.isChecked();
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
    
    private void switchToLoadingCaptcha() {
        captchaLoading.setVisibility(View.VISIBLE);
        captchaView.setVisibility(View.GONE);
        captchaField.setEnabled(false);
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
        switchToLoadingCaptcha();
        if (currentTask != null) currentTask.cancel();
        MainApplication.getInstance().draftsCache.clearLastCaptcha();
        PriorityThreadFactory.LOW_PRIORITY_FACTORY.newThread(new Runnable() {
            @Override
            public void run() {
                try {
                    currentTask = new CancellableTask.BaseCancellableTask();
                    final CaptchaModel bmp = chan.getNewCaptcha(sendPostModel.boardName, sendPostModel.threadNumber, null, currentTask);
                    if (currentTask != null && currentTask.isCancelled()) return;
                    handler.post(new Runnable() {
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
                        final String message;
                        if (e instanceof HttpRequestException) {
                            if (((HttpRequestException) e).isSslException()) {
                                message = getString(R.string.error_ssl);
                            } else {
                                message = getString(R.string.error_connection);
                            }
                        } else {
                            message = e.getMessage();
                        }
                        if (currentTask != null && currentTask.isCancelled()) return;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                switchToErrorCaptcha(message);
                            }
                        });
                    }
                }
            }
        }).start();
        
    }
    
    @Override
    public void onPause() {
        saveSendPostModel();
        super.onPause();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentTask != null) currentTask.cancel();
    }
    
}
