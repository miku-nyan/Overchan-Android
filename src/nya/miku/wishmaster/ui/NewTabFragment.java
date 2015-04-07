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

import java.io.File;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.chans.cirno.MikubaModule;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.FileDialogActivity;
import nya.miku.wishmaster.ui.tabs.LocalHandler;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class NewTabFragment extends Fragment implements AdapterView.OnItemClickListener, View.OnClickListener {
    private static final String TAG = "NewTabFragment";
    private static final int REQUEST_FILE = 500;
    
    private MainActivity activity;
    private Resources resources;
    
    private ListView listView;
    private ChansListAdapter adapter;
    
    private View addressBar;
    private boolean addressBarOpened = false;
    private Button openAddressBar;
    private EditText addressField;
    private Button addressGo;
    private Button openLocal;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activity = (MainActivity) getActivity();
        resources = MainApplication.getInstance().resources;
        adapter = new ChansListAdapter(activity);
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity.setTitle(R.string.tabs_newtab);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) CompatibilityImpl.setActionBarDefaultIcon(activity);
        View v = inflater.inflate(R.layout.newtab_fragment, container, false);
        listView = (ListView) v.findViewById(android.R.id.list);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        registerForContextMenu(listView);
        addressBar = v.findViewById(R.id.newtab_address_bar);
        openAddressBar = (Button) v.findViewById(R.id.newtab_open_address_bar);
        openAddressBar.setOnClickListener(this);
        addressField = (EditText) v.findViewById(R.id.newtab_address_field);
        addressField.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    onClick(v);
                    return true;
                }
                return false;
            }
        });
        addressGo = (Button) v.findViewById(R.id.newtab_address_go);
        addressGo.setOnClickListener(this);
        openLocal = (Button) v.findViewById(R.id.newtab_open_local);
        openLocal.setOnClickListener(this);
        return v;
    }
    
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.newtab_open_address_bar:
                if (addressBarOpened) {
                    hideKeyboard(v);
                    addressBar.setVisibility(View.GONE);
                    addressBarOpened = false;
                } else {
                    addressBar.setVisibility(View.VISIBLE);
                    addressField.requestFocus();
                    showKeyboard(v);
                    addressBarOpened = true;
                }
                break;
            case R.id.newtab_open_local:
                hideKeyboard(v);
                openLocal();
                break;
            case R.id.newtab_address_go:
            case R.id.newtab_address_field:
                String url = addressField.getText().toString();
                if (url.length() == 0) return;
                hideKeyboard(v);
                openNewTab(url);
                break;
        }
    }
    
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        hideKeyboard(listView);
        ChanModule chan = adapter.getItem(position);
        UrlPageModel model = new UrlPageModel();
        model.chanName = chan.getChanName();
        model.type = UrlPageModel.TYPE_INDEXPAGE;
        openNewTab(chan.buildUrl(model));
    }
    
    private void openLocal() {
        final ListAdapter savedThreadsAdapter = new ArrayAdapter<Object>(activity, 0) {
            private static final int HEAD_ITEM = 0;
            private static final int NORMAL_ITEM = 1;
            
            private LayoutInflater inflater = LayoutInflater.from(activity);
            private int drawablePadding = (int) (resources.getDisplayMetrics().density * 5 + 0.5f);
            
            {
                add(new Object());
                for (Database.SavedThreadEntry entity : MainApplication.getInstance().database.getSavedThreads()) {
                    File file = new File(entity.filepath);
                    if (file.exists()) add(entity);
                }
            }
            
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View v;
                if (position == 0) {
                    v = convertView == null ? inflater.inflate(android.R.layout.simple_list_item_1, parent, false) : convertView;
                    TextView tv = (TextView) v.findViewById(android.R.id.text1);
                    tv.setText(R.string.newtab_select_local_file);
                } else {
                    Database.SavedThreadEntry item = (Database.SavedThreadEntry) getItem(position);
                    v = convertView == null ? inflater.inflate(android.R.layout.simple_list_item_2, parent, false) : convertView;
                    TextView t1 = (TextView) v.findViewById(android.R.id.text1);
                    TextView t2 = (TextView) v.findViewById(android.R.id.text2);
                    t1.setSingleLine();
                    t2.setSingleLine();
                    t1.setEllipsize(TextUtils.TruncateAt.END);
                    t2.setEllipsize(TextUtils.TruncateAt.START);
                    t1.setText(item.title);
                    t2.setText(item.filepath);
                    t1.setCompoundDrawablesWithIntrinsicBounds(
                            MainApplication.getInstance().getChanModule(item.chan).getChanFavicon(), null, null, null);
                    t1.setCompoundDrawablePadding(drawablePadding);
                }
                return v;
            }
            
            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int position) {
                return position == 0 ? HEAD_ITEM : NORMAL_ITEM;
            }
        };
        
        if (savedThreadsAdapter.getCount() == 1) {
            selectFile();
            return;
        }
        DialogInterface.OnClickListener listListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    selectFile();
                } else {
                    Database.SavedThreadEntry item = (Database.SavedThreadEntry) savedThreadsAdapter.getItem(which);
                    LocalHandler.open(item.filepath, activity);
                }
            }
        };
        new AlertDialog.Builder(activity).
                setTitle(R.string.newtab_saved_threads_title).
                setAdapter(savedThreadsAdapter, listListener).
                setNegativeButton(android.R.string.cancel, null).
                show();
    }
    
    private void selectFile() {
        Intent selectFile = new Intent(activity, FileDialogActivity.class);
        selectFile.putExtra(FileDialogActivity.SELECTION_MODE, FileDialogActivity.SELECTION_MODE_OPEN);
        selectFile.putExtra(FileDialogActivity.FORMAT_FILTER, new String[] { ".zip", ".mhtml", ".html" });
        selectFile.putExtra(FileDialogActivity.START_PATH, MainApplication.getInstance().settings.getDownloadDirectory().getAbsolutePath());
        startActivityForResult(selectFile, REQUEST_FILE);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_FILE && resultCode == Activity.RESULT_OK) {
            String path = data.getStringExtra(FileDialogActivity.RESULT_PATH);
            LocalHandler.open(path, activity);
        }
    }
    
    private void openNewTab(String url) {
        UrlHandler.open(url, activity);
    }
    
    private void showKeyboard(View v) {
        try {
            ((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).
                    toggleSoftInputFromWindow(v.getWindowToken(), InputMethodManager.SHOW_FORCED, 0);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    private void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    private static class ChansListAdapter extends ArrayAdapter<ChanModule> {
        private LayoutInflater inflater;
        private final int drawablePadding;
        public ChansListAdapter(Activity activity) {
            super(activity, 0);
            inflater = LayoutInflater.from(activity);
            for (ChanModule chan : MainApplication.getInstance().chanModulesList) {
                if (!(chan instanceof MikubaModule)) add(chan);
            }
            drawablePadding = (int) (activity.getResources().getDisplayMetrics().density * 5 + 0.5f);
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ChanModule chan = getItem(position);
            TextView view = (TextView) (convertView == null ? inflater.inflate(android.R.layout.simple_list_item_1, parent, false) : convertView);
            view.setText(chan.getDisplayingName());
            view.setCompoundDrawablesWithIntrinsicBounds(chan.getChanFavicon(), null, null, null);
            view.setCompoundDrawablePadding(drawablePadding);
            return view;
        }
    }
}
