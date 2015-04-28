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

package nya.miku.wishmaster.chans.cirno;

import java.io.InputStream;
import java.text.DateFormat;

import nya.miku.wishmaster.api.models.PostModel;
import nya.miku.wishmaster.api.util.WakabaReader;

/**
 * Парсер страниц 410chan.org, корректно определяется сажа (символ ⇩ в теме сообщения)
 * @author miku-nyan
 *
 */
public class Chan410Reader extends WakabaReader {
    
    public Chan410Reader(InputStream in) {
        super(in, DateFormats.CHAN_410_DATE_FORMAT);
    }
    
    public Chan410Reader(InputStream in, DateFormat dateFormat) {
        super(in, dateFormat);
    }
    
    @Override
    protected void postprocessPost(PostModel post) {
        if (post.subject.contains("\u21E9")) post.sage = true;
    }
}
