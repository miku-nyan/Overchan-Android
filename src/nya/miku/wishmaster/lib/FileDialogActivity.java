/*
 * Исходная версия
 * https://code.google.com/p/android-file-dialog/source/browse/trunk/FileExplorer/src/com/lamerman/FileDialog.java
 * 
 * - Добавлено отображение миниатюр для файлов изображений
 * - Различные иконки для разных типов файлов
 * 
 */
package nya.miku.wishmaster.lib;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.MainApplication;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

/**
 * Activity para escolha de arquivos/diretorios.
 * 
 * @author android
 * 
 */
public class FileDialogActivity extends ListActivity {

    private static final String[] IMAGE_EXTENSIONS = new String[] { "jpg", "jpeg", "png", "gif", "svg", "svgz" };
    private static final String[] VIDEO_EXTENSIONS = new String[] { "webm", "mp4", "avi", "mov", "mkv", "wmv", "flv" };
    private static final String[] AUDIO_EXTENSIONS = new String[] { "mp3", "ogg", "flac", "wav" };
    private static final String[] SAVEDTHREAD_EXTENSIONS = new String[] { "html", "mhtml", "zip" };
    /**
     * Chave de um item da lista de paths.
     */
    private static final String ITEM_KEY = "key";

    /**
     * Imagem de um item da lista de paths (diretorio ou arquivo).
     */
    private static final String ITEM_IMAGE = "image";

    /**
     * Diretorio raiz.
     */
    private static final String ROOT = "/";

    /**
     * Parametro de entrada da Activity: path inicial. Padrao: ROOT.
     */
    public static final String START_PATH = "START_PATH";

    /**
     * Parametro de entrada da Activity: filtro de formatos de arquivos. Padrao:
     * null.
     */
    public static final String FORMAT_FILTER = "FORMAT_FILTER";

    /**
     * Parametro de saida da Activity: path escolhido. Padrao: null.
     */
    public static final String RESULT_PATH = "RESULT_PATH";

    /**
     * Parametro de entrada da Activity: tipo de selecao: pode criar novos paths
     * ou nao. Padrao: nao permite.
     */
    public static final String SELECTION_MODE = "SELECTION_MODE";
    
    public static final int SELECTION_MODE_CREATE = 0;
    public static final int SELECTION_MODE_OPEN = 1;

    /**
     * Parametro de entrada da Activity: se e permitido escolher diretorios.
     * Padrao: falso.
     */
    public static final String CAN_SELECT_DIR = "CAN_SELECT_DIR";

    private List<String> path = null;
    private TextView myPath;
    private EditText mFileName;
    private ArrayList<HashMap<String, Object>> mList;

    private Button selectButton;

    private LinearLayout layoutSelect;
    private LinearLayout layoutCreate;
    private InputMethodManager inputManager;
    private String parentPath;
    private String currentPath = ROOT;

    private int selectionMode = SELECTION_MODE_CREATE;

    private String[] formatFilter = null;

    private boolean canSelectDir = false;

    private File selectedFile;
    private HashMap<String, Integer> lastPositions = new HashMap<String, Integer>();

    /**
     * Called when the activity is first created. Configura todos os parametros
     * de entrada e das VIEWS..
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        MainApplication.getInstance().settings.getTheme().setTo(this);
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED, getIntent());

        setTitle(R.string.filedialog_title);
        setContentView(R.layout.filedialog_main);
        myPath = (TextView) findViewById(R.id.path);
        mFileName = (EditText) findViewById(R.id.filedialog_EditTextFile);

        inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        selectButton = (Button) findViewById(R.id.filedialog_ButtonSelect);
        selectButton.setEnabled(false);
        selectButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (selectedFile != null) {
                    getIntent().putExtra(RESULT_PATH, selectedFile.getPath());
                    setResult(RESULT_OK, getIntent());
                    finish();
                }
            }
        });

        final Button newButton = (Button) findViewById(R.id.filedialog_ButtonNew);
        newButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setCreateVisible(v);

                mFileName.setText("");
                mFileName.requestFocus();
            }
        });

        selectionMode = getIntent().getIntExtra(SELECTION_MODE, SELECTION_MODE_CREATE);

        formatFilter = getIntent().getStringArrayExtra(FORMAT_FILTER);

        canSelectDir = getIntent().getBooleanExtra(CAN_SELECT_DIR, false);

        if (selectionMode == SELECTION_MODE_OPEN) {
            newButton.setEnabled(false);
        }

        layoutSelect = (LinearLayout) findViewById(R.id.filedialog_LinearLayoutSelect);
        layoutCreate = (LinearLayout) findViewById(R.id.filedialog_LinearLayoutCreate);
        layoutCreate.setVisibility(View.GONE);

        final Button cancelButton = (Button) findViewById(R.id.filedialog_ButtonCancel);
        cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setSelectVisible(v);
            }

        });
        final Button createButton = (Button) findViewById(R.id.filedialog_ButtonCreate);
        createButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mFileName.getText().length() > 0) {
                    getIntent().putExtra(RESULT_PATH, currentPath + "/" + mFileName.getText());
                    setResult(RESULT_OK, getIntent());
                    finish();
                }
            }
        });

        String startPath = getIntent().getStringExtra(START_PATH);
        startPath = startPath != null ? startPath : ROOT;
        if (canSelectDir) {
            File file = new File(startPath);
            selectedFile = file;
            selectButton.setEnabled(true);
        }
        getDir(startPath);
    }

    private void getDir(String dirPath) {

        boolean useAutoSelection = dirPath.length() < currentPath.length();

        Integer position = lastPositions.get(parentPath);

        getDirImpl(dirPath);

        if (position != null && useAutoSelection) {
            getListView().setSelection(position);
        }

    }

    /**
     * Monta a estrutura de arquivos e diretorios filhos do diretorio fornecido.
     * 
     * @param dirPath
     *            Diretorio pai.
     */
    private void getDirImpl(final String dirPath) {

        currentPath = dirPath;

        final List<String> item = new ArrayList<String>();
        path = new ArrayList<String>();
        mList = new ArrayList<HashMap<String, Object>>();

        File f = new File(currentPath);
        File[] files = f.listFiles();
        if (files == null) {
            currentPath = ROOT;
            f = new File(currentPath);
            files = f.listFiles();
        }
        myPath.setText(getText(R.string.filedialog_location) + ": " + currentPath);

        if (!currentPath.equals(ROOT)) {

            /*item.add(ROOT);
            addItem(ROOT, R.drawable.filedialog_folder);
            path.add(ROOT);*/

            item.add("../");
            addItem("../", R.drawable.filedialog_folder);
            path.add(f.getParent());
            parentPath = f.getParent();

        }

        TreeMap<String, String> dirsMap = new TreeMap<String, String>();
        TreeMap<String, String> dirsPathMap = new TreeMap<String, String>();
        TreeMap<String, String> filesMap = new TreeMap<String, String>();
        TreeMap<String, String> filesPathMap = new TreeMap<String, String>();
        for (File file : files) {
            if (file.isDirectory()) {
                String dirName = file.getName();
                dirsMap.put(dirName, dirName);
                dirsPathMap.put(dirName, file.getPath());
            } else {
                final String fileName = file.getName();
                final String fileNameLwr = fileName.toLowerCase(Locale.US);
                // se ha um filtro de formatos, utiliza-o
                if (formatFilter != null) {
                    boolean contains = false;
                    for (int i = 0; i < formatFilter.length; i++) {
                        final String formatLwr = formatFilter[i].toLowerCase(Locale.US);
                        if (fileNameLwr.endsWith(formatLwr)) {
                            contains = true;
                            break;
                        }
                    }
                    if (contains) {
                        filesMap.put(fileName, fileName);
                        filesPathMap.put(fileName, file.getPath());
                    }
                    // senao, adiciona todos os arquivos
                } else {
                    filesMap.put(fileName, fileName);
                    filesPathMap.put(fileName, file.getPath());
                }
            }
        }
        item.addAll(dirsMap.tailMap("").values());
        item.addAll(filesMap.tailMap("").values());
        path.addAll(dirsPathMap.tailMap("").values());
        path.addAll(filesPathMap.tailMap("").values());

        SimpleAdapter fileList = new ExtendedSimpleAdapter(this, mList, R.layout.filedialog_row, new String[] {
                ITEM_KEY, ITEM_IMAGE }, new int[] { R.id.filedialog_rowtext, R.id.filedialog_rowimage });

        for (String dir : dirsMap.tailMap("").values()) {
            addItem(dir, R.drawable.filedialog_folder);
        }

        for (String file : filesMap.tailMap("").values()) {
            Bitmap bmp = null;
            if (isImageFile(file)) {
                bmp = getBitmap(filesPathMap.get(file));
            }
            if (bmp != null) {
                addItem(file, bmp);
            } else {
                addItem(file, getDefaultIconResId(file));
            }
        }

        fileList.notifyDataSetChanged();

        setListAdapter(fileList);

    }

    private void addItem(String fileName, int imageId) {
        HashMap<String, Object> item = new HashMap<String, Object>();
        item.put(ITEM_KEY, fileName);
        item.put(ITEM_IMAGE, imageId);
        mList.add(item);
    }
    
    private void addItem(String fileName, Bitmap imageBitmap) {
        HashMap<String, Object> item = new HashMap<String, Object>();
        item.put(ITEM_KEY, fileName);
        item.put(ITEM_IMAGE, imageBitmap);
        mList.add(item);
    }

    /**
     * Quando clica no item da lista, deve-se: 1) Se for diretorio, abre seus
     * arquivos filhos; 2) Se puder escolher diretorio, define-o como sendo o
     * path escolhido. 3) Se for arquivo, define-o como path escolhido. 4) Ativa
     * botao de selecao.
     */
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        File file = new File(path.get(position));

        setSelectVisible(v);

        if (file.isDirectory()) {
            selectButton.setEnabled(false);
            if (file.canRead()) {
                lastPositions.put(currentPath, position);
                getDir(path.get(position));
                if (canSelectDir) {
                    selectedFile = file;
                    v.setSelected(true);
                    selectButton.setEnabled(true);
                }
            } else {
                new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("[" + file.getName() + "]\n" + getText(R.string.filedialog_cant_read_folder))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        }).show();
            }
        } else {
            selectedFile = file;
            v.setSelected(true);
            selectButton.setEnabled(true);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_BACK)) {
            selectButton.setEnabled(false);

            if (layoutCreate.getVisibility() == View.VISIBLE) {
                layoutCreate.setVisibility(View.GONE);
                layoutSelect.setVisibility(View.VISIBLE);
            } else {
                if (!currentPath.equals(ROOT)) {
                    getDir(parentPath);
                } else {
                    return super.onKeyDown(keyCode, event);
                }
            }

            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * Define se o botao de CREATE e visivel.
     * 
     * @param v
     */
    private void setCreateVisible(View v) {
        layoutCreate.setVisibility(View.VISIBLE);
        layoutSelect.setVisibility(View.GONE);

        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        selectButton.setEnabled(false);
    }

    /**
     * Define se o botao de SELECT e visivel.
     * 
     * @param v
     */
    private void setSelectVisible(View v) {
        layoutCreate.setVisibility(View.GONE);
        layoutSelect.setVisibility(View.VISIBLE);

        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
        selectButton.setEnabled(false);
    }
    
    private boolean isImageFile(String file) {
        String fileLwr = file.toLowerCase(Locale.US);
        for (String ext : IMAGE_EXTENSIONS) {
            if (fileLwr.endsWith(ext)) return true;
        }
        return false;
    }
    
    public static int getDefaultIconResId(String file) {
        String fileLwr = file.toLowerCase(Locale.US);
        for (String ext : IMAGE_EXTENSIONS) if (fileLwr.endsWith(ext)) return R.drawable.filedialog_file_image;
        for (String ext : VIDEO_EXTENSIONS) if (fileLwr.endsWith(ext)) return R.drawable.filedialog_file_video;
        for (String ext : AUDIO_EXTENSIONS) if (fileLwr.endsWith(ext)) return R.drawable.filedialog_file_audio;
        for (String ext : SAVEDTHREAD_EXTENSIONS) if (fileLwr.endsWith(ext)) return R.drawable.filedialog_file_html;
        return R.drawable.filedialog_file;
    }
    
    // filename - полный путь к файлу
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
    
    /*
     * SimpleAdapter, корректно обрабатывающий объекты Bitmap к ImageView
     * Взято отсюда:
     * http://stackoverflow.com/questions/6327465/displaying-bitmap-image-in-imageview-by-simple-adapter
     * 
     * miku-nyan
     * 
     */
    private class ExtendedSimpleAdapter extends SimpleAdapter{
        List<? extends Map<String, ?>> map; // if fails to compile, replace with List<HashMap<String, Object>> map
        String[] from;
        int layout;
        int[] to;
        Context context;
        LayoutInflater mInflater;
        public ExtendedSimpleAdapter(Context context, List<? extends Map<String, ?>> data, // if fails to compile, do the same replacement as above on this line
                int resource, String[] from, int[] to) { 
            super(context, data, resource, from, to);
            layout = resource;
            map = data;
            this.from = from;
            this.to = to;
            this.context = context;
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return this.createViewFromResource(position, convertView, parent, layout);
        }

        private View createViewFromResource(int position, View convertView, ViewGroup parent, int resource) {
            View v;
            if (convertView == null) {
                v = mInflater.inflate(resource, parent, false);
            } else {
                v = convertView;
            }
            
            this.bindView(position, v);
            
            return v;
        }
        
        
        private void bindView(int position, View view) {
            final Map<String, ?> dataSet = map.get(position);
            if (dataSet == null) {
                return;
            }
            
            final ViewBinder binder = super.getViewBinder();
            final int count = to.length;
            
            for (int i = 0; i < count; i++) {
                final View v = view.findViewById(to[i]);
                if (v != null) {
                    final Object data = dataSet.get(from[i]);
                    String text = data == null ? "" : data.toString();
                    if (text == null) {
                        text = "";
                    }
                    
                    boolean bound = false;
                    if (binder != null) {
                        bound = binder.setViewValue(v, data, text);
                    }
                    
                    if (!bound) {
                        if (v instanceof Checkable) {
                            if (data instanceof Boolean) {
                                ((Checkable) v).setChecked((Boolean) data);
                            } else if (v instanceof TextView) {
                            //  Note: keep the instanceof TextView check at the bottom of these
                            //  ifs since a lot of views are TextViews (e.g. CheckBoxes).
                                setViewText((TextView) v, text);
                            } else {
                                throw new IllegalStateException(v.getClass().getName() +
                                        " should be bound to a Boolean, not a " +
                                        (data == null ? "<unknown type>" : data.getClass()));
                            }
                        } else if (v instanceof TextView) {
                            // Note: keep the instanceof TextView check at the bottom of these
                            // ifs since a lot of views are TextViews (e.g. CheckBoxes).
                            setViewText((TextView) v, text);
                        } else if (v instanceof ImageView) {
                            if (data instanceof Integer) {
                                setViewImage((ImageView) v, (Integer) data);                            
                            } else if (data instanceof Bitmap){
                                setViewImage((ImageView) v, (Bitmap)data);
                            } else {
                                setViewImage((ImageView) v, text);
                            }
                        } else {
                            throw new IllegalStateException(v.getClass().getName() + " is not a " +
                                    " view that can be bounds by this SimpleAdapter");
                        }
                    }
                }
            }
        }
        
        private void setViewImage(ImageView v, Bitmap bmp){
            v.setImageBitmap(bmp);
        }
        
    }
}
