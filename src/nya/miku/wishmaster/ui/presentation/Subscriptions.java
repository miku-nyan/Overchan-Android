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

package nya.miku.wishmaster.ui.presentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringEscapeUtils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import nya.miku.wishmaster.api.models.UrlPageModel;
import nya.miku.wishmaster.api.util.RegexUtils;
import nya.miku.wishmaster.cache.SerializablePage;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONObject;

public class Subscriptions {
    private static final String TAG = "Subscriptions";
    private SubscriptionsDB database;
    private Object[] cached;
    private Object[] waitingOwnPost;
    
    public Subscriptions(Context context) {
        database = new SubscriptionsDB(context);
    }
    
    /**
     * Получить текущее количество подписок (отслеживаемых постов)
     */
    public long getCurrentCount() {
        return database.getNumEntries();
    }
    
    /**
     * Проверить, есть ли на данной странице ответы на отслеживаемые посты
     * @param page страница
     * @param startPostIndex номер поста (по порядку) на странице, начиная с которого требуется проверять
     * @return индекс (номер по порядку) первого ответа на отслеживаемые посты, если таковые есть, в противном случае -1 
     */
    public int checkSubscriptions(SerializablePage page, int startPostIndex) {
        if (!MainApplication.getInstance().settings.isSubscriptionsEnabled()) return -1;
        if (page.pageModel == null || page.pageModel.type != UrlPageModel.TYPE_THREADPAGE || page.posts == null)
            return -1;
        String[] subscriptions = getSubscriptions(page.pageModel.chanName, page.pageModel.boardName, page.pageModel.threadNumber);
        if (subscriptions == null) return -1;
        if (startPostIndex < page.posts.length &&
                MainApplication.getInstance().settings.subscribeThreads() &&
                Arrays.binarySearch(subscriptions, page.pageModel.threadNumber) >= 0)
            return startPostIndex;
        for (int i=startPostIndex; i<page.posts.length; ++i) {
            String comment = page.posts[i].comment;
            if (comment == null) continue;
            Matcher m = PresentationItemModel.REPLY_LINK_FULL_PATTERN.matcher(comment);
            while (m.find()) if (Arrays.binarySearch(subscriptions, m.group(1)) >= 0) return i;
        }
        return -1;
    }
    
    public void importSubscriptions(SubscriptionEntry[] subscription, Boolean overwrite){
        if (overwrite) {
            reset();
            MainApplication.getInstance().settings.setSubscriptionsClear(true);
        }
        database.importSubscriptions(subscription, overwrite);
    }
    
    /**
     * Добавить отслеживаемый пост
     */
    public void addSubscription(String chan, String board, String thread, String post) {
        database.put(chan, board, thread, post, true);
        Object[] tuple = cached;
        if (tuple != null && tuple[0].equals(chan) && tuple[1].equals(board) && tuple[2].equals(thread))
            cached = null;
    }
    
    /**
     * Проверить, является ли пост отслеживаемым
     */
    public boolean hasSubscription(String chan, String board, String thread, String post) {
        return database.hasSubscription(chan, board, thread, post);
    }
    
    /**
     * Удалить отслеживаемый пост
     */
    public void removeSubscription(String chan, String board, String thread, String post) {
        database.remove(chan, board, thread, post);
        Object[] tuple = cached;
        if (tuple != null && tuple[0].equals(chan) && tuple[1].equals(board) && tuple[2].equals(thread))
            cached = null;
    }
    
    /**
     * Получить отсортированный список отслеживаемых постов в данном треде
     * @return массив, отсортированный как массив java.lang.String
     */
    public String[] getSubscriptions(String chan, String board, String thread) {
        Object[] tuple = cached;
        if (tuple != null && tuple[0].equals(chan) && tuple[1].equals(board) && tuple[2].equals(thread))
            return (String[]) tuple[3];
        String[] result = database.getSubscriptions(chan, board, thread);
        if (result == null || result.length == 0) result = null; else Arrays.sort(result);
        cached = new Object[] { chan, board, thread, result };
        return result;
    }
    
    /**
     * Очистить все подписки (отслеживаемые посты)
     */
    public void reset() {
        database.resetDB();
        cached = null;
    }
    
    /**
     * Установить детектор своего поста (для борд, которые не отдают номер своего поста после отправки)
     */
    public void detectOwnPost(String chan, String board, String thread, String comment) {
        if (chan == null || board == null || thread == null || comment == null) return;
        List<String> words = commentToWordsList(comment);
        //Logger.d(TAG, "set detector; words: " + words);
        waitingOwnPost = new Object[] { chan, board, thread, words };
    }
    
    /**
     * Проверить, нет ли на странице своего поста, установленного в {@link #detectOwnPost(String, String, String, String)},
     * если есть, добавить пост к отслеживаемым (подпискам)
     * @param page страница
     * @param startPostIndex номер поста (по порядку) на странице, начиная с которого требуется проверять
     */
    @SuppressWarnings("unchecked")
    public void checkOwnPost(SerializablePage page, int startPostIndex) {
        if (page.pageModel == null || page.pageModel.type != UrlPageModel.TYPE_THREADPAGE || page.posts == null) return;
        String chan = page.pageModel.chanName;
        String board = page.pageModel.boardName;
        String thread = page.pageModel.threadNumber;
        Object[] tuple = waitingOwnPost;
        if (tuple != null && tuple[0].equals(chan) && tuple[1].equals(board) && tuple[2].equals(thread)) {
            waitingOwnPost = null;
            int postCount = page.posts.length - startPostIndex;
            if (postCount <= 1) {
                if (postCount == 1 && page.posts[startPostIndex] != null)
                    addSubscription(chan, board, thread, page.posts[startPostIndex].number);
                return;
            }
            List<int[]> result = new ArrayList<>(postCount);
            List<String> waitingWords = (List<String>) tuple[3];
            for (int i=startPostIndex; i<page.posts.length; ++i) {
                if (page.posts[i] == null || page.posts[i].comment == null) continue;
                HashSet<String> postWords = new HashSet<>(commentToWordsList(htmlToComment(page.posts[i].comment)));
                //Logger.d(TAG, "checking post i=" + i + "\ncomment: " + page.posts[i].comment+"\nwords:" + postWords);
                int wordsCount = 0;
                for (String waitingWord : waitingWords)
                    if (postWords.remove(waitingWord)) ++wordsCount;
                result.add(new int[] { i, wordsCount, postWords.size() });
                //Logger.d(TAG, "result: overlap=" + wordsCount + "; remained=" + postWords.size());
            }
            if (result.size() == 0) return;
            Collections.sort(result, new Comparator<int[]>() {
                @Override
                public int compare(int[] lhs, int[] rhs) {
                    int result = compareInt(rhs[1], lhs[1]);
                    if (result == 0) result = compareInt(lhs[2], rhs[2]);
                    if (result == 0) result = compareInt(lhs[0], rhs[0]);
                    return result;
                }
                private int compareInt(int lhs, int rhs) {
                    return lhs < rhs ? -1 : (lhs == rhs ? 0 : 1);
                }
            });
            //for (int[] entry : result) Logger.d(TAG, "[" + entry[0] + ";" + entry[1] + ";" + entry[2] + "]");
            addSubscription(chan, board, thread, page.posts[result.get(0)[0]].number);
        }
    }
    
    private static String htmlToComment(String html) {
        return StringEscapeUtils.unescapeHtml4(RegexUtils.removeHtmlTags(html.replaceAll("<(br|p)/?>", " ")));
    }
    
    private static List<String> commentToWordsList(String comment) {
        return Arrays.asList(comment.replaceAll("[\\*%_]", "").replaceAll("\\[[^\\]]*\\]", "").replaceAll("[^\\w\\d\\s]", " ").trim().split("\\s+"));
    }
    
    public static class SubscriptionEntry {
        public final String chan;
        public final String board;
        public final String thread;
        public final String post;
        public SubscriptionEntry(JSONObject json) {
            this.chan = json.getString("chan");
            this.board = json.getString("board");
            this.thread = json.getString("thread");
            this.post = json.getString("post");
        }
     
        public SubscriptionEntry(String chan, String board, String thread, String post) {
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

    public List<SubscriptionEntry> getSubscriptions() {
        return database.getSubscriptions();
    }

    private static class SubscriptionsDB {
        private static final int DB_VERSION = 1000;
        private static final String DB_NAME = "subscriptions.db";
        
        private static final String TABLE_NAME = "subscriptions";
        private static final String COL_CHAN = "chan";
        private static final String COL_BOARD = "board";
        private static final String COL_THREAD = "thread";
        private static final String COL_POST = "post";
        
        private final DBHelper dbHelper; 
        public SubscriptionsDB(Context context) {
            dbHelper = new DBHelper(context);
        }
        
        public boolean hasSubscription(String chan, String board, String thread, String post) {
            Cursor c = dbHelper.getReadableDatabase().query(TABLE_NAME, null,
                    COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_THREAD + " = ? AND " + COL_POST + " = ?",
                    new String[] { chan, board, thread, post }, null, null, null);
            boolean result = false;
            if (c != null && c.moveToFirst()) result = true;
            if (c != null) c.close();
            return result;
        }

        public void importSubscriptions(SubscriptionEntry[] subscription, Boolean overwrite){
            dbHelper.getWritableDatabase().beginTransaction();
            for (SubscriptionEntry entry : subscription) {
                put(
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
        
        public void put(String chan, String board, String thread, String post, Boolean check_existence) {
            if (check_existence)
                if (hasSubscription(chan, board, thread, post)) {
                    Logger.d(TAG, "entry is already exists");
                    return;
                }
            ContentValues value = new ContentValues(4);
            value.put(COL_CHAN, chan);
            value.put(COL_BOARD, board);
            value.put(COL_THREAD, thread);
            value.put(COL_POST, post);
            dbHelper.getWritableDatabase().insert(TABLE_NAME, null, value);
        }
        
        public void remove(String chan, String board, String thread, String post) {
            dbHelper.getWritableDatabase().delete(TABLE_NAME,
                    COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_THREAD + " = ? AND " + COL_POST + " = ?",
                    new String[] { chan, board, thread, post });
        }
        
        public String[] getSubscriptions(String chan, String board, String thread) {
            Cursor c = dbHelper.getReadableDatabase().query(TABLE_NAME, null,
                    COL_CHAN + " = ? AND " + COL_BOARD + " = ? AND " + COL_THREAD + " = ?",
                    new String[] { chan, board, thread }, null, null, null);
            String[] result = null;
            if (c != null && c.moveToFirst()) {
                int postIndex = c.getColumnIndex(COL_POST);
                int count = c.getCount();
                result = new String[count];
                int i = 0;
                do result[i++] = c.getString(postIndex); while (i < count && c.moveToNext());
                if (i < count) {
                    Logger.e(TAG, "result size < cursor getCount()");
                    String[] tmp = new String[i];
                    System.arraycopy(result, 0, tmp, 0, i);
                    result = tmp;
                }
            }
            if (c != null) c.close();
            return result;
        }
        
        public List<SubscriptionEntry> getSubscriptions() {
            List<SubscriptionEntry> list = new ArrayList<SubscriptionEntry>();
            Cursor c = dbHelper.getReadableDatabase().query(TABLE_NAME, null, null, null, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int chanIndex = c.getColumnIndex(COL_CHAN);
                int boardIndex = c.getColumnIndex(COL_BOARD);
                int threadIndex = c.getColumnIndex(COL_THREAD);
                int postIndex = c.getColumnIndex(COL_POST);
                do {
                    list.add(new SubscriptionEntry(c.getString(chanIndex), c.getString(boardIndex),
                            c.getString(threadIndex), c.getString(postIndex)));
                } while (c.moveToNext());
            }
            if (c != null) c.close();
            return list;
        }
        
        public void resetDB() {
            dbHelper.resetDB();
        }
        
        public long getNumEntries() {
            return DatabaseUtils.queryNumEntries(dbHelper.getReadableDatabase(), TABLE_NAME);
        }
        
        private static class DBHelper extends SQLiteOpenHelper implements BaseColumns {
            public DBHelper(Context context) {
                super(context, DB_NAME, null, DB_VERSION);
            }
            
            @Override
            public void onCreate(SQLiteDatabase db) {
                db.execSQL(createTable(TABLE_NAME, new String[] { COL_CHAN, COL_BOARD, COL_THREAD, COL_POST }, null));
            }
            
            @Override
            public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                if (oldVersion < newVersion) {
                    db.execSQL(dropTable(TABLE_NAME));
                    onCreate(db);
                }
            }
            
            @Override
            public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
                onUpgrade(db, oldVersion, newVersion);
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
            
            private void resetDB() {
                SQLiteDatabase db = getWritableDatabase();
                db.execSQL(dropTable(TABLE_NAME));
                onCreate(db);
            }
        }
    }
    
}
