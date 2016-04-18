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

package nya.miku.wishmaster.ui.theme;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.LayoutInflaterCompat;
import android.support.v4.view.LayoutInflaterFactory;
import android.util.AttributeSet;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.ui.AppearanceUtils;
import nya.miku.wishmaster.ui.CompatibilityImpl;

public class CustomThemeHelper implements LayoutInflaterFactory {
    private static final String TAG = "CustomThemeHelper";
    private static final String[] CLASS_PREFIX_LIST = new String[] { "android.widget.", "android.webkit.", "android.app.", "android.view." };
    private static final Class<?>[] CONSTRUCTOR_SIGNATURE = new Class[] { Context.class, AttributeSet.class };
    private static final HashMap<String, Constructor<? extends View>> CONSTRUCTOR_MAP = new HashMap<>();
    
    private static CustomThemeHelper currentInstance = null;
    
    private final Resources resources;
    private final LayoutInflater inflater;
    private final SparseIntArray customAttrs;
    private final int[] mappingKeys;
    private final int[] mappingValues;
    
    private final int textColorPrimaryOriginal, textColorPrimaryOverridden;
    
    private CustomThemeHelper(Context context, SparseIntArray customAttrs, int textColorPrimaryOriginal, int textColorPrimaryOverridden) {
        this.customAttrs = customAttrs;
        this.mappingKeys = new int[customAttrs.size()];
        this.mappingValues = new int[customAttrs.size()];
        this.textColorPrimaryOriginal = textColorPrimaryOriginal;
        this.textColorPrimaryOverridden = textColorPrimaryOverridden;
        this.resources = context.getResources();
        this.inflater = LayoutInflater.from(context);
    }
    
    private final Object[] constructorArgs = new Object[2];
    private View instantiate(String name, Context context, AttributeSet attrs) {
        try {
            Constructor<? extends View> constructor = CONSTRUCTOR_MAP.get(name);
            if (constructor == null) {
                Class<? extends View> clazz = null;
                if (name.indexOf('.') != -1) {
                    clazz = context.getClassLoader().loadClass(name).asSubclass(View.class);
                } else {
                    for (String prefix : CLASS_PREFIX_LIST) {
                        try {
                            clazz = context.getClassLoader().loadClass(prefix + name).asSubclass(View.class);
                            break;
                        } catch (ClassNotFoundException e) {
                        }
                    }
                    if (clazz == null) throw new ClassNotFoundException("couldn't find class: " + name);
                }
                constructor = clazz.getConstructor(CONSTRUCTOR_SIGNATURE);
                CONSTRUCTOR_MAP.put(name, constructor);
            }
            
            Object[] args = constructorArgs;
            args[0] = context;
            args[1] = attrs;
            
            constructor.setAccessible(true);
            View view = constructor.newInstance(args);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && view instanceof ViewStub)
                CompatibilityImpl.setLayoutInflater((ViewStub) view, inflater.cloneInContext(context));
            return view;
        } catch (Exception e) {
            Logger.e(TAG, "couldn't instantiate class " + name, e);
            return null;
        }
    }
    
    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        int mappingCount = 0;
        if (attrs != null) {
            for (int i=0, size=attrs.getAttributeCount(); i<size; ++i) {
                String value = attrs.getAttributeValue(i);
                if (!value.startsWith("?")) continue;
                int attrId = resources.getIdentifier(value.substring(1), null, null); //Integer.parseInt(value.substring(1));
                if (attrId == 0) {
                    Logger.e(TAG, "couldn't get id for attribute: " + value);
                    continue;
                }
                int index = customAttrs.indexOfKey(attrId);
                if (index >= 0) {
                    mappingKeys[mappingCount] = attrs.getAttributeNameResource(i);
                    mappingValues[mappingCount] = customAttrs.valueAt(index);
                    ++mappingCount;
                }
            }
        }
        
        if (mappingCount == 0 && textColorPrimaryOverridden == textColorPrimaryOriginal) return null;
        
        View view = instantiate(name, context, attrs);
        if (view == null) return null;
        
        boolean shouldOverrideTextColor = textColorPrimaryOverridden != textColorPrimaryOriginal && view instanceof TextView;
        for (int i=0; i<mappingCount; ++i) {
            switch (mappingKeys[i]) {
                case android.R.attr.background:
                    view.setBackgroundColor(mappingValues[i]);
                    break;
                case android.R.attr.textColor:
                    if (view instanceof TextView) {
                        ((TextView) view).setTextColor(mappingValues[i]);
                        shouldOverrideTextColor = false;
                    } else {
                        Logger.e(TAG, "couldn't apply attribute 'textColor' on class " + name + " (not instance of TextView)");
                    }
                    break;
                case android.R.attr.divider:
                    if (view instanceof ListView) {
                        ListView listView = (ListView) view;
                        int dividerHeight = listView.getDividerHeight();
                        listView.setDivider(new ColorDrawable(mappingValues[i]));
                        listView.setDividerHeight(dividerHeight);
                    } else {
                        Logger.e(TAG, "couldn't apply attribute 'divider' on class " + name + " (not instance of ListView)");
                    }
                    break;
                default:
                    String attrResName = null;
                    try {
                        attrResName = resources.getResourceName(mappingKeys[i]);
                    } catch (Exception e) {
                        attrResName = Integer.toString(mappingKeys[i]);
                    }
                    Logger.e(TAG, "couldn't apply attribure '" + attrResName + "' on class " + name);
            }
        }
        
        if (shouldOverrideTextColor) {
            TextView tv = (TextView) view;
            if (tv.getCurrentTextColor() == textColorPrimaryOriginal) {
                tv.setTextColor(textColorPrimaryOverridden);
            }
        }
        
        return view;
    }
    
    public static void setCustomTheme(Context context, SparseIntArray customAttrs) {
        if (customAttrs == null || customAttrs.size() == 0) {
            currentInstance = null;
            return;
        }
        
        TypedValue tmp = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, tmp, true);
        int textColorPrimaryOriginal = (tmp.type >= TypedValue.TYPE_FIRST_COLOR_INT && tmp.type <= TypedValue.TYPE_LAST_COLOR_INT) ?
                tmp.data : Color.TRANSPARENT;
        int textColorPrimaryOverridden = customAttrs.get(android.R.attr.textColorPrimary, textColorPrimaryOriginal);
        
        try {
            processWindow(context, customAttrs, textColorPrimaryOriginal, textColorPrimaryOverridden);
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
        
        CustomThemeHelper instance = new CustomThemeHelper(context, customAttrs, textColorPrimaryOriginal, textColorPrimaryOverridden);
        LayoutInflaterCompat.setFactory(instance.inflater, instance);
        currentInstance = instance;
    }
    
    private static void processWindow(Context context, SparseIntArray attrs, int textColorPrimaryOriginal, int textColorPrimaryOverridden) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) return;
        boolean isLollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
        
        int materialPrimaryIndex = attrs.indexOfKey(R.attr.materialPrimary);
        int materialPrimaryDarkIndex = attrs.indexOfKey(R.attr.materialPrimaryDark);
        int materialNavigationBarIndex = attrs.indexOfKey(R.attr.materialNavigationBar);
        boolean overrideActionbarColor = materialPrimaryIndex >= 0;
        boolean overridePanelsColor = Math.max(materialPrimaryDarkIndex, materialNavigationBarIndex) >= 0;
        
        boolean overrideTextColor = textColorPrimaryOriginal != textColorPrimaryOverridden;
        if (!overrideTextColor && !overrideActionbarColor && !overridePanelsColor) return;
        
        Window window = ((Activity) context).getWindow();
        final View decorView = window.getDecorView();
        Resources resources = context.getResources();
        
        if (overrideActionbarColor) {
            try {
                Drawable background = new ColorDrawable(attrs.valueAt(materialPrimaryIndex));
                Object actionBar = context.getClass().getMethod("getActionBar").invoke(context);
                actionBar.getClass().getMethod("setBackgroundDrawable", Drawable.class).invoke(actionBar, background);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        
        if (overrideTextColor) {
            int id = resources.getIdentifier("action_bar_title", "id", "android");
            if (id != 0) {
                View v = decorView.findViewById(id);
                if (v instanceof TextView) ((TextView) v).setTextColor(textColorPrimaryOverridden);
            }
        }
        
        if (isLollipop && overrideTextColor) {
            try {
                int id = resources.getIdentifier("action_bar", "id", "android");
                if (id == 0) throw new Exception("'android:id/action_bar' identifier not found");
                View v = decorView.findViewById(id);
                if (v == null) throw new Exception("view with id 'android:id/action_bar' not found");
                Class<?> toolbarClass = Class.forName("android.widget.Toolbar");
                if (!toolbarClass.isInstance(v)) throw new Exception("view 'android:id/action_bar' is not instance of android.widget.Toolbar");
                toolbarClass.getMethod("setTitleTextColor", int.class).invoke(v, textColorPrimaryOverridden);
                setLollipopMenuOverflowIconColor((ViewGroup) v, textColorPrimaryOverridden);
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
        
        if (isLollipop && overridePanelsColor) {
            try {
                if (materialPrimaryDarkIndex >= 0) {
                    window.getClass().getMethod("setStatusBarColor", int.class).invoke(window, attrs.valueAt(materialPrimaryDarkIndex));
                }
                if (materialNavigationBarIndex >= 0) {
                    window.getClass().getMethod("setNavigationBarColor", int.class).invoke(window, attrs.valueAt(materialNavigationBarIndex));
                }
            } catch (Exception e) {
                Logger.e(TAG, e);
            }
        }
    }
    
    private static void setLollipopMenuOverflowIconColor(final ViewGroup toolbar, final int color) {
        try {
            //for API 23 (Android 6): at this point method Toolbar.setOverflowIcon(Drawable) has no effect
            toolbar.getClass().getMethod("getMenu").invoke(toolbar);
            AppearanceUtils.callWhenLoaded(toolbar, new Runnable() {
                @Override
                public void run() {
                    try {
                        final ViewGroup actionMenuView = (ViewGroup) findViewByClassName(toolbar, "android.widget.ActionMenuView");
                        
                        Runnable setOverflowIcon = new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Class<?> toolbarClass = toolbar.getClass();
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        Drawable overflowIcon = (Drawable) toolbarClass.getMethod("getOverflowIcon").invoke(toolbar);
                                        setColorFilter(overflowIcon);
                                        toolbarClass.getMethod("setOverflowIcon", Drawable.class).invoke(toolbar, overflowIcon);
                                    } else {
                                        ImageView overflowButton = (ImageView)
                                                findViewByClassName(actionMenuView, "android.widget.ActionMenuPresenter$OverflowMenuButton");
                                        if (overflowButton != null) {
                                            Drawable overflowIcon = overflowButton.getDrawable();
                                            setColorFilter(overflowIcon);
                                            overflowButton.setImageDrawable(overflowIcon);
                                        }
                                    }
                                } catch (Exception e) {
                                    Logger.e(TAG, e);
                                }
                            }
                            private void setColorFilter(Drawable overflowIcon) {
                                overflowIcon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
                            }
                        };
                        
                        if (actionMenuView.getChildCount() == 0) {
                            AppearanceUtils.callWhenLoaded(actionMenuView == null ? toolbar : actionMenuView, setOverflowIcon);
                        } else {
                            setOverflowIcon.run();
                        }
                    } catch (Exception e) {
                        Logger.e(TAG, e);
                    }
                }
                private View findViewByClassName(ViewGroup group, String className) {
                    for (int i=0, size=group.getChildCount(); i<size; ++i) {
                        View child = group.getChildAt(i);
                        if (child.getClass().getName().equals(className)) {
                            return child;
                        }
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            Logger.e(TAG, e);
        }
    }
    
    public static boolean resolveAttribute(int attrId, TypedValue outValue) {
        if (currentInstance == null) return false;
        SparseIntArray customAttrs = currentInstance.customAttrs;
        int index = customAttrs.indexOfKey(attrId);
        if (index < 0) return false;
        outValue.type = TypedValue.TYPE_INT_COLOR_ARGB8;
        outValue.data = customAttrs.valueAt(index);
        return true;
    }
    
}
