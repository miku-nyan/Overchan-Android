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

package nya.miku.wishmaster.http.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509TrustManager;

import cz.msebera.android.httpclient.HttpHost;
import cz.msebera.android.httpclient.conn.socket.LayeredConnectionSocketFactory;
import cz.msebera.android.httpclient.conn.ssl.BrowserCompatHostnameVerifier;
import cz.msebera.android.httpclient.conn.ssl.SSLContexts;
import cz.msebera.android.httpclient.conn.ssl.X509HostnameVerifier;
import cz.msebera.android.httpclient.protocol.HttpContext;
import cz.msebera.android.httpclient.util.Args;
import nya.miku.wishmaster.common.Logger;

public class ExtendedSSLSocketFactory implements LayeredConnectionSocketFactory {
    private static final String TAG = "ExtendedSSLSocketFactory";
    
    private static final X509HostnameVerifier BROWSER_COMPATIBLE_HOSTNAME_VERIFIER = new BrowserCompatHostnameVerifier();
    
    private static volatile boolean initialized = false;
    private static SSLContext interactiveContext = null;
    private static X509HostnameVerifier interactiveHostnameVerifier = null;
    private static void initInteractiveObjects() throws Exception {
        if (initialized) return;
        synchronized (ExtendedSSLSocketFactory.class) {
            if (initialized) return;
            
            SSLContext context = SSLContexts.createDefault();
            ExtendedTrustManager mtm = new ExtendedTrustManager();
            context.init(null, new X509TrustManager[] { mtm }, null);
            X509HostnameVerifier verifier = mtm.wrapHostnameVerifier(BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
            
            interactiveContext = context;
            interactiveHostnameVerifier = verifier;
            initialized = true;
        }
    }
    
    public static ExtendedSSLSocketFactory getSocketFactory() {
        try {
            initInteractiveObjects();
            return new ExtendedSSLSocketFactory(interactiveContext, interactiveHostnameVerifier);
        } catch (Exception e) {
            Logger.e(TAG, "cannot instantiate interactive SSL socket factory", e);
            return new ExtendedSSLSocketFactory(SSLContexts.createDefault(), BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
        }
    }
    
    private final javax.net.ssl.SSLSocketFactory socketfactory;
    private final X509HostnameVerifier hostnameVerifier;
    
    public ExtendedSSLSocketFactory(final SSLContext sslContext) {
        this(sslContext, null);
    }
    
    public ExtendedSSLSocketFactory(final SSLContext sslContext, final X509HostnameVerifier hostnameVerifier) {
        this(Args.notNull(sslContext, "SSL context").getSocketFactory(), hostnameVerifier);
    }
    
    public ExtendedSSLSocketFactory(final javax.net.ssl.SSLSocketFactory socketfactory, final X509HostnameVerifier hostnameVerifier) {
        this.socketfactory = Args.notNull(socketfactory, "SSL socket factory");
        this.hostnameVerifier = hostnameVerifier != null ? hostnameVerifier : BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
    }
    
    public Socket createSocket(final HttpContext context) throws IOException {
        return SocketFactory.getDefault().createSocket();
    }
    
    public Socket connectSocket(
            final int connectTimeout,
            final Socket socket,
            final HttpHost host,
            final InetSocketAddress remoteAddress,
            final InetSocketAddress localAddress,
            final HttpContext context) throws IOException {
        Args.notNull(host, "HTTP host");
        Args.notNull(remoteAddress, "Remote address");
        final Socket sock = socket != null ? socket : createSocket(context);
        if (localAddress != null) {
            sock.bind(localAddress);
        }
        try {
            if (connectTimeout > 0 && sock.getSoTimeout() == 0) {
                sock.setSoTimeout(connectTimeout);
            }
            sock.connect(remoteAddress, connectTimeout);
        } catch (final IOException ex) {
            try {
                sock.close();
            } catch (final IOException ignore) {
            }
            throw ex;
        }
        // Setup SSL layering if necessary
        if (sock instanceof SSLSocket) {
            final SSLSocket sslsock = (SSLSocket) sock;
            sslsock.startHandshake();
            verifyHostname(sslsock, host.getHostName());
            return sock;
        } else {
            return createLayeredSocket(sock, host.getHostName(), remoteAddress.getPort(), context);
        }
    }
    
    public Socket createLayeredSocket(
            final Socket socket,
            final String target,
            final int port,
            final HttpContext context) throws IOException {
        final SSLSocket sslsock = (SSLSocket) this.socketfactory.createSocket(
                socket,
                target,
                port,
                true);
        // If supported protocols are not explicitly set, remove all SSL protocol versions
        final String[] allProtocols = sslsock.getSupportedProtocols();
        final List<String> enabledProtocols = new ArrayList<String>(allProtocols.length);
        for (String protocol: allProtocols) {
            if (!protocol.startsWith("SSL")) {
                enabledProtocols.add(protocol);
            }
        }
        sslsock.setEnabledProtocols(enabledProtocols.toArray(new String[enabledProtocols.size()]));
        //prepareSocket(sslsock);
        
        // Android specific code to enable SNI
        try {
            sslsock.getClass().getMethod("setHostname", String.class).invoke(sslsock, target);
        } catch (NoSuchMethodException ex) {
            Logger.e(TAG, "SNI configuration failed: NoSuchMethodException");
        } catch (Exception ex) {
            Logger.e(TAG, "SNI configuration failed", ex);
        }
        // End of Android specific code
        
        sslsock.startHandshake();
        verifyHostname(sslsock, target);
        return sslsock;
    }
    
    X509HostnameVerifier getHostnameVerifier() {
        return this.hostnameVerifier;
    }
    
    private void verifyHostname(final SSLSocket sslsock, final String hostname) throws IOException {
        try {
            this.hostnameVerifier.verify(hostname, sslsock);
            // verifyHostName() didn't blowup - good!
        } catch (final IOException iox) {
            // close the socket before re-throwing the exception
            try { sslsock.close(); } catch (final Exception x) { /*ignore*/ }
            throw iox;
        }
    }
}
