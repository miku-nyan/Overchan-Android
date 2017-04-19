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

package nya.miku.wishmaster.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.lib.org_json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * Работа с базой данных
 * @author miku-nyan
 *
 */
public class Database {
    private static final String TAG = "Database";
    
    private static final int DB_VERSION = 1000;
    private static final String DB_NAME = "database.db";
    
    private static final String TABLE_HIDDEN = "hiddenitems";
    private static final String TABLE_HISTORY = "history";
    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_SAVED = "saved";
    private static final String COL_CHAN = "chan";
    private static final String COL_BOARD = "board";
    private static final String COL_BOARDPAGE = "boardpage";
    private static final String COL_THREAD = "thread";
    private static final String COL_POST = "post";
    private static final String COL_TITLE = "title";
    private static final String COL_URL = "url";
    private static final String COL_DATE = "date";
    private static final String COL_FILEPATH = "filepath";
    
    private static final String NULL = "NULL";
    
    private final DBHelper dbHelper; 
    public Database(Context context) {
        dbHelper = new DBHelper(context);
    }
    
    private static String fixNull(String s) {
        return s != null && s.length() > 0 ? s : NULL;
    }
    
    public static boolean isNull(String s) {
        return s == null || s.equals(NULL);
    }
    
    /* *********************** HIDDEN ITEMS *********************** */
    
    public interface IsHiddenDelegate {
        public boolean isHidden(String chan, String board, String thread, String post);
    }
    
    private IsHiddenDelegate isHiddenDelegate = new IsHiddenDelegate() {
        @Override
        public boolean isHidden(String chan, String board, String thread, String post) {
            return Database.this.isHidden(chan, board, thread, post);
        }
    };
    
    public IsHiddenDelegate getDefaultIsHiddenDelegate() {
        return isHiddenDelegate;
    }
    
    public IsHiddenDelegate getCachedIsHiddenDelegate(final String fchan, final String fboard, final String fthread) {
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_HIDDEN, null,
                COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_THREAD + " = ?",
                new String[] { fixNull(fchan), fixNull(fboard), fixNull(fthread) }, null, null, null);
        
        final List<String> hiddenList;
        if (c != null && c.moveToFirst()) {
            hiddenList = new ArrayList<String>(c.getCount());
            int postIndex = c.getColumnIndex(COL_POST);
            do {
                hiddenList.add(c.getString(postIndex));
            } while (c.moveToNext());
        } else {
            hiddenList = Collections.emptyList();
        }
        if (c != null) c.close();
        return new IsHiddenDelegate() {
            @Override
            public boolean isHidden(String chan, String board, String thread, String post) {
                if (fchan != chan && !fchan.equals(chan)) return false;
                if (fboard != board && !fboard.equals(board)) return false;
                if (fthread != thread && !fthread.equals(thread)) return false;
                return hiddenList.contains(post);
            }
        };
    }

    public void importHidden(HiddenEntry[] hidden, Boolean overwrite){
        if (overwrite)
            clearHidden();
        dbHelper.getWritableDatabase().beginTransaction();
        for (HiddenEntry entry : hidden) {
            addHidden(
                    entry.chan,
                    entry.board,
                    entry.thread,
                    entry.post,
                    !overwrite
            );
        }
        dbHelper.getWritableDatabase().setTransactionSuccessful();
        dbHelper.getWritableDatabase().endTransaction();
    }
    
    public void addHidden(String chan, String board, String thread, String post) {
        addHidden(chan, board, thread, post, true);
    }
    
    public void addHidden(String chan, String board, String thread, String post, Boolean check_existence) {
        if (check_existence) 
            if (isHidden(chan, board, thread, post)) {
                Logger.d(TAG, "entry is already exists");
                return;
            }
        ContentValues value = new ContentValues(4);
        value.put(COL_CHAN, fixNull(chan));
        value.put(COL_BOARD, fixNull(board));
        value.put(COL_THREAD, fixNull(thread));
        value.put(COL_POST, fixNull(post));
        dbHelper.getWritableDatabase().insert(TABLE_HIDDEN, null, value);
    }
    
    public void removeHidden(String chan, String board, String thread, String post) {
        dbHelper.getWritableDatabase().delete(TABLE_HIDDEN,
                COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_THREAD + " = ? AND " + COL_POST + " = ?",
                new String[] { fixNull(chan), fixNull(board), fixNull(thread), fixNull(post) });
    }
    
    public boolean isHidden(String chan, String board, String thread, String post) {
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_HIDDEN, null,
                COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_THREAD + " = ? AND " + COL_POST + " = ?",
                new String[] { fixNull(chan), fixNull(board), fixNull(thread), fixNull(post) }, null, null, null);
        boolean result = false;
        if (c != null && c.moveToFirst()) result = true;
        if (c != null) c.close();
        return result;
    }
    
    public static class HiddenEntry {
        public final String chan;
        public final String board;
        public final String thread;
        public final String post;
        public HiddenEntry(JSONObject json) {
            this.chan = json.getString("chan");
            this.board = json.getString("board");
            this.thread = json.getString("thread");
            this.post = json.getString("post");
        }
        
        public HiddenEntry(String chan, String board, String thread, String post) {
            this.chan = chan;
            this.board = board;
            this.thread = thread;
            this.post = post;
        }
        
        public String getChan(){
            return this.chan;    
        }

        public String getBoard(){
            return this.board;    
        }

        public String getThread(){
            return this.thread;    
        }
        
        public String getPost(){
            return this.post;    
        }
    }

    public List<HiddenEntry> getHidden() {
        List<HiddenEntry> list = new ArrayList<HiddenEntry>();
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_HIDDEN, null, null, null, null, null, null, null);
        if (c != null && c.moveToFirst()) {
            int chanIndex = c.getColumnIndex(COL_CHAN);
            int boardIndex = c.getColumnIndex(COL_BOARD);
            int threadIndex = c.getColumnIndex(COL_THREAD);
            int postIndex = c.getColumnIndex(COL_POST);
            do {
                list.add(new HiddenEntry(c.getString(chanIndex), c.getString(boardIndex),
                        c.getString(threadIndex), c.getString(postIndex)));
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return list;
    }
    
    public void clearHidden(){
        dbHelper.recreateHidden();
    }

    /* *********************** HISTORY *********************** */
    
    public static class HistoryEntry {
        public final String chan;
        public final String board;
        public final String boardPage;
        public final String thread;
        public final String title;
        public final String url;
        public final long date;
        public HistoryEntry(JSONObject json){
            this.chan = json.getString("chan");
            this.board = json.getString("board");
            this.boardPage = json.getString("boardPage");
            this.thread = json.getString("thread");
            this.title = json.getString("title");
            this.url = json.getString("url");
            this.date = json.getLong("date");
        }
        
        public HistoryEntry(String chan, String board, String boardPage, String thread, String title, String url, long date) {
            this.chan = chan;
            this.board = board;
            this.boardPage = boardPage;
            this.thread = thread;
            this.title = title;
            this.url = url;
            this.date = date;
        }

        public String getChan(){
            return this.chan;
        }
        
        public String getBoard(){
            return this.board;
        }
        
        public String getBoardPage(){
            return this.boardPage;
        }
        
        public String getThread(){
            return this.thread;
        }
        
        public String getTitle(){
            return this.title;
        }
        
        public String getUrl(){
            return this.url;
        }
        
        public long   getDate(){
            return this.date;
        }
    }

    public void importHistory(HistoryEntry[] history, Boolean overwrite){
        if (overwrite) 
            clearHistory();
        dbHelper.getWritableDatabase().beginTransaction();
        for (HistoryEntry entry : history) {
            addHistory(
                    entry.chan,
                    entry.board,
                    entry.boardPage,
                    entry.thread,
                    entry.title,
                    entry.url,
                    entry.date,
                    !overwrite
            );
        }
        dbHelper.getWritableDatabase().setTransactionSuccessful();
        dbHelper.getWritableDatabase().endTransaction();
    }
    
    public void addHistory(String chan, String board, String boardPage, String thread, String title, String url) {
        addHistory(chan, board, boardPage, thread, title, url, System.currentTimeMillis(), true);
    }

    public void addHistory(String chan, String board, String boardPage, String thread, String title, String url, Long date, Boolean check_existence) {
        if (check_existence)
            removeHistory(chan, board, boardPage, thread);
        ContentValues values = new ContentValues(6);
        values.put(COL_CHAN, fixNull(chan));
        values.put(COL_BOARD, fixNull(board));
        values.put(COL_BOARDPAGE, fixNull(boardPage));
        values.put(COL_THREAD, fixNull(thread));
        values.put(COL_TITLE, fixNull(title));
        values.put(COL_URL, fixNull(url));
        values.put(COL_DATE, date);
        dbHelper.getWritableDatabase().insert(TABLE_HISTORY, null, values);
    }
    
    public void removeHistory(String chan, String board, String boardPage, String thread) {
        dbHelper.getWritableDatabase().delete(TABLE_HISTORY,
                COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_BOARDPAGE + " = ? AND " + COL_THREAD + " = ?",
                new String[] { fixNull(chan), fixNull(board), fixNull(boardPage), fixNull(thread) });
    }
    
    public List<HistoryEntry> getHistory() {
        List<HistoryEntry> list = new ArrayList<HistoryEntry>();
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_HISTORY, null, null, null, null, null, COL_DATE + " desc", "200");
        if (c != null && c.moveToFirst()) {
            int chanIndex = c.getColumnIndex(COL_CHAN);
            int boardIndex = c.getColumnIndex(COL_BOARD);
            int boardpageIndex = c.getColumnIndex(COL_BOARDPAGE);
            int threadIndex = c.getColumnIndex(COL_THREAD);
            int titleIndex = c.getColumnIndex(COL_TITLE);
            int urlIndex = c.getColumnIndex(COL_URL);
            int dateIndex = c.getColumnIndex(COL_DATE);
            do {
                list.add(new HistoryEntry(c.getString(chanIndex), c.getString(boardIndex), c.getString(boardpageIndex),
                        c.getString(threadIndex), c.getString(titleIndex), c.getString(urlIndex), c.getLong(dateIndex)));
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return list;
    }
    
    public void clearHistory() {
        dbHelper.recreateHistory();
    }
    
    /* *********************** FAVORITES *********************** */
    
    public static class FavoritesEntry {
        public final String chan;
        public final String board;
        public final String boardPage;
        public final String thread;
        public final String title;
        public final String url;
        public FavoritesEntry(JSONObject json) {
            this.chan = json.getString("chan");
            this.board = json.getString("board");
            this.boardPage = json.getString("boardPage");
            this.thread = json.getString("thread");
            this.title = json.getString("title");
            this.url = json.getString("url");
        }

        public FavoritesEntry(String chan, String board, String boardPage, String thread, String title, String url) {
            this.chan = chan;
            this.board = board;
            this.boardPage = boardPage;
            this.thread = thread;
            this.title = title;
            this.url = url;
        }
        
        public String getChan(){
            return this.chan;
        }
        
        public String getBoard(){
            return this.board;
        }
        
        public String getBoardPage(){
            return this.boardPage;
        }
        
        public String getThread(){
            return this.thread;
        }
        
        public String getTitle(){
            return this.title;
        }
        
        public String getUrl(){
            return this.url;
        }
    }
    
    public int getCnf() {
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_FAVORITES, null, null, null, null, null, BaseColumns._ID + " desc");
        int r = c.getCount();
        if (c != null) c.close();
        return r;
    }

    public void importFavorites(FavoritesEntry[] favorite, Boolean overwrite){
        if (overwrite)
            clearFavorites();
        dbHelper.getWritableDatabase().beginTransaction();
        for (FavoritesEntry entry : favorite) {
            addFavorite(
                    entry.chan,
                    entry.board,
                    entry.boardPage,
                    entry.thread,
                    entry.title,
                    entry.url,
                    !overwrite
            );
        }
        dbHelper.getWritableDatabase().setTransactionSuccessful();
        dbHelper.getWritableDatabase().endTransaction();
    }

    public void addFavorite(String chan, String board, String boardPage, String thread, String title, String url) {
        addFavorite(chan, board, boardPage, thread, title, url, true);
    }

    public void addFavorite(String chan, String board, String boardPage, String thread, String title, String url, Boolean check_existence) {
        if (check_existence)
            removeFavorite(chan, board, boardPage, thread);
        ContentValues values = new ContentValues(6);
        values.put(COL_CHAN, fixNull(chan));
        values.put(COL_BOARD, fixNull(board));
        values.put(COL_BOARDPAGE, fixNull(boardPage));
        values.put(COL_THREAD, fixNull(thread));
        values.put(COL_TITLE, fixNull(title));
        values.put(COL_URL, fixNull(url));
        dbHelper.getWritableDatabase().insert(TABLE_FAVORITES, null, values);
    }
    
    public void removeFavorite(String chan, String board, String boardPage, String thread) {
        dbHelper.getWritableDatabase().delete(TABLE_FAVORITES,
                COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_BOARDPAGE + " = ? AND " + COL_THREAD + " = ?",
                new String[] { fixNull(chan), fixNull(board), fixNull(boardPage), fixNull(thread) });
    }
    
    public List<FavoritesEntry> getFavorites() {
        List<FavoritesEntry> list = new ArrayList<FavoritesEntry>();
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_FAVORITES, null, null, null, null, null, BaseColumns._ID + " desc", "200");
        if (c != null && c.moveToFirst()) {
            int chanIndex = c.getColumnIndex(COL_CHAN);
            int boardIndex = c.getColumnIndex(COL_BOARD);
            int boardpageIndex = c.getColumnIndex(COL_BOARDPAGE);
            int threadIndex = c.getColumnIndex(COL_THREAD);
            int titleIndex = c.getColumnIndex(COL_TITLE);
            int urlIndex = c.getColumnIndex(COL_URL);
            do {
                list.add(new FavoritesEntry(c.getString(chanIndex), c.getString(boardIndex), c.getString(boardpageIndex),
                        c.getString(threadIndex), c.getString(titleIndex), c.getString(urlIndex)));
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return list;
    }
    
    public List<String> getFavoriteBoards(ChanModule chan) {
        List<String> list = new ArrayList<>();
        Cursor c =
                dbHelper.getReadableDatabase().query(TABLE_FAVORITES, null, COL_CHAN + " = ? AND " + COL_BOARD + " != ? AND " + COL_THREAD + " = ?",
                new String[] { fixNull(chan.getChanName()), fixNull(null), fixNull(null) }, null, null, BaseColumns._ID + " desc", "200");
        if (c != null && c.moveToFirst()) {
            int boardIndex = c.getColumnIndex(COL_BOARD);
            int boardpageIndex = c.getColumnIndex(COL_BOARDPAGE);
            do {
                String boardName = c.getString(boardIndex);
                String boardPage = c.getString(boardpageIndex);
                if (isFirstPage(chan, boardName, boardPage)) list.add(boardName);
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return list;
    }
    
    private boolean isFirstPage(ChanModule chan, String boardName, String boardPage) {
        try {
            UrlPageModel urlModel = new UrlPageModel();
            urlModel.chanName = chan.getChanName();
            urlModel.type = UrlPageModel.TYPE_BOARDPAGE;
            urlModel.boardName = boardName;
            urlModel.boardPage = UrlPageModel.DEFAULT_FIRST_PAGE;
            if (chan.parseUrl(chan.buildUrl(urlModel)).boardPage == Integer.parseInt(boardPage)) return true;
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        return false;
    }
    
    public boolean isFavorite(String chan, String board, String boardPage, String thread) {
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_FAVORITES, null,
                COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_BOARDPAGE + " = ? AND " + COL_THREAD + " = ?",
                new String[] { fixNull(chan), fixNull(board), fixNull(boardPage), fixNull(thread) }, null, null, null);
        boolean result = false;
        if (c != null && c.moveToFirst()) result = true;
        if (c != null) c.close();
        return result;
    }
    
    public void clearFavorites() {
        dbHelper.recreateFavorites();
    }
    
    /* *********************** HISTORY & FAVORITES *********************** */
    
    public void updateHistoryFavoritesEntries(String chan, String board, String boardPage, String thread, String title) {
        ContentValues values = new ContentValues(1);
        values.put(COL_TITLE, title);
        for (String table : new String[] { TABLE_HISTORY, TABLE_FAVORITES }) {
            dbHelper.getWritableDatabase().update(table, values, 
                    COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_BOARDPAGE + " = ? AND " + COL_THREAD + " = ?",
                    new String[] { fixNull(chan), fixNull(board), fixNull(boardPage), fixNull(thread) });
        }
    }
    
    /* *********************** SAVED THREADS *********************** */
    
    public class SavedThreadEntry {
        public final String chan;
        public final String title;
        public final String filepath;
        private SavedThreadEntry(String chan, String title, String filepath) {
            this.chan = chan;
            this.title = title;
            this.filepath = filepath;
        }
    }
    
    public void addSavedThread(String chan, String title, String filepath) {
        removeSavedThread(filepath);
        ContentValues values = new ContentValues(3);
        values.put(COL_CHAN, fixNull(chan));
        values.put(COL_TITLE, fixNull(title));
        values.put(COL_FILEPATH, fixNull(filepath));
        dbHelper.getWritableDatabase().insert(TABLE_SAVED, null, values);
    }
    
    public void removeSavedThread(String filepath) {
        dbHelper.getWritableDatabase().delete(TABLE_SAVED, COL_FILEPATH + " = ?", new String[] { fixNull(filepath) });
    }
    
    public List<SavedThreadEntry> getSavedThreads() {
        List<SavedThreadEntry> list = new ArrayList<SavedThreadEntry>();
        Cursor c = dbHelper.getReadableDatabase().query(TABLE_SAVED, null, null, null, null, null, BaseColumns._ID + " desc", null);
        if (c != null && c.moveToFirst()) {
            int chanIndex = c.getColumnIndex(COL_CHAN);
            int titleIndex = c.getColumnIndex(COL_TITLE);
            int filepathIndex = c.getColumnIndex(COL_FILEPATH);
            do {
                list.add(new SavedThreadEntry(c.getString(chanIndex), c.getString(titleIndex), c.getString(filepathIndex)));
            } while (c.moveToNext());
        }
        if (c != null) c.close();
        return list;
    }
    
    /* *********************** DB HELPER *********************** */
    
    private static class DBHelper extends SQLiteOpenHelper implements BaseColumns {
        public DBHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(createTable(TABLE_HIDDEN, new String[] { COL_CHAN, COL_BOARD, COL_THREAD, COL_POST }));
            db.execSQL(createTable(TABLE_HISTORY,
                    new String[] { COL_CHAN, COL_BOARD, COL_BOARDPAGE, COL_THREAD, COL_TITLE, COL_URL, COL_DATE },
                    new String[] { "text", "text", "text", "text", "text", "text", "integer" }));
            db.execSQL(createTable(TABLE_FAVORITES, new String[] { COL_CHAN, COL_BOARD, COL_BOARDPAGE, COL_THREAD, COL_TITLE, COL_URL }));
            db.execSQL(createTable(TABLE_SAVED, new String[] { COL_CHAN, COL_TITLE, COL_FILEPATH }));
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < newVersion) {
                db.execSQL(dropTable(TABLE_HIDDEN));
                db.execSQL(dropTable(TABLE_HISTORY));
                db.execSQL(dropTable(TABLE_FAVORITES));
                db.execSQL(dropTable(TABLE_SAVED));
                onCreate(db);
            }
        }
        
        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
        
        private static String createTable(String tableName, String[] columns) {
            return createTable(tableName, columns, null);
        }
        
        private static String createTable(String tableName, String[] columns, String[] types) {
            StringBuilder sql = new StringBuilder(110).append("create table ").append(tableName).append(" (").
                    append(_ID).append(" integer primary key autoincrement,");
            for (int i=0; i<columns.length; ++i) {
                sql.append(columns[i]).append(' ').append(types == null ? "text" : types[i]).append(',');
            }
            sql.setCharAt(sql.length()-1, ')');
            return sql.append(';').toString();
        }
        
        private static String dropTable(String tableName) {
            return "DROP TABLE IF EXISTS " + tableName;
        }
        
        public void recreateHistory() {
            getWritableDatabase().execSQL(dropTable(TABLE_HISTORY));
            getWritableDatabase().execSQL(createTable(TABLE_HISTORY,
                    new String[] { COL_CHAN, COL_BOARD, COL_BOARDPAGE, COL_THREAD, COL_TITLE, COL_URL, COL_DATE },
                    new String[] { "text", "text", "text", "text", "text", "text", "integer" }));
        }
        
        public void recreateFavorites() {
            getWritableDatabase().execSQL(dropTable(TABLE_FAVORITES));
            getWritableDatabase().execSQL(createTable(TABLE_FAVORITES,
                    new String[] { COL_CHAN, COL_BOARD, COL_BOARDPAGE, COL_THREAD, COL_TITLE, COL_URL }));
        }

        public void recreateHidden() {
            getWritableDatabase().execSQL(dropTable(TABLE_HIDDEN));
            getWritableDatabase().execSQL(createTable(TABLE_HIDDEN, new String[] { COL_CHAN, COL_BOARD, COL_THREAD, COL_POST }));
        }
    }
}
