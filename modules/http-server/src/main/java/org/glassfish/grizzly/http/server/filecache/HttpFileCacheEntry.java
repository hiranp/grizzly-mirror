/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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
package org.glassfish.grizzly.http.server.filecache;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import org.glassfish.grizzly.http.util.Base64Utils;
import org.glassfish.grizzly.http.util.BufferChunk;

/**
 *
 * @author gustav trede
 */
public final class HttpFileCacheEntry {
    static final String eol = "\r\n";
    
    final UpdateAbleResponse response;
    final UpdateAbleResponse responseNotModified;
    final byte[] etagHeaderValue;
    
    public final int fileDataRamSize;    
    public final HttpFileCache hc;

    public HttpFileCacheEntry(String name,HttpFileCache hc,long disksize,ByteChannel bs, final String contentType,String modTime) throws Throwable {
        this.hc = hc;
        boolean compress = !contentType.startsWith("image");
        final long ms = hc.MAX_PER_FILE_INRAMSIZE_BYTES;
        if (disksize >= (compress ? ms * 2 : ms)) {
            throw new IOException("Not caching too big file:"+name+" raw size: " + disksize);
        }
        byte[] data = new byte[(int) disksize];
        MyByteArrayOutputStream bout = compress?new MyByteArrayOutputStream((int)disksize):null;
        hc.fileLoader.loadFile(hc.MD5_, bs, data, bout, compress);
        int dataLength=data.length;
        String etagString = Base64Utils.encodeToString(hc.MD5_.digest(), false);
        etagHeaderValue = ("If-None-Match: " + etagString).getBytes();
        if (compress = compress && bout.count < disksize) {
            data = bout.buf;
            dataLength = bout.count;
        }
        if ((fileDataRamSize = data.length) > ms) {
            throw new IOException("Not caching too big file:"+name+" compressed size:" + fileDataRamSize);
        }
        String maxage = Integer.toString(hc.HTTP_HEADER_EXPIRES_MINUTES*60);
        boolean usemd5 = hc.useContentMD5header;
        response = new UpdateAbleResponse("200 OK", data,dataLength, 
                "Expires: ", HttpFileCache.expireHTTPtimestamp.s, 
                "Cache-Control: max-age=" + maxage,"        ", 
                "max-age: " + maxage, "        ", 
                "Content-Length: ", Integer.toString(data.length), 
                "Etag: ", etagString, 
                "Last-Modified: ",modTime, 
                "Content-Type: ", contentType, 
                compress ? 
                "Content-Encoding: deflate\r\nAge: " : "Age: ", "0", 
                usemd5 ? "Content-MD5: " : "",
                usemd5 ? etagString : "");
        responseNotModified = new UpdateAbleResponse("304 Not Modified", "Etag: ", etagString);
    }

    int getRamUsage(){
        return 64+ response.getRamUsage()+responseNotModified.getRamUsage()+
                etagHeaderValue.length+48;//concurrenthashmap estimation        
    }
    
    void updateHeaders(byte[] expminbytes, byte[] curtime, byte[] expires) {
        response.updateHeaders(expminbytes, curtime, expires);
        UpdateAbleResponse.put(curtime, responseNotModified.dateOffset, responseNotModified.dataAdr);
    }
    
    ByteBuffer getResponse(final BufferChunk mb) {
        return (mb.getBuffer().indexOf(etagHeaderValue, 0) >= 0) ? responseNotModified.data : response.data;
    }

    @Override
    public String toString() {
        return " FileDataRamSize " +fileDataRamSize + " respSize:" + response.data.remaining() + " Etag:" + new String(etagHeaderValue);
    }    
    
    
    class MyByteArrayOutputStream extends OutputStream {
        final byte buf[];
        int count;

        public MyByteArrayOutputStream(int size) {
            buf = new byte[size];
        }

        @Override
        public void write(int b) {
            buf[count++] = (byte) b;
        }

        @Override
        public void write(byte b[], int off, int len) {   
            System.arraycopy(b, off, buf, count, len);
            count += len;
        }

        @Override
        public void close() throws IOException {}
    }
    
}
