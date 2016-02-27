//на основе проекта MemorizingTrustManager - https://github.com/ge0rg/MemorizingTrustManager

/* MemorizingTrustManager - a TrustManager which asks the user about invalid
 *  certificates and memorizes their decision.
 *
 * Copyright (c) 2010 Georg Lukas <georg@op-co.de>
 *
 * MemorizingTrustManager.java contains the actual trust manager and interface
 * code to create a MemorizingActivity and obtain the results.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package nya.miku.wishmaster.http.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import cz.msebera.android.httpclient.conn.ssl.X509HostnameVerifier;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.common.Logger;

public class ExtendedTrustManager implements X509TrustManager {
    private static final String TAG = "ExtendedTrustManager";
    
    public final static int DECISION_INVALID    = 0;
    public final static int DECISION_ABORT      = 1;
    public final static int DECISION_ONCE       = 2;
    public final static int DECISION_ALWAYS     = 3;
    
    private static final String KEYSTORE_DIR = "trusted_keystore";
    private static final String KEYSTORE_FILE = "trusted_keystore.bks";
    private static final String KEYSTORE_PASSWORD = "password";
    
    private static Context staticContext;
    private static Activity foregroundAct;
    
    private Context context;
    private X509TrustManager defaultTrustManager;
    
    private File appKeyStoreFile;
    private KeyStore appKeyStore;
    private X509TrustManager appTrustManager;
    
    /*package*/ ExtendedTrustManager() {
        if (staticContext == null)
            throw new IllegalStateException("set app context (call ExtendedTrustManager.setAppContext() in onCreate() of the application)");
        context = staticContext;
        appKeyStoreFile = new File(context.getDir(KEYSTORE_DIR, Context.MODE_PRIVATE), KEYSTORE_FILE);
        appKeyStore = loadAppKeyStore(appKeyStoreFile);
        appTrustManager = getTrustManager(appKeyStore);
        defaultTrustManager = getTrustManager(null);
    }
    
    public static void bindActivity(Activity activity) {
        foregroundAct = activity;
    }
    
    public static void unbindActivity() {
        foregroundAct = null;
    }
    
    public static void setAppContext(Context context) {
        staticContext = context;
    }
    
    private static X509TrustManager getTrustManager(KeyStore ks) {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
            tmf.init(ks);
            for (TrustManager t : tmf.getTrustManagers()) {
                if (t instanceof X509TrustManager) {
                    return (X509TrustManager) t;
                }
            }
        } catch (Exception e) {
            // Here, we are covering up errors. It might be more useful
            // however to throw them out of the constructor so the
            // embedding app knows something went wrong.
            Logger.e(TAG, "getTrustManager(" + ks + ")", e);
        }
        return null;
    }
    
    private static KeyStore loadAppKeyStore(File keyStoreFile) {
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            Logger.e(TAG, "getAppKeyStore()", e);
            return null;
        }
        try {
            ks.load(null, null);
        } catch (NoSuchAlgorithmException | CertificateException | IOException e) {
            Logger.e(TAG, "getAppKeyStore(" + keyStoreFile + ")", e);
        }
        InputStream is = null;
        try {
            is = new java.io.FileInputStream(keyStoreFile);
            ks.load(is, KEYSTORE_PASSWORD.toCharArray());
        } catch (FileNotFoundException e) {
        } catch (NoSuchAlgorithmException | CertificateException | IOException e) {
            Logger.e(TAG, "getAppKeyStore(" + keyStoreFile + ") - exception loading file key store", e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Logger.e(TAG, "getAppKeyStore(" + keyStoreFile + ") - exception closing file key store input stream", e);
                }
            }
        }
        return ks;
    }
    
    public Enumeration<String> getCertificates() {
        try {
            return appKeyStore.aliases();
        } catch (KeyStoreException e) {
            // this should never happen, however...
            throw new RuntimeException(e);
        }
    }
    
    public Certificate getCertificate(String alias) {
        try {
            return appKeyStore.getCertificate(alias);
        } catch (KeyStoreException e) {
            // this should never happen, however...
            throw new RuntimeException(e);
        }
    }
    
    public void deleteCertificate(String alias) throws KeyStoreException {
        appKeyStore.deleteEntry(alias);
        keyStoreUpdated();
    }
    
    private void storeCert(String alias, Certificate cert) {
        try {
            appKeyStore.setCertificateEntry(alias, cert);
        } catch (KeyStoreException e) {
            Logger.e(TAG, "storeCert(" + cert + ")", e);
            return;
        }
        keyStoreUpdated();
    }
    
    private void storeCert(X509Certificate cert) {
        storeCert(cert.getSubjectDN().toString(), cert);
    }
    
    private void keyStoreUpdated() {
        // reload appTrustManager
        appTrustManager = getTrustManager(appKeyStore);
        
        // store KeyStore to file
        java.io.FileOutputStream fos = null;
        try {
            fos = new java.io.FileOutputStream(appKeyStoreFile);
            appKeyStore.store(fos, KEYSTORE_PASSWORD.toCharArray());
        } catch (Exception e) {
            Logger.e(TAG, "storeCert(" + appKeyStoreFile + ")", e);
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Logger.e(TAG, "storeCert(" + appKeyStoreFile + ")", e);
                }
            }
        }
    }
    
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkCertTrusted(chain, authType, false);
    }
    
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        checkCertTrusted(chain, authType, true);
    }
    
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return defaultTrustManager.getAcceptedIssuers();
    }
    
    /*package*/ X509HostnameVerifier wrapHostnameVerifier(final X509HostnameVerifier defaultVerifier) {
        if (defaultVerifier == null) throw new IllegalArgumentException("The default verifier may not be null");
        return new X509HostnameVerifier() {
            private boolean verifyCert(String hostname, X509Certificate cert) {
                try {
                    if (cert.equals(appKeyStore.getCertificate(hostname.toLowerCase(Locale.US)))) {
                        return true;
                    } else {
                        return interactHostname(cert, hostname);
                    }
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    return false;
                }
            }
            
            @Override
            public boolean verify(String hostname, SSLSession session) {
                if (defaultVerifier.verify(hostname, session)) {
                    Logger.d(TAG, "default verifier accepted " + hostname);
                    return true;
                }
                try {
                    X509Certificate cert = (X509Certificate) session.getPeerCertificates()[0];
                    return verifyCert(hostname, cert);
                } catch (Exception e) {
                    Logger.e(TAG, e);
                    return false;
                }
            }
            
            @Override
            public void verify(String host, SSLSocket ssl) throws IOException {
                try {
                    defaultVerifier.verify(host, ssl);
                } catch (Exception e) {
                    X509Certificate cert = (X509Certificate) ssl.getSession().getPeerCertificates()[0];
                    if (verifyCert(host, cert)) return;
                    throw e;
                }
            }
            
            @Override
            public void verify(String host, X509Certificate cert) throws SSLException {
                try {
                    defaultVerifier.verify(host, cert);
                } catch (Exception e) {
                    if (verifyCert(host, cert)) return;
                    throw e;
                }
            }
            
            @Override
            public void verify(String host, String[] cns, String[] subjectAlts) throws SSLException {
                defaultVerifier.verify(host, cns, subjectAlts);
            }
        };
    }
    
    private void checkCertTrusted(X509Certificate[] chain, String authType, boolean isServer) throws CertificateException {
        try {
            Logger.d(TAG, "checkCertTrusted: trying appTrustManager");
            if (isServer) appTrustManager.checkServerTrusted(chain, authType);
            else appTrustManager.checkClientTrusted(chain, authType);
        } catch (CertificateException e) {
            Logger.d(TAG, "checkCertTrusted: appTrustManager did not verify certificate. " +
                    "Will fall back to secondary verification mechanisms (if any). " + e);
            // if the cert is stored in our appTrustManager, we ignore expiredness
            if (isExpiredException(e)) {
                Logger.d(TAG, "checkCertTrusted: accepting expired certificate from keystore");
                return;
            }
            if (isCertKnown(chain[0])) {
                Logger.d(TAG, "checkCertTrusted: accepting cert already stored in keystore");
                return;
            }
            try {
                if (defaultTrustManager == null) throw e;
                Logger.d(TAG, "checkCertTrusted: trying defaultTrustManager");
                if (isServer) defaultTrustManager.checkServerTrusted(chain, authType);
                else defaultTrustManager.checkClientTrusted(chain, authType);
            } catch (CertificateException e1) {
                Logger.e(TAG, "checkCertTrusted: defaultTrustManager failed", e1);
                interactCert(chain, authType, e1);
            }
        }
    }
    
    // if the certificate is stored in the app key store, it is considered
    // "known"
    private boolean isCertKnown(X509Certificate cert) {
        try {
            return appKeyStore.getCertificateAlias(cert) != null;
        } catch (KeyStoreException e) {
            return false;
        }
    }
    
    private static boolean isExpiredException(Throwable e) {
        while (e != null) {
            if (e instanceof CertificateExpiredException) return true;
            e = e.getCause();
        }
        return false;
    }
    
    private void interactCert(final X509Certificate[] chain, String authType, CertificateException cause) throws CertificateException {
        switch (interact(certChainMessage(chain, cause), R.string.ssl_accept_cert)) {
            case DECISION_ALWAYS:
                storeCert(chain[0]); // only store the server cert, not the whole chain
            case DECISION_ONCE:
                break;
            default:
                throw cause;
        }
    }
    
    private boolean interactHostname(X509Certificate cert, String hostname) {
        switch (interact(hostNameMessage(cert, hostname), R.string.ssl_accept_servername)) {
            case DECISION_ALWAYS:
                storeCert(hostname, cert);
            case DECISION_ONCE:
                return true;
            default:
                return false;
        }
    }
    
    private int interact(final String message, final int titleId) {
        final Activity activity = foregroundAct;
        if (activity == null) return DECISION_ABORT;
        
        class Decision { int state = DECISION_INVALID; }
        final Decision decision = new Decision();
        
        class DlgRunnable implements Runnable, DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
            @Override
            public void run() {
                AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(titleId)
                        .setMessage(message)
                        .setPositiveButton(R.string.ssl_decision_always, this)
                        .setNeutralButton(R.string.ssl_decision_once, this)
                        .setNegativeButton(R.string.ssl_decision_abort, this)
                        .setOnCancelListener(this)
                        .create();
                dialog.show();
            }
            @Override
            public void onCancel(DialogInterface dialog) {
                sendDecision(DECISION_ABORT);
            }
            @Override
            public void onClick(DialogInterface dialog, int which) {
                int decision;
                dialog.dismiss();
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        decision = DECISION_ALWAYS;
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        decision = DECISION_ONCE;
                        break;
                    default:
                        decision = DECISION_ABORT;
                }
                sendDecision(decision);
            }
            void sendDecision(int d) {
                Logger.d(TAG, "notify dicision " + d + "on " + decision);
                synchronized (decision) {
                    decision.state = d;
                    decision.notify();
                }
            }
        }
        activity.runOnUiThread(new DlgRunnable());
        
        Logger.d(TAG, "waiting on decision " + decision);
        try {
            synchronized (decision) {
                decision.wait();
            }
        } catch (InterruptedException e) {
            Logger.e(TAG, "InterruptedException", e);
        }
        Logger.d(TAG, "finished wait on " + decision);
        return decision.state;
    }
    
    private String certChainMessage(final X509Certificate[] chain, CertificateException e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        StringBuilder si = new StringBuilder();
        if (cause instanceof CertPathValidatorException) si.append(context.getString(R.string.ssl_trust_anchor));
        else if (cause instanceof CertificateExpiredException) si.append(context.getString(R.string.ssl_cert_expired));
        else si.append(cause.getLocalizedMessage() != null ? cause.getLocalizedMessage() : cause.getClass().getSimpleName());
        si.append("\n\n");
        si.append(context.getString(R.string.ssl_connect_anyway));
        si.append("\n\n");
        si.append(context.getString(R.string.ssl_cert_details));
        si.append("\n");
        certDetails(si, chain[0]);
        return si.toString();
    }
    
    private String hostNameMessage(X509Certificate cert, String hostname) {
        StringBuilder si = new StringBuilder();
        
        si.append(context.getString(R.string.ssl_hostname_mismatch, hostname));
        si.append("\n\n");
        try {
            Collection<List<?>> sans = cert.getSubjectAlternativeNames();
            if (sans == null) {
                si.append(cert.getSubjectDN());
                si.append("\n");
            } else
                for (List<?> altName : sans) {
                    Object name = altName.get(1);
                    if (name instanceof String) {
                        si.append("[");
                        si.append(altName.get(0));
                        si.append("] ");
                        si.append(name);
                        si.append("\n");
                    }
                }
        } catch (CertificateParsingException e) {
            Logger.e(TAG, e);
            si.append("<Parsing error: ");
            si.append(e.getLocalizedMessage());
            si.append(">\n");
        }
        si.append("\n");
        si.append(context.getString(R.string.ssl_connect_anyway));
        si.append("\n\n");
        si.append(context.getString(R.string.ssl_cert_details));
        si.append("\n");
        certDetails(si, cert);
        return si.toString();
    }
    
    public static void certDetails(StringBuilder si, X509Certificate c) {
        SimpleDateFormat validityDateFormater = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        si.append(c.getSubjectDN().toString());
        si.append("\n");
        si.append(validityDateFormater.format(c.getNotBefore()));
        si.append(" - ");
        si.append(validityDateFormater.format(c.getNotAfter()));
        si.append("\nSHA-256: ");
        si.append(certHash(c, "SHA-256"));
        si.append("\nSHA-1: ");
        si.append(certHash(c, "SHA-1"));
        si.append("\nSigned by: ");
        si.append(c.getIssuerDN().toString());
    }
    
    private static String certHash(final X509Certificate cert, String digest) {
        try {
            MessageDigest md = MessageDigest.getInstance(digest);
            md.update(cert.getEncoded());
            return hexString(md.digest());
        } catch (java.security.cert.CertificateEncodingException e) {
            return e.getMessage();
        } catch (java.security.NoSuchAlgorithmException e) {
            return e.getMessage();
        }
    }
    
    private static String hexString(byte[] data) {
        StringBuilder si = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            si.append(String.format("%02X", data[i]));
            if (i < data.length - 1) si.append(":");
        }
        return si.toString();
    }
}
