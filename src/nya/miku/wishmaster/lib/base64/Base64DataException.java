/*
 * Из исходных кодов Android (android.util.Base64DataException), в целях совместимости со старыми версиями Android (API < 8)
 * 
 */

package nya.miku.wishmaster.lib.base64;

import java.io.IOException;

/**
 * This exception is thrown by {@link Base64InputStream} or {@link Base64OutputStream}
 * when an error is detected in the data being decoded.  This allows problems with the base64 data
 * to be disambiguated from errors in the underlying streams (e.g. actual connection errors.)
 */
@SuppressWarnings("serial")
public class Base64DataException extends IOException {
    public Base64DataException(String detailMessage) {
        super(detailMessage);
    }
}
