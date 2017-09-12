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

package nya.miku.wishmaster.chans.endchan;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.support.v4.content.res.ResourcesCompat;
import android.text.InputType;

import java.util.Arrays;
import java.util.List;

import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractLynxChanModule;

public class EndChanModule extends AbstractLynxChanModule {
    private static final List<String> DOMAINS_LIST = Arrays.asList(new String[]{
            "endchan.xyz", "endchan.net", "infinow.net", "endchan5doxvprs5.onion", "s6424n4x4bsmqs27.onion", "endchan.i2p"
    });
    private static final String DOMAINS_HINT = "endchan.xyz, endchan.net, infinow.net (cached), endchan5doxvprs5.onion, s6424n4x4bsmqs27.onion, endchan.i2p";
    private static final String TAG = "EndChanModule";
    private static final String DISPLAYING_NAME = "EndChan";
    private static final String CHAN_NAME = "endchan.xyz";
    private static final String DEFAULT_DOMAIN = "endchan.xyz";
    private static final String PREF_KEY_DOMAIN = "domain";
    private String domain = DEFAULT_DOMAIN;

    public EndChanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }

    @Override
    protected String getUsingDomain() {
        return domain;
    }

    private void addDomainPreferences(PreferenceGroup group) {
        Context context = group.getContext();
        Preference.OnPreferenceChangeListener updateDomainListener = new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                if (preference.getKey().equals(getSharedKey(PREF_KEY_DOMAIN))) {
                    updateDomain((String) newValue);
                    return true;
                }
                return false;
            }
        };
        PreferenceCategory domainCat = new PreferenceCategory(context);
        domainCat.setTitle(R.string.makaba_prefs_domain_category);
        group.addPreference(domainCat);
        EditTextPreference domainPref = new EditTextPreference(context);
        domainPref.setTitle(R.string.pref_domain);
        domainPref.setDialogTitle(R.string.pref_domain);
        domainPref.setSummary(resources.getString(R.string.pref_domain_summary, DOMAINS_HINT));
        domainPref.setKey(getSharedKey(PREF_KEY_DOMAIN));
        domainPref.getEditText().setHint(DEFAULT_DOMAIN);
        domainPref.getEditText().setSingleLine();
        domainPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        domainPref.setOnPreferenceChangeListener(updateDomainListener);
        domainCat.addPreference(domainPref);
    }

    @Override
    public void addPreferencesOnScreen(PreferenceGroup preferenceGroup) {
        addPasswordPreference(preferenceGroup);
        if (canHttps()) addHttpsPreference(preferenceGroup, useHttpsDefaultValue());
        addCloudflareRecaptchaFallbackPreference(preferenceGroup);
        addDomainPreferences(preferenceGroup);
        addProxyPreferences(preferenceGroup);
    }

    @Override
    protected String[] getAllDomains() {
        return DOMAINS_LIST.toArray(new String[DOMAINS_LIST.size()]);
    }

    private void updateDomain(String domain) {
        if (domain.endsWith("/")) domain = domain.substring(0, domain.length() - 1);
        if (domain.contains("//")) domain = domain.substring(domain.indexOf("//") + 2);
        if (domain.equals("")) domain = DEFAULT_DOMAIN;
        this.domain = domain;
    }

    @Override
    public String getChanName() {
        return CHAN_NAME;
    }

    @Override
    public String getDisplayingName() {
        return DISPLAYING_NAME;
    }

    protected boolean canHttps() {
        return true;
    }

    protected boolean canCloudflare() {
        return true;
    }

    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_endchan, null);
    }
}
