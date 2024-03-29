/*
 * $Id: HtmlUtils.java,v 1.15 2008/01/07 22:07:25 rlubke Exp $
 */

/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.faces.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.BitSet;
import java.nio.charset.Charset;

import com.sun.faces.renderkit.RenderKitUtils;

/**
 * Utility class for HTML.
 * Kudos to Adam Winer (Oracle) for much of this code.
 */
public class HtmlUtils {

    //-------------------------------------------------
    // The following methods include the handling of
    // escape characters....
    //-------------------------------------------------

    static public void writeText(Writer out,
                                 char[] buffer,
                                 char[] text) throws IOException {
        writeText(out, buffer, text, 0, text.length);
    }


    /**
     * Write char array text.
     */
    static public void writeText(Writer out,
                                 char[] buff,
                                 char[] text,
                                 int start,
                                 int length) throws IOException {
        int buffLength = buff.length;
        int buffIndex = 0;

        int end = start + length;
        for (int i = start; i < end; i++) {
            buffIndex = writeTextChar(out, text[i], buffIndex, buff, buffLength);
        }

        flushBuffer(out, buff, buffIndex);
    }


    /**
     * Write String text.  
     */
    static public void writeText(Writer out,
                                 char[] buff,
                                 String text,
                                 char[] textBuff) throws IOException {

        int length = text.length();

        if (length >= 16) {
            text.getChars(0, length, textBuff, 0);
            writeText(out, buff, textBuff, 0, length);
        } else {
            int buffLength = buff.length;
            int buffIndex = 0;
            for (int i = 0; i < length; i++) {
                char ch = text.charAt(i);
                buffIndex = writeTextChar(out, ch, buffIndex, buff, buffLength);
            }
            flushBuffer(out, buff, buffIndex);
        }

        
    }

    private static int writeTextChar(Writer out,
                                     char ch,
                                     int buffIndex,
                                     char[] buff,
                                     int buffLength) throws IOException {
        int nextIndex;
        if (ch < 0xA0) {
            // If "?" or over, no escaping is needed (this covers
            // most of the Latin alphabet)
            if (ch >= 0x3f) {
                nextIndex = addToBuffer(out, buff, buffIndex,
                                        buffLength, ch);
            } else if (ch >= 0x27) {  // If above "'"...
                // If between "'" and ";", no escaping is needed
                if (ch < 0x3c) {
                    nextIndex = addToBuffer(out, buff, buffIndex,
                                            buffLength, ch);
                } else if (ch == '<') {
                    nextIndex = addToBuffer(out,
                                            buff,
                                            buffIndex,
                                            buffLength,
                                            LT_CHARS);
                } else if (ch == '>') {
                    nextIndex = addToBuffer(out,
                                            buff,
                                            buffIndex,
                                            buffLength,
                                            GT_CHARS);
                } else {
                    nextIndex = addToBuffer(out, buff, buffIndex,
                                            buffLength, ch);
                }
            } else {
                if (ch == '&') {
                    nextIndex = addToBuffer(out,
                                            buff,
                                            buffIndex,
                                            buffLength,
                                            AMP_CHARS);
                } else {
                    nextIndex = addToBuffer(out, buff, buffIndex,
                                            buffLength, ch);
                }
            }
        } else if (ch <= 0xff) {
            // ISO-8859-1 entities: encode as needed
            nextIndex = addToBuffer(out,
                                    buff,
                                    buffIndex,
                                    buffLength,
                                    sISO8859_1_Entities[ch - 0xA0]);
        } else {
            // Double-byte characters to encode.
            // PENDING: when outputting to an encoding that
            // supports double-byte characters (UTF-8, for example),
            // we should not be encoding
            nextIndex =
                  _writeDecRef(out, buff, buffIndex, buffLength, ch);
        }
        return nextIndex;
    }


    /**
     * Write a string attribute.  Note that this code
     * is duplicated below for character arrays - change both
     * places if you make any changes!!!
     */
    static public void writeAttribute(Writer out,
                                      char[] buff,
                                      String text,
                                      char[] textBuff) throws IOException {

        int length = text.length();
        if (length >= 16) {
            if (length > textBuff.length) {
                // resize our buffer
                textBuff = new char[length * 2];
            }
            text.getChars(0, length, textBuff, 0);
            writeAttribute(out, buff, textBuff, 0, length);
        } else {
            int buffLength = buff.length;
            int buffIndex = 0;
            for (int i = 0; i < length; i++) {
                char ch = text.charAt(i);

                // Tilde or less...
                if (ch < 0xA0) {
                    // If "?" or over, no escaping is needed (this covers
                    // most of the Latin alphabet)
                    if (ch >= 0x3f) {
                        buffIndex = addToBuffer(out, buff, buffIndex,
                                                buffLength, ch);
                    } else if (ch >= 0x27) { // If above "'"...
                        // If between "'" and ";", no escaping is needed
                        if (ch < 0x3c) {
                            buffIndex = addToBuffer(out, buff, buffIndex,
                                                    buffLength, ch);
                        } else if (ch == '<') {
                            buffIndex = addToBuffer(out,
                                                    buff,
                                                    buffIndex,
                                                    buffLength,
                                                    LT_CHARS);
                        } else if (ch == '>') {
                            buffIndex = addToBuffer(out,
                                                    buff,
                                                    buffIndex,
                                                    buffLength,
                                                    GT_CHARS);
                        } else {
                            buffIndex = addToBuffer(out, buff, buffIndex,
                                                    buffLength, ch);
                        }
                    } else {
                        if (ch == '&') {
                            // HTML 4.0, section B.7.1: ampersands followed by
                            // an open brace don't get escaped
                            if ((i + 1 < length) && (text.charAt(i + 1)
                                                     == '{')) {
                                buffIndex = addToBuffer(out,
                                                        buff,
                                                        buffIndex,
                                                        buffLength,
                                                        ch);
                            } else {
                                buffIndex = addToBuffer(out,
                                                        buff,
                                                        buffIndex,
                                                        buffLength,
                                                        AMP_CHARS);
                            }
                        } else if (ch == '"') {
                            buffIndex = addToBuffer(out,
                                                    buff,
                                                    buffIndex,
                                                    buffLength,
                                                    QUOT_CHARS);
                        } else {
                            buffIndex = addToBuffer(out, buff, buffIndex,
                                                    buffLength, ch);
                        }
                    }
                } else if (ch <= 0xff) {
                    // ISO-8859-1 entities: encode as needed
                    buffIndex = addToBuffer(out,
                                            buff,
                                            buffIndex,
                                            buffLength,
                                            sISO8859_1_Entities[ch - 0xA0]);
                } else {
                    // Double-byte characters to encode.
                    // PENDING: when outputting to an encoding that
                    // supports double-byte characters (UTF-8, for example),
                    // we should not be encoding
                    buffIndex =
                          _writeDecRef(out, buff, buffIndex, buffLength, ch);
                }
            }

            flushBuffer(out, buff, buffIndex);
        }
    }


    static public void writeAttribute(Writer out,
                                      char[] buffer,
                                      char[] text) throws IOException {
        writeAttribute(out, buffer, text, 0, text.length);
    }


    /**
     * Write a character array attribute.  Note that this code
     * is duplicated above for string - change both places if you make
     * any changes!!!
     */
    static public void writeAttribute(Writer out,
                                      char[] buff,
                                      char[] text,
                                      int start,
                                      int length) throws IOException {
        int buffLength = buff.length;
        int buffIndex = 0;

        int end = start + length;
        for (int i = start; i < end; i++) {
            char ch = text[i];

            // Tilde or less...
            if (ch < 0xA0) {
                // If "?" or over, no escaping is needed (this covers
                // most of the Latin alphabet)
                if (ch >= 0x3f) {
                    buffIndex = addToBuffer(out, buff, buffIndex,
                                            buffLength, ch);
                } else if (ch >= 0x27) { // If above "'"...
                    if (ch < 0x3c) {
                        // If between "'" and ";", no escaping is needed
                        buffIndex = addToBuffer(out, buff, buffIndex,
                                                buffLength, ch);                       
                    } else if (ch == '<') {
                        buffIndex = addToBuffer(out,
                                                buff,
                                                buffIndex,
                                                buffLength,
                                                LT_CHARS);
                    } else if (ch == '>') {
                        buffIndex = addToBuffer(out,
                                                buff,
                                                buffIndex,
                                                buffLength,
                                                GT_CHARS);
                    } else {
                        buffIndex = addToBuffer(out, buff, buffIndex,
                                                buffLength, ch);
                    }
                } else {
                    if (ch == '&') {
                        // HTML 4.0, section B.7.1: ampersands followed by
                        // an open brace don't get escaped
                        if ((i + 1 < end) && (text[i + 1] == '{')) {
                            buffIndex = addToBuffer(out,
                                                    buff,
                                                    buffIndex,
                                                    buffLength,
                                                    ch);
                        } else {
                            buffIndex = addToBuffer(out,
                                                buff,
                                                buffIndex,
                                                buffLength,
                                                AMP_CHARS);
                        }
                    } else if (ch == '"') {
                        buffIndex = addToBuffer(out,
                                                buff,
                                                buffIndex,
                                                buffLength,
                                                QUOT_CHARS);
                    } else {
                        buffIndex = addToBuffer(out, buff, buffIndex,
                                                buffLength, ch);
                    }
                }
            } else if (ch <= 0xff) {
                // ISO-8859-1 entities: encode as needed
                buffIndex = addToBuffer(out,
                                        buff,
                                        buffIndex,
                                        buffLength,
                                        sISO8859_1_Entities[ch - 0xA0]);
            } else {
                // Double-byte characters to encode.
                // PENDING: when outputting to an encoding that
                // supports double-byte characters (UTF-8, for example),
                // we should not be encoding
                buffIndex = _writeDecRef(out, buff, buffIndex, buffLength, ch);
            }
        }

        flushBuffer(out, buff, buffIndex);
    }


    /**
     * Writes a character as a decimal escape.  Hex escapes are smaller than
     * the decimal version, but Netscape didn't support hex escapes until
     * 4.7.4.
     */
    static private int _writeDecRef(Writer out,
                                    char[] buffer,
                                    int bufferIndex,
                                    int bufferLength,
                                    char ch) throws IOException {
        if (ch == '\u20ac') {
            bufferIndex = addToBuffer(out,
                                      buffer,
                                      bufferIndex,
                                      bufferLength,
                                      EURO_CHARS);
            return bufferIndex;
        }
        bufferIndex = addToBuffer(out,
                                  buffer,
                                  bufferIndex,
                                  bufferLength,
                                  DEC_REF_START);
        // Formerly used String.valueOf().  This version tests out
        // about 40% faster in a microbenchmark (and on systems where GC is
        // going gonzo, it should be even better)
        int i = (int) ch;
        if (i > 10000) {
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, (char) i);
            i = i % 10000;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 1000))));
            i = i % 1000;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 100))));
            i = i % 100;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 10))));
            i = i % 10;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + i)));
        } else if (i > 1000) {
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 1000))));
            i = i % 1000;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 100))));
            i = i % 100;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 10))));
            i = i % 10;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + i)));
        } else {
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 100))));
            i = i % 100;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + (i / 10))));
            i = i % 10;
            bufferIndex = addToBuffer(out, buffer, bufferIndex, bufferLength, ((char) ('0' + i)));
        }

        return addToBuffer(out, buffer, bufferIndex, bufferLength, ';');
        
    }

    // 
    // Buffering scheme: we use a tremendously simple buffering
    // scheme that greatly reduces the number of calls into the
    // Writer/PrintWriter.  In practice this has produced significant
    // measured performance gains (at least in JDK 1.3.1).
    //

    /**
     * Add a character to the buffer, flushing the buffer if the buffer is
     * full, and returning the new buffer index
     */
    private static int addToBuffer(Writer out,
                                   char[] buffer,
                                   int bufferIndex,
                                   int bufferLength,
                                   char ch) throws IOException {
        if (bufferIndex >= bufferLength) {
            out.write(buffer, 0, bufferIndex);
            bufferIndex = 0;
        }

        buffer[bufferIndex] = ch;

        return bufferIndex + 1;
    }

    /**
     * Add an array of characters to the buffer, flushing the buffer
     * if the buffer is full, and returning the new buffer index. 
     */
    private static int addToBuffer(Writer out,
                                   char[] buffer,
                                   int bufferIndex,
                                   int bufferLength,
                                   char[] toAdd) throws IOException {

        if (bufferIndex >= bufferLength
            || (toAdd.length + bufferIndex >= bufferLength)) {
            out.write(buffer, 0, bufferIndex);
            bufferIndex = 0;
        }
        System.arraycopy(toAdd, 0, buffer, bufferIndex, toAdd.length);
        return bufferIndex + toAdd.length;

    }


    /**
     * Flush the contents of the buffer to the output stream
     * and return the reset buffer index
     */
    private static int flushBuffer(Writer out,
                                   char[] buffer,
                                   int bufferIndex) throws IOException {
        if (bufferIndex > 0)
            out.write(buffer, 0, bufferIndex);

        return 0;
    }


    private HtmlUtils() {
    }


    /**
     * Writes a string into URL-encoded format out to a Writer.
     * <p/>
     * All characters before the start of the query string will be encoded
     * using ISO-8859-1.
     * PENDING: Ideally, we'd encode characters before the query string
     * using UTF-8, which is what the HTML spec recommends.  Unfortunately,
     * the Apache server doesn't support this until 2.0.
     * <p/>
     * Characters after the start of the query string will be encoded
     * using a client-defined encoding.  You'll need to use the encoding
     * that the server will expect.  (HTML forms will generate query
     * strings using the character encoding that the HTML itself was
     * generated in.)
     * <p/>
     * All characters will be encoded as needed for URLs, with the exception
     * of the percent symbol ("%").  Because this is the character
     * itself used for escaping, attempting to escape this character
     * would cause this code to double-escape some strings.  It also may
     * be necessary to pre-escape some characters.  In particular, a
     * question mark ("?") is considered the start of the query string.
     * <p/>
     *
     * <p>
     * NOTE:  This is method is duplicated below.  The difference being
     *  the acceptance of a char[] for the text to write.  Any changes made
     *  here, should be made below.
     * </p>
     *
     * @param out           a Writer for the output
     * @param text          the unencoded (or partially encoded) String
     * @param queryEncoding the character set encoding for after the first
     *                      question mark
     */
    static public void writeURL(Writer out,
                                String text,
                                char[] textBuff,
                                String queryEncoding,
                                String contentType)
          throws IOException, UnsupportedEncodingException {

        int length = text.length();
        if (length >= 16) {
            text.getChars(0, length, textBuff, 0);
            writeURL(out, textBuff, 0, length, queryEncoding, contentType);
        } else {
            for (int i = 0; i < length; i++) {
                char ch = text.charAt(i);

                if ((ch < 33) || (ch > 126)) {
                    if (ch == ' ') {
                        out.write('+');
                    } else {
                        // ISO-8859-1.  Blindly assume the character will be < 255.
                        // Not much we can do if it isn't.
                        writeURIDoubleHex(out, ch);

                    }
                }
                // DO NOT encode '%'.  If you do, then for starters,
                // we'll double-encode anything that's pre-encoded.
                // And, what's worse, there becomes no way to use
                // characters that must be encoded if you
                // don't want them to be interpreted, like '?' or '&'.
                // else if('%' == ch)
                // {
                //   writeURIDoubleHex(out, ch);
                // }
                else if (ch == '"') {
                    out.write("%22");
                }
                // Everything in the query parameters will be decoded
                // as if it were in the request's character set.  So use
                // the real encoding for those!
                else if (ch == '?') {
                    out.write('?');
                    encodeURIString(out,
                                    text,
                                    queryEncoding,
                                    RenderKitUtils.isXml(contentType),
                                    i + 1);
                    return;
                } else {
                    out.write(ch);
                }
            }
        }
    }


    /**
     * Writes a string into URL-encoded format out to a Writer.
     * <p/>
     * All characters before the start of the query string will be encoded
     * using ISO-8859-1.
     * PENDING: Ideally, we'd encode characters before the query string
     * using UTF-8, which is what the HTML spec recommends.  Unfortunately,
     * the Apache server doesn't support this until 2.0.
     * <p/>
     * Characters after the start of the query string will be encoded
     * using a client-defined encoding.  You'll need to use the encoding
     * that the server will expect.  (HTML forms will generate query
     * strings using the character encoding that the HTML itself was
     * generated in.)
     * <p/>
     * All characters will be encoded as needed for URLs, with the exception
     * of the percent symbol ("%").  Because this is the character
     * itself used for escaping, attempting to escape this character
     * would cause this code to double-escape some strings.  It also may
     * be necessary to pre-escape some characters.  In particular, a
     * question mark ("?") is considered the start of the query string.
     * <p/>
     * <p>
     * NOTE:  This is method is duplicated above.  The difference being
     *  the acceptance of a String for the text to write.  Any changes made
     *  here, should be made above.
     * </p>
     *
     * @param out           a Writer for the output
     * @param textBuff    char[] containing the content to write
     * @param queryEncoding the character set encoding for after the first
     *                      question mark
     */
    static public void writeURL(Writer out,
                                char[] textBuff,
                                int start,
                                int len,
                                String queryEncoding,
                                String contentType)
        throws IOException, UnsupportedEncodingException {

        int end = start + len;
        for (int i = start; i < end; i++) {
            char ch = textBuff[i];

            if ((ch < 33) || (ch > 126)) {
                if (ch == ' ') {
                    out.write('+');
                } else {
                    // ISO-8859-1.  Blindly assume the character will be < 255.
                    // Not much we can do if it isn't.
                    writeURIDoubleHex(out, ch);

                }
            }
            // DO NOT encode '%'.  If you do, then for starters,
            // we'll double-encode anything that's pre-encoded.
            // And, what's worse, there becomes no way to use
            // characters that must be encoded if you
            // don't want them to be interpreted, like '?' or '&'.
            // else if('%' == ch)
            // {
            //   writeURIDoubleHex(out, ch);
            // }
            else if (ch == '"') {
                out.write("%22");
            }
            // Everything in the query parameters will be decoded
            // as if it were in the request's character set.  So use
            // the real encoding for those!
            else if (ch == '?') {
           out.write('?');
                encodeURIString(out,
                                textBuff,
                                queryEncoding,
                                RenderKitUtils.isXml(contentType),
                                i + 1,
                                end);
                return;
            } else {
                out.write(ch);
            }
        }
    }


    // Encode a String into URI-encoded form.  This code will
    // appear rather (ahem) similar to java.net.URLEncoder
    // This is duplicated below accepting a char[] for the content
    // to write.  Any changes here, should be made there as well.
     static private void encodeURIString(Writer out,
                                        String text,
                                        String encoding,
                                        boolean isXml,
                                        int start)
     throws IOException {   
        MyByteArrayOutputStream buf = null;
        OutputStreamWriter writer = null;
        char[] charArray = null;

        int length = text.length();
        for (int i = start; i < length; i++) {
            char ch = text.charAt(i);
            if (DONT_ENCODE_SET.get(ch)) {
                if (isXml && ch == '&') {                  
                    if (((i + 1) < length) && isAmpEscaped(text, i + 1)) {
                        out.write(ch);
                        continue;
                    }
                    out.write(AMP_CHARS);
                } else {
                    out.write(ch);
                }
            } else {
                if (buf == null) {
                    buf = new MyByteArrayOutputStream(MAX_BYTES_PER_CHAR);
                    if (encoding != null) {
                        writer = new OutputStreamWriter(buf, encoding);
                    } else {
                        writer = new OutputStreamWriter(buf);
                    }
                    charArray = new char[1];
                }

                // convert to external encoding before hex conversion
                try {
                    // An inspection of OutputStreamWriter reveals
                    // that write(char) always allocates a one element
                    // character array.  We can reuse our own.
                    charArray[0] = ch;
                    writer.write(charArray, 0, 1);
                    writer.flush();
                } catch (IOException e) {
                    buf.reset();
                    continue;
                }

                byte[] ba = buf.getBuf();
                for (int j = 0, size = buf.size(); j < size; j++) {
                    writeURIDoubleHex(out, ba[j] + 256);
                }

                buf.reset();
            }
        }      
    }

    // Encode a String into URI-encoded form.  This code will
    // appear rather (ahem) similar to java.net.URLEncoder
    // This is duplicated above accepting a String for the content
    // to write.  Any changes here, should be made there as well.
     static private void encodeURIString(Writer out,
                                        char[] textBuff,
                                        String encoding,
                                        boolean isXml,
                                        int start,
                                        int end)
     throws IOException {
        MyByteArrayOutputStream buf = null;
        OutputStreamWriter writer = null;
        char[] charArray = null;


        for (int i = start; i < end; i++) {
            char ch = textBuff[i];
            if (DONT_ENCODE_SET.get(ch)) {
                if (isXml && ch == '&') {
                    if (((i + 1) < end) && isAmpEscaped(textBuff, i + 1)) {
                        out.write(ch);
                        continue;
                    }
                    out.write(AMP_CHARS);
                } else {
                    out.write(ch);
                }
            } else {
                if (buf == null) {
                    buf = new MyByteArrayOutputStream(MAX_BYTES_PER_CHAR);
                    if (encoding != null) {
                        writer = new OutputStreamWriter(buf, encoding);
                    } else {
                        writer = new OutputStreamWriter(buf);
                    }
                    charArray = new char[1];
                }

                // convert to external encoding before hex conversion
                try {
                    // An inspection of OutputStreamWriter reveals
                    // that write(char) always allocates a one element
                    // character array.  We can reuse our own.
                    charArray[0] = ch;
                    writer.write(charArray, 0, 1);
                    writer.flush();
                } catch (IOException e) {
                    buf.reset();
                    continue;
                }

                byte[] ba = buf.getBuf();
                for (int j = 0, size = buf.size(); j < size; j++) {
                    writeURIDoubleHex(out, ba[j] + 256);
                }

                buf.reset();
            }
        }
    }
    

    // NOTE: Any changes made to this method should be made
    //  in the associated method that accepts a char[] instead
    //  of String
    static private boolean isAmpEscaped(String text, int idx) {       
        for (int i = 1, ix = idx; i < AMP_CHARS.length; i++, ix++) {
            if (text.charAt(ix) == AMP_CHARS[i]) {
                continue;
            }
            return false;
        }
        return true;
    }

    // NOTE: Any changes made to this method should be made
    //  in the associated method that accepts a String instead
    //  of char[]
    static private boolean isAmpEscaped(char[] text, int idx) {
         for (int i = 1, ix = idx; i < AMP_CHARS.length; i++, ix++) {
            if (text[ix] == AMP_CHARS[i]) {
                continue;
            }
            return false;
        }
        return true;
    }


    static private void writeURIDoubleHex(Writer out,
                                          int i) throws IOException {
        out.write('%');
        out.write(intToHex((i >> 4) % 0x10));
        out.write(intToHex(i % 0x10));
    }


    static private char intToHex(int i) {
        if (i < 10)
            return ((char) ('0' + i));
        else
            return ((char) ('A' + (i - 10)));
    }

    static private final char[] AMP_CHARS = "&amp;".toCharArray();
    static private final char[] QUOT_CHARS = "&quot;".toCharArray();
    static private final char[] GT_CHARS = "&gt;".toCharArray();
    static private final char[] LT_CHARS = "&lt;".toCharArray();
    static private final char[] EURO_CHARS = "&euro;".toCharArray();
    static private final char[] DEC_REF_START = "&#".toCharArray();
    static private final int MAX_BYTES_PER_CHAR = 10;
    static private final BitSet DONT_ENCODE_SET = new BitSet(256);


    // See: http://www.ietf.org/rfc/rfc2396.txt
    // We're not fully along for that ride either, but we do encode
    // ' ' as '%20', and don't bother encoding '~' or '/'
    static {
        for (int i = 'a'; i <= 'z'; i++) {
            DONT_ENCODE_SET.set(i);
        }

        for (int i = 'A'; i <= 'Z'; i++) {
            DONT_ENCODE_SET.set(i);
        }

        for (int i = '0'; i <= '9'; i++) {
            DONT_ENCODE_SET.set(i);
        }
        
        // Don't encode '%' - we don't want to double encode anything.
        DONT_ENCODE_SET.set('%');
        // Ditto for '+', which is an encoded space
        DONT_ENCODE_SET.set('+');

        DONT_ENCODE_SET.set('#');
        DONT_ENCODE_SET.set('&');
        DONT_ENCODE_SET.set('=');
        DONT_ENCODE_SET.set('-');
        DONT_ENCODE_SET.set('_');
        DONT_ENCODE_SET.set('.');
        DONT_ENCODE_SET.set('*');
        DONT_ENCODE_SET.set('~');
        DONT_ENCODE_SET.set('/');
        DONT_ENCODE_SET.set('\'');
        DONT_ENCODE_SET.set('!');
        DONT_ENCODE_SET.set('(');
        DONT_ENCODE_SET.set(')');
        DONT_ENCODE_SET.set(';');
    }


    //
    // Entities from HTML 4.0, section 24.2.1; character codes 0xA0 to 0xFF
    //
    static private char[][] sISO8859_1_Entities = new char[][]{
        "&nbsp;".toCharArray(),
        "&iexcl;".toCharArray(),
        "&cent;".toCharArray(),
        "&pound;".toCharArray(),
        "&curren;".toCharArray(),
        "&yen;".toCharArray(),
        "&brvbar;".toCharArray(),
        "&sect;".toCharArray(),
        "&uml;".toCharArray(),
        "&copy;".toCharArray(),
        "&ordf;".toCharArray(),
        "&laquo;".toCharArray(),
        "&not;".toCharArray(),
        "&shy;".toCharArray(),
        "&reg;".toCharArray(),
        "&macr;".toCharArray(),
        "&deg;".toCharArray(),
        "&plusmn;".toCharArray(),
        "&sup2;".toCharArray(),
        "&sup3;".toCharArray(),
        "&acute;".toCharArray(),
        "&micro;".toCharArray(),
        "&para;".toCharArray(),
        "&middot;".toCharArray(),
        "&cedil;".toCharArray(),
        "&sup1;".toCharArray(),
        "&ordm;".toCharArray(),
        "&raquo;".toCharArray(),
        "&frac14;".toCharArray(),
        "&frac12;".toCharArray(),
        "&frac34;".toCharArray(),
        "&iquest;".toCharArray(),
        "&Agrave;".toCharArray(),
        "&Aacute;".toCharArray(),
        "&Acirc;".toCharArray(),
        "&Atilde;".toCharArray(),
        "&Auml;".toCharArray(),
        "&Aring;".toCharArray(),
        "&AElig;".toCharArray(),
        "&Ccedil;".toCharArray(),
        "&Egrave;".toCharArray(),
        "&Eacute;".toCharArray(),
        "&Ecirc;".toCharArray(),
        "&Euml;".toCharArray(),
        "&Igrave;".toCharArray(),
        "&Iacute;".toCharArray(),
        "&Icirc;".toCharArray(),
        "&Iuml;".toCharArray(),
        "&ETH;".toCharArray(),
        "&Ntilde;".toCharArray(),
        "&Ograve;".toCharArray(),
        "&Oacute;".toCharArray(),
        "&Ocirc;".toCharArray(),
        "&Otilde;".toCharArray(),
        "&Ouml;".toCharArray(),
        "&times;".toCharArray(),
        "&Oslash;".toCharArray(),
        "&Ugrave;".toCharArray(),
        "&Uacute;".toCharArray(),
        "&Ucirc;".toCharArray(),
        "&Uuml;".toCharArray(),
        "&Yacute;".toCharArray(),
        "&THORN;".toCharArray(),
        "&szlig;".toCharArray(),
        "&agrave;".toCharArray(),
        "&aacute;".toCharArray(),
        "&acirc;".toCharArray(),
        "&atilde;".toCharArray(),
        "&auml;".toCharArray(),
        "&aring;".toCharArray(),
        "&aelig;".toCharArray(),
        "&ccedil;".toCharArray(),
        "&egrave;".toCharArray(),
        "&eacute;".toCharArray(),
        "&ecirc;".toCharArray(),
        "&euml;".toCharArray(),
        "&igrave;".toCharArray(),
        "&iacute;".toCharArray(),
        "&icirc;".toCharArray(),
        "&iuml;".toCharArray(),
        "&eth;".toCharArray(),
        "&ntilde;".toCharArray(),
        "&ograve;".toCharArray(),
        "&oacute;".toCharArray(),
        "&ocirc;".toCharArray(),
        "&otilde;".toCharArray(),
        "&ouml;".toCharArray(),
        "&divide;".toCharArray(),
        "&oslash;".toCharArray(),
        "&ugrave;".toCharArray(),
        "&uacute;".toCharArray(),
        "&ucirc;".toCharArray(),
        "&uuml;".toCharArray(),
        "&yacute;".toCharArray(),
        "&thorn;".toCharArray(),
        "&yuml;".toCharArray()
    };


    //----------------------------------------------------------
    // The following is used to verify encodings
    //----------------------------------------------------------
    //
    static public boolean validateEncoding(String encoding) {
        return Charset.isSupported(encoding);
    }
    

    //----------------------------------------------------------
    // The following is used to verify "empty" Html elements.
    // "Empty" Html elements are those that do not require an
    // ending tag.  For example, <br>  or <hr>...
    //----------------------------------------------------------

    static public boolean isEmptyElement(String name) {
        char firstChar = name.charAt(0);
        if (firstChar > _LAST_EMPTY_ELEMENT_START)
            return false;

        // Can we improve performance here?  It's certainly slower to use
        // a HashMap, at least if we can't assume the input name is lowercased.
        String[] array = emptyElementArr[firstChar];
        if (array != null) {
            for (int i = array.length - 1; i >= 0; i--) {
                if (name.equalsIgnoreCase(array[i]))
                    return true;
            }
        }
        return false;
    }


    static private char _LAST_EMPTY_ELEMENT_START = 'p';
    static private String[][] emptyElementArr =
        new String[((int) _LAST_EMPTY_ELEMENT_START) + 1][];

    static private String[] aNames = new String[]{
        "area",
    };

    static private String[] bNames = new String[]{
        "br",
        "base",
        "basefont",
    };

    static private String[] cNames = new String[]{
        "col",
    };

    static private String[] fNames = new String[]{
        "frame",
    };

    static private String[] hNames = new String[]{
        "hr",
    };

    static private String[] iNames = new String[]{
        "img",
        "input",
        "isindex",
    };

    static private String[] lNames = new String[]{
        "link",
    };

    static private String[] mNames = new String[]{
        "meta",
    };

    static private String[] pNames = new String[]{
        "param",
    };


    static {
        emptyElementArr['a'] = aNames;
        emptyElementArr['A'] = aNames;
        emptyElementArr['b'] = bNames;
        emptyElementArr['B'] = bNames;
        emptyElementArr['c'] = cNames;
        emptyElementArr['C'] = cNames;
        emptyElementArr['f'] = fNames;
        emptyElementArr['F'] = fNames;
        emptyElementArr['h'] = hNames;
        emptyElementArr['H'] = hNames;
        emptyElementArr['i'] = iNames;
        emptyElementArr['I'] = iNames;
        emptyElementArr['l'] = lNames;
        emptyElementArr['L'] = lNames;
        emptyElementArr['m'] = mNames;
        emptyElementArr['M'] = mNames;
        emptyElementArr['p'] = pNames;
        emptyElementArr['P'] = pNames;
    }
    
    
    // ----------------------------------------------------------- Inner Classes


    /**
     * <p>Private implementation of ByteArrayOutputStream.</p>
     */
    private static class MyByteArrayOutputStream extends ByteArrayOutputStream {

        
        public MyByteArrayOutputStream(int initialCapacity) {
            super(initialCapacity);
        }
        /**
         * Obtain access to the underlying byte array to prevent 
         * unecessary temp object creation.
         * @return <code>buf</code>
         */
        public byte[] getBuf() {
            return buf;
        }
        
    }
}
