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

package nya.miku.wishmaster.ui.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.ChanModule;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.lib.org_json.JSONArray;
import nya.miku.wishmaster.lib.org_json.JSONObject;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;
import android.view.ContextMenu;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class AutohideActivity extends ListActivity {
    private ApplicationSettings settings;
    private JSONArray rulesJson;
    
    private List<String> chans = new ArrayList<String>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = MainApplication.getInstance().settings;
        settings.getTheme().setToPreferencesActivity(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.autohide_title);
        
        chans = new ArrayList<String>();
        chans.add(getString(R.string.autohide_all_chans));
        for (ChanModule chan : MainApplication.getInstance().chanModulesList) {
            String chanName = chan.getChanName();
            if (settings.isUnlockedChan(chanName)) chans.add(chanName);
        }
        
        rulesJson = new JSONArray(settings.getAutohideRulesJson());
        setListAdapter(new AutohideAdapter(this));
        registerForContextMenu(getListView());
    }
    
    @SuppressLint("InflateParams")
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        Object item = l.getItemAtPosition(position);
        final int changeId;
        if (item instanceof AutohideRule) {
            changeId = position - 1;
        } else {
            changeId = -1; //-1 - создать новое правило
        }
        
        Context dialogContext = Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ? new ContextThemeWrapper(this, R.style.Theme_Neutron) : this;
        View dialogView = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_autohide_rule, null);
        final EditText regexEditText = (EditText) dialogView.findViewById(R.id.dialog_autohide_regex);
        final Spinner chanSpinner = (Spinner) dialogView.findViewById(R.id.dialog_autohide_chan_spinner);
        final EditText boardEditText = (EditText) dialogView.findViewById(R.id.dialog_autohide_boardname);
        final EditText threadEditText = (EditText) dialogView.findViewById(R.id.dialog_autohide_threadnum);
        final CheckBox inCommentCheckBox = (CheckBox) dialogView.findViewById(R.id.dialog_autohide_in_comment);
        final CheckBox inSubjectCheckBox = (CheckBox) dialogView.findViewById(R.id.dialog_autohide_in_subject);
        final CheckBox inNameCheckBox = (CheckBox) dialogView.findViewById(R.id.dialog_autohide_in_name);
        
        chanSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, chans));
        if (changeId != -1) {
            AutohideRule rule = (AutohideRule) item;
            regexEditText.setText(rule.regex);
            int chanPosition = chans.indexOf(rule.chanName);
            chanSpinner.setSelection(chanPosition != -1 ? chanPosition : 0);
            boardEditText.setText(rule.boardName);
            threadEditText.setText(rule.threadNumber);
            inCommentCheckBox.setChecked(rule.inComment);
            inSubjectCheckBox.setChecked(rule.inSubject);
            inNameCheckBox.setChecked(rule.inName);
        } else {
            chanSpinner.setSelection(0);
        }
        
        DialogInterface.OnClickListener save = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String regex = regexEditText.getText().toString();
                if (regex.length() == 0) {
                    Toast.makeText(AutohideActivity.this, R.string.autohide_error_empty_regex, Toast.LENGTH_LONG).show();
                    return;
                }
                
                try {
                    Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
                } catch (Exception e) {
                    CharSequence message = null;
                    if (e instanceof PatternSyntaxException) {
                        String eMessage = e.getMessage();
                        if (!TextUtils.isEmpty(eMessage)) {
                            SpannableStringBuilder a = new SpannableStringBuilder(getString(R.string.autohide_error_incorrect_regex));
                            a.append('\n');
                            int startlen = a.length();
                            a.append(eMessage);
                            a.setSpan(new TypefaceSpan("monospace"), startlen, a.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            message = a;
                        }
                    }
                    if (message == null) message = getString(R.string.error_unknown);
                    Toast.makeText(AutohideActivity.this, message, Toast.LENGTH_LONG).show();
                    return;
                }
                
                AutohideRule rule = new AutohideRule();
                int spinnerSelectedPosition = chanSpinner.getSelectedItemPosition();
                rule.regex = regex;
                rule.chanName = spinnerSelectedPosition > 0 ? chans.get(spinnerSelectedPosition) : ""; // 0 элемент = все имиджборды
                rule.boardName = boardEditText.getText().toString();
                rule.threadNumber = threadEditText.getText().toString();
                rule.inComment = inCommentCheckBox.isChecked();
                rule.inSubject = inSubjectCheckBox.isChecked();
                rule.inName = inNameCheckBox.isChecked();
                
                if (!rule.inComment && !rule.inSubject && !rule.inName) {
                    Toast.makeText(AutohideActivity.this, R.string.autohide_error_no_condition, Toast.LENGTH_LONG).show();
                    return;
                }
                
                if (changeId == -1) {
                    rulesJson.put(rule.toJson());
                } else {
                    rulesJson.put(changeId, rule.toJson());
                }
                rulesChanged();
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).
                setTitle(changeId == -1 ? R.string.autohide_add_rule_title : R.string.autohide_edit_rule_title).
                setPositiveButton(R.string.autohide_save_button, save).
                setNegativeButton(android.R.string.cancel, null).create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (info.position == 0) return;
        menu.add(Menu.NONE, R.id.context_menu_delete, 1, R.string.context_menu_delete_autohide_rule);
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.context_menu_delete) {
            if (item.getMenuInfo() != null && item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo) {
                int position = ((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position;
                if (position > 0) {
                    rulesJson.remove(position-1);
                    rulesChanged();
                }
            }
        }
        return super.onContextItemSelected(item);
    }
    
    private void rulesChanged() {
        settings.saveAutohideRulesJson(rulesJson.toString());
        setListAdapter(new AutohideAdapter(this));
    }

    public static class AutohideRule {
        public String regex;
        public String chanName;
        public String boardName;
        public String threadNumber;
        public boolean inComment;
        public boolean inSubject;
        public boolean inName;
        
        public JSONObject toJson() {
            JSONObject rule = new JSONObject();
            rule.put("regex", regex);
            rule.put("chanName", chanName);
            rule.put("boardName", boardName);
            rule.put("threadNumber", threadNumber);
            rule.put("inComment", inComment);
            rule.put("inSubject", inSubject);
            rule.put("inName", inName);
            return rule;
        }
        
        public static AutohideRule fromJson(JSONObject json) {
            AutohideRule rule = new AutohideRule();
            rule.regex = json.getString("regex");
            rule.chanName = json.getString("chanName");
            rule.boardName = json.getString("boardName");
            rule.threadNumber = json.getString("threadNumber");
            rule.inComment = json.getBoolean("inComment");
            rule.inSubject = json.getBoolean("inSubject");
            rule.inName = json.getBoolean("inName");
            return rule;
        }
        
        public boolean matches(String chanName, String boardName, String threadNumber) {
            return ((TextUtils.isEmpty(this.chanName) || chanName == this.chanName || chanName.equals(this.chanName)) &&
                    (TextUtils.isEmpty(this.boardName) || boardName == this.boardName || boardName.equals(this.boardName)) &&
                    (TextUtils.isEmpty(this.threadNumber) || threadNumber == this.threadNumber || threadNumber.equals(this.threadNumber)));
        }

        public int hashCode() {
            int result = 7;
            result += result * 37 + (TextUtils.isEmpty(this.regex) ? 0 : this.regex.hashCode());
            result += result * 37 + (TextUtils.isEmpty(this.chanName) ? 0 : this.chanName.hashCode());
            result += result * 37 + (TextUtils.isEmpty(this.boardName) ? 0 : this.boardName.hashCode());
            result += result * 37 + (TextUtils.isEmpty(this.threadNumber) ? 0 : this.threadNumber.hashCode());
            result += result * 37 + (this.inComment ? 0 : 1);
            result += result * 37 + (this.inSubject ? 0 : 1);
            result += result * 37 + (this.inName ? 0 : 1);
            return result;
        }
    }
    
    public static class CompiledAutohideRule extends AutohideRule {
        public Pattern pattern;
        
        public CompiledAutohideRule(AutohideRule rule) {
            regex = rule.regex;
            chanName = rule.chanName;
            boardName = rule.boardName;
            threadNumber = rule.threadNumber;
            inComment = rule.inComment;
            inSubject = rule.inSubject;
            inName = rule.inName;
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL);
        }
    }
    
    private static class AutohideAdapter extends ArrayAdapter<Object> {
        private LayoutInflater inflater;
        private Resources resources;
        
        public AutohideAdapter(AutohideActivity activity) {
            super(activity, android.R.layout.simple_list_item_2);
            inflater = activity.getLayoutInflater();
            resources = activity.getResources();
            add(new Object());
            for (int i=0; i<activity.rulesJson.length(); ++i) {
                add(AutohideRule.fromJson(activity.rulesJson.getJSONObject(i)));
            }
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView == null ? inflater.inflate(android.R.layout.simple_list_item_2, parent, false) : convertView;
            
            String str1, str2;
            Object current = getItem(position);
            if (current instanceof AutohideRule) {
                AutohideRule rule = (AutohideRule) current;
                str1 = rule.regex;
                str2 = (TextUtils.isEmpty(rule.chanName) ? "*" : rule.chanName) + " : " +
                        (TextUtils.isEmpty(rule.boardName) ? "*" : rule.boardName) + " : " +
                        (TextUtils.isEmpty(rule.threadNumber) ? "*" : rule.threadNumber) + " : " +
                        (rule.inComment ? "TEXT|" : "") + (rule.inSubject ? "SUBJ|" : "") + (rule.inName ? "NAME|" : "");
                str2 = str2.substring(0, str2.length() - 1);
            } else {
                str1 = resources.getString(R.string.autohide_add_rule_item);
                str2 = resources.getString(R.string.autohide_add_rule_summary);
            }
            
            ((TextView) view.findViewById(android.R.id.text1)).setText(str1);
            ((TextView) view.findViewById(android.R.id.text2)).setText(str2);
            
            return view;
        }
        
    }
}
