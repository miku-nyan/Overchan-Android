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

import java.security.cert.X509Certificate;
import java.util.Collections;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;
import nya.miku.wishmaster.http.client.ExtendedSSLSocketFactory;
import nya.miku.wishmaster.http.client.ExtendedTrustManager;

public class CertificatesActivity extends ListActivity {
    private static final String TAG = "CertificatesActivity";
    private ExtendedTrustManager trustManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MainApplication.getInstance().settings.getTheme().setToPreferencesActivity(this);
        super.onCreate(savedInstanceState);
        setTitle(R.string.ssl_certificates_title);
        try {
            trustManager = ExtendedSSLSocketFactory.getTrustManager();
            setListAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, Collections.list(trustManager.getCertificates())));
        } catch (Exception e) {
            Logger.e(TAG, e);
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        try {
            final String alias = (String) getListAdapter().getItem(position);
            X509Certificate cert = (X509Certificate) trustManager.getCertificate(alias);
            StringBuilder sb = new StringBuilder();
            ExtendedTrustManager.certDetails(sb, cert);
            new AlertDialog.Builder(this).
                    setTitle(R.string.ssl_cert_details).
                    setMessage(sb).
                    setNeutralButton(R.string.ssl_certificate_delete, new DialogInterface.OnClickListener() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                trustManager.deleteCertificate(alias);
                                ((ArrayAdapter<String>) getListAdapter()).remove(alias);
                            } catch (Exception e) {
                                Logger.e(TAG, e);
                                Toast.makeText(CertificatesActivity.this, R.string.error_unknown, Toast.LENGTH_LONG).show();
                            }
                        }
                    }).show();
        } catch (Exception e) {
            Logger.e(TAG, e);
            Toast.makeText(this, R.string.error_unknown, Toast.LENGTH_LONG).show();
        }
    }
    
    public static boolean hasCertificates() {
        try {
            return ExtendedSSLSocketFactory.getTrustManager().getCertificates().hasMoreElements();
        } catch (Exception e) {
            Logger.e(TAG, e);
            return false;
        }
    }
}
