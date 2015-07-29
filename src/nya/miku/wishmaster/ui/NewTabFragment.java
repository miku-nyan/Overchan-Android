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
import java.util.List;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.CompatibilityImpl;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.FileDialogActivity;
import nya.miku.wishmaster.lib.dslv.DragSortController;
import nya.miku.wishmaster.lib.dslv.DragSortListView;
import nya.miku.wishmaster.ui.tabs.LocalHandler;
import nya.miku.wishmaster.ui.tabs.UrlHandler;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class NewTabFragment extends Fragment implements AdapterView.OnItemClickListener, View.OnClickListener {
    private static final String TAG = "NewTabFragment";
    private static final int REQUEST_FILE = 500;
    
    private MainActivity activity;
    private Resources resources;
    
    private DragSortListView listView;
    private QuickAccessAdapter adapter;
    private List<QuickAccess.Entry> list;
    
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
        list = QuickAccess.getQuickAccessFromPreferences();
        adapter = new QuickAccessAdapter(activity, list);
    }
    
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity.setTitle(R.string.tabs_newtab);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) CompatibilityImpl.setActionBarDefaultIcon(activity);
        View v = inflater.inflate(R.layout.newtab_fragment, container, false);
        listView = (DragSortListView) v.findViewById(android.R.id.list);
        DragSortController controller = new DragSortController(listView, R.id.newtab_quickaccess_drag_handle, DragSortController.ON_DRAG, 0) {
            @Override
            public View onCreateFloatView(int position) { return adapter.getView(position, null, listView); }
            @Override
            public void onDragFloatView(View floatView, Point floatPoint, Point touchPoint) {}
            @Override
            public void onDestroyFloatView(View floatView) {}
        };
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(this);
        listView.setDragEnabled(true);
        listView.setFloatViewManager(controller);
        listView.setOnTouchListener(controller);
        listView.setDropListener(new DragSortListView.DropListener() {
            @Override
            public void drop(int from, int to) {
                if (from != to) {
                    QuickAccess.Entry moved = list.remove(from);
                    list.add(to, moved);
                    adapter.setDraggingItem(-1);
                    saveQuickAccessToPreferences();
                }
            }
        });
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
        adapter.setDraggingItem(-1);
        hideKeyboard(listView);
        QuickAccess.Entry entry = adapter.getItem(position);
        if (entry.chan == null) {
            openChansList();
        } else {
            UrlPageModel model = new UrlPageModel();
            model.chanName = entry.chan.getChanName();
            model.type = entry.boardName == null ? UrlPageModel.TYPE_INDEXPAGE : UrlPageModel.TYPE_BOARDPAGE;
            if (entry.boardName != null) {
                model.boardName = entry.boardName;
                model.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
            }
            openNewTab(entry.chan.buildUrl(model));
        }
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (list.size() > 1) {
            menu.add(Menu.NONE, R.id.context_menu_quickaccess_move, 1, R.string.context_menu_move);
            QuickAccess.Entry entry = adapter.getItem(((AdapterView.AdapterContextMenuInfo) menuInfo).position);
            if (entry.chan == null) return;
            menu.add(Menu.NONE, R.id.context_menu_quickaccess_remove, 2, R.string.context_menu_quickaccess_remove);
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
        switch (item.getItemId()) {
            case R.id.context_menu_quickaccess_move:
                adapter.setDraggingItem(position);
                return true;
            case R.id.context_menu_quickaccess_remove:
                list.remove(position);
                adapter.notifyDataSetChanged();
                saveQuickAccessToPreferences();
                return true;
        }
        return false;
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
                    ChanModule chan = MainApplication.getInstance().getChanModule(item.chan);
                    if (chan != null) {
                        t1.setCompoundDrawablesWithIntrinsicBounds(chan.getChanFavicon(), null, null, null);
                        t1.setCompoundDrawablePadding(drawablePadding);
                    }
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
    
    private static boolean isSingleboardChan(ChanModule chan) {
        try {
            UrlPageModel index = new UrlPageModel();
            index.type = UrlPageModel.TYPE_INDEXPAGE;
            index.chanName = chan.getChanName();
            index = chan.parseUrl(chan.buildUrl(index));
            return index.type != UrlPageModel.TYPE_INDEXPAGE;
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return false;
    }
    
    private void openChansList() {
        final ArrayAdapter<ChanModule> chansAdapter = new ArrayAdapter<ChanModule>(activity, 0) {
            private LayoutInflater inflater = LayoutInflater.from(activity);
            private int drawablePadding = (int) (resources.getDisplayMetrics().density * 5 + 0.5f);
            
            {
                for (ChanModule chan : MainApplication.getInstance().chanModulesList) {
                    if (!MainApplication.getInstance().isLocked(chan.getChanName()) || !isSingleboardChan(chan)) add(chan);
                }
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
        };
        
        DialogInterface.OnClickListener onChanSelected = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ChanModule chan = chansAdapter.getItem(which);
                UrlPageModel model = new UrlPageModel();
                model.chanName = chan.getChanName();
                model.type = UrlPageModel.TYPE_INDEXPAGE;
                openNewTab(chan.buildUrl(model));
            }
        };
        
        final AlertDialog chansListDialog = new AlertDialog.Builder(activity).
                setTitle(R.string.newtab_quickaccess_all_boards).
                setAdapter(chansAdapter, onChanSelected).
                setNegativeButton(android.R.string.cancel, null).
                create();
        
        chansListDialog.getListView().setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
            @Override
            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                MenuItem.OnMenuItemClickListener contextMenuHandler = new MenuItem.OnMenuItemClickListener() {
                    @SuppressLint("InlinedApi")
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        final ChanModule chan = chansAdapter.getItem(((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position);
                        switch (item.getItemId()) {
                            case R.id.context_menu_favorites_from_fragment:
                                if (MainApplication.getInstance().database.isFavorite(chan.getChanName(), null, null, null)) {
                                    MainApplication.getInstance().database.removeFavorite(chan.getChanName(), null, null, null);
                                } else {
                                    try {
                                        UrlPageModel indexPage = new UrlPageModel();
                                        indexPage.chanName = chan.getChanName();
                                        indexPage.type = UrlPageModel.TYPE_INDEXPAGE;
                                        MainApplication.getInstance().database.addFavorite(
                                                chan.getChanName(), null, null, null, chan.getChanName(), chan.buildUrl(indexPage));
                                    } catch (Exception e) {
                                        Logger.e(TAG, e);
                                    }
                                }
                                return true;
                            case R.id.context_menu_quickaccess_add:
                                QuickAccess.Entry newEntry = new QuickAccess.Entry();
                                newEntry.chan = chan;
                                list.add(0, newEntry);
                                adapter.notifyDataSetChanged();
                                saveQuickAccessToPreferences();
                                chansListDialog.dismiss();
                                return true;
                            case R.id.context_menu_quickaccess_custom_board:
                                LinearLayout dialogLayout = new LinearLayout(activity);
                                dialogLayout.setOrientation(LinearLayout.VERTICAL);
                                final EditText boardField = new EditText(activity);
                                final EditText descriptionField = new EditText(activity);
                                boardField.setHint(R.string.newtab_quickaccess_addcustom_boardcode);
                                descriptionField.setHint(R.string.newtab_quickaccess_addcustom_boarddesc);
                                LinearLayout.LayoutParams fieldsParams =
                                        new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                dialogLayout.addView(boardField, fieldsParams);
                                dialogLayout.addView(descriptionField, fieldsParams);
                                DialogInterface.OnClickListener onOkClicked = new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        String boardName = boardField.getText().toString();
                                        for (QuickAccess.Entry entry : list)
                                            if (entry.boardName != null && entry.chan != null)
                                                if (entry.chan.getChanName().equals(chan.getChanName()) && entry.boardName.equals(boardName)) {
                                                    Toast.makeText(activity,
                                                            R.string.newtab_quickaccess_addcustom_already_exists, Toast.LENGTH_LONG).show();
                                                    return;
                                                }
                                        
                                        try {
                                            if (boardName.trim().length() == 0) throw new Exception();
                                            UrlPageModel boardPageModel = new UrlPageModel();
                                            boardPageModel.type = UrlPageModel.TYPE_BOARDPAGE;
                                            boardPageModel.chanName = chan.getChanName();
                                            boardPageModel.boardName = boardName;
                                            boardPageModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
                                            chan.buildUrl(boardPageModel); //проверка, что существование такой доски на данной имиджборде возможно
                                        } catch (Exception e) {
                                            Toast.makeText(activity, R.string.newtab_quickaccess_addcustom_incorrect_code, Toast.LENGTH_LONG).show();
                                            return;
                                        }
                                        
                                        QuickAccess.Entry newEntry = new QuickAccess.Entry();
                                        newEntry.chan = chan;
                                        newEntry.boardName = boardName;
                                        newEntry.boardDescription = descriptionField.getText().toString();
                                        list.add(0, newEntry);
                                        adapter.notifyDataSetChanged();
                                        saveQuickAccessToPreferences();
                                        chansListDialog.dismiss();
                                    }
                                };
                                new AlertDialog.Builder(activity).
                                        setTitle(resources.getString(R.string.newtab_quickaccess_addcustom_title, chan.getChanName())).
                                        setView(dialogLayout).
                                        setPositiveButton(android.R.string.ok, onOkClicked).
                                        setNegativeButton(android.R.string.cancel, null).
                                        show();
                                return true;
                        }
                        return false;
                    }
                };
                String thisChanName = chansAdapter.getItem(((AdapterView.AdapterContextMenuInfo) menuInfo).position).getChanName();
                boolean canAddToQuickAccess = true;
                for (QuickAccess.Entry entry : list)
                    if (entry.boardName == null && entry.chan != null && entry.chan.getChanName().equals(thisChanName)) {
                        canAddToQuickAccess = false;
                        break;
                    }
                menu.add(Menu.NONE, R.id.context_menu_favorites_from_fragment, 1,
                        MainApplication.getInstance().database.isFavorite(thisChanName, null, null, null) ?
                        R.string.context_menu_remove_favorites : R.string.context_menu_add_favorites).
                        setOnMenuItemClickListener(contextMenuHandler);
                menu.add(Menu.NONE, R.id.context_menu_quickaccess_add, 2, R.string.context_menu_quickaccess_add).
                        setOnMenuItemClickListener(contextMenuHandler).
                        setVisible(canAddToQuickAccess);
                menu.add(Menu.NONE, R.id.context_menu_quickaccess_custom_board, 3, R.string.context_menu_quickaccess_custom_board).
                        setOnMenuItemClickListener(contextMenuHandler);
                if (isSingleboardChan(chansAdapter.getItem(((AdapterView.AdapterContextMenuInfo) menuInfo).position)))
                    menu.findItem(R.id.context_menu_quickaccess_custom_board).setVisible(false);
            }
        });
        chansListDialog.show();
    }
    
    private static class QuickAccessAdapter extends ArrayAdapter<QuickAccess.Entry> {
        private Resources resources;
        private LayoutInflater inflater;
        private int drawablePadding;
        
        private int draggingItem = -1;
        
        public QuickAccessAdapter(Activity activity, List<QuickAccess.Entry> list) {
            super(activity, 0, list);
            this.resources = MainApplication.getInstance().resources;
            this.inflater = LayoutInflater.from(activity);
            this.drawablePadding = (int) (resources.getDisplayMetrics().density * 5 + 0.5f);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            QuickAccess.Entry entry = getItem(position);
            View view = (convertView == null ? inflater.inflate(R.layout.newtab_quickaccess_item, parent, false) : convertView);
            TextView tv = (TextView) view.findViewById(R.id.newtab_quickaccess_text);
            if (entry.chan != null) {
                tv.setText(entry.boardName == null ?
                        entry.chan.getDisplayingName() : resources.getString(R.string.boardslist_format, entry.boardName, entry.boardDescription));
                tv.setCompoundDrawablesWithIntrinsicBounds(entry.chan.getChanFavicon(), null, null, null);
                tv.setCompoundDrawablePadding(drawablePadding);
            } else {
                tv.setText(R.string.newtab_quickaccess_all_boards);
                tv.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
            View dragHandler = view.findViewById(R.id.newtab_quickaccess_drag_handle);
            dragHandler.getLayoutParams().width = position == draggingItem ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            dragHandler.setLayoutParams(dragHandler.getLayoutParams());
            return view;
        }
        
        public void setDraggingItem(int position) {
            if (draggingItem != position) {
                draggingItem = position;
                notifyDataSetChanged();
            }
        }
    }
    
    private void saveQuickAccessToPreferences() {
        QuickAccess.saveQuickAccessToPreferences(list);
    }
}
