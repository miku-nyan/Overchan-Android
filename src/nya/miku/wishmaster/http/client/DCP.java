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

package nya.miku.wishmaster.http.client;

import java.io.IOException;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;

import android.os.Build;
import nya.miku.wishmaster.common.CryptoUtils;

@SuppressWarnings("deprecation") // https://issues.apache.org/jira/browse/HTTPCLIENT-1632

//Google Data Compression Proxy
public class DCP implements HttpRequestInterceptor {
    public static final DCP INSTANCE = new DCP();
    
    private static final short[] KEY_1 = new short[] {
            0x5d, 0x30, 0x13, 0x5a, 0xb3, 0x25, 0xce, 0x44, 0xc5, 0xde, 0x8a, 0x80, 0xd2, 0x06, 0x0f, 0xd8, 0x38, 0x5d, 0x8a, 0x0c
    };
    
    private static final short[] KEY_2 = new short[] {
            0xf1, 0x75, 0x13, 0x87, 0x88, 0x50, 0xb7, 0x5c, 0xa9, 0xc5, 0x8c, 0xa0, 0xb3, 0x49, 0xd4, 0xc7, 0x45, 0x3c, 0x73, 0x48
    };
    
    // https://code.google.com/p/datacompressionproxy/source/browse/background.js
    private static final String AUTH_VALUE;
    
    static {
        StringBuilder builder = new StringBuilder();
        for (int i=0; i<KEY_1.length; ++i) {
            String b = Integer.toHexString(KEY_1[i] ^ KEY_2[i]);
            if (b.length() == 1) builder.append('0');
            builder.append(b);
        }
        AUTH_VALUE = builder.toString();
    }
    
    @Override
    public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
        String timestamp = Long.toString(System.currentTimeMillis());
        if (timestamp.length() > 10) timestamp = timestamp.substring(0, 10);
        String sid = CryptoUtils.computeMD5(timestamp + AUTH_VALUE + timestamp);
        String key = "ps=" + timestamp + "-" + getRandom() + "-" + getRandom() + "-" + getRandom() + ", sid=" + sid + ", b=2228, p=0, c=win";
        request.addHeader(new BasicHeader("Chrome-Proxy", key));
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            request.removeHeaders(HttpHeaders.ACCEPT);
            request.addHeader(new BasicHeader(HttpHeaders.ACCEPT, "image/webp,*/*;q=0.8"));
        }
    }
    
    private String getRandom() {
        return Long.toString((long)Math.floor(Math.random() * 1000000000));
    }
    
    private DCP() {}
}
