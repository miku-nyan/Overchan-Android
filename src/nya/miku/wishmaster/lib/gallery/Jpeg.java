/*
 * Методы для определения нестандартных JPEG, которые не отображаются SubsamplingImageView.
 * Взято из 2ch Browser <https://github.com/vortexwolf/2ch-Browser/>
 * 
 */

package nya.miku.wishmaster.lib.gallery;

import java.io.File;
import java.io.FileInputStream;

import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;

public class Jpeg {
    private static final String TAG = "Jpeg";
    
    private static byte[] getJpegInfoBytes(File file){
        FileInputStream fis = null;
        byte[] bytes = null;
        try {
            fis = new FileInputStream(file);
            if (fis.read() != 255 || fis.read() != 216) {
                return null; // not JPEG
            }
            
            while (fis.read() == 255) {
                int marker = fis.read();
                int len = fis.read() << 8 | fis.read();
                
                // 192-207, except 196, 200 and 204
                if (marker >= 192 && marker <= 207 && marker != 196 && marker != 200 && marker != 204) {
                    bytes = new byte[len - 2];
                    fis.read(bytes, 0, bytes.length);
                    break;
                } else {
                    fis.skip(len - 2);
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            IOUtils.closeQuietly(fis);
        }
        
        return bytes;
    }
    
    public static boolean isNonStandardGrayscaleImage(File file) {
        byte[] bytes = getJpegInfoBytes(file);
        if (bytes == null) {
            return false;
        }
        
        // read subsampling information
        byte numberF = bytes[5]; // byte #6
        String[] samplings = new String[numberF];
        
        for (int i = 0; i < numberF; i++) {
            int hv = bytes[7 + i * numberF]; // byte #8, #10...
            
            int h = hv >> 4;
            int v = hv & 0x0F;
            samplings[i] = h + "x" + v;
        }
        
        return samplings.length == 1 && !samplings[0].equals("1x1");
    }
}
