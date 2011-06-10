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

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import sun.misc.Unsafe;
import sun.nio.ch.DirectBuffer;

/**
 *
 * @author gustav trede
 */
class UpdateAbleResponse {
    static final String eol = "\r\n";    
    static final Unsafe unsafe; 
    static{
        Unsafe unsafe_ = null;        
        try{
            unsafe_ = Unsafe.getUnsafe();
        }catch(SecurityException se){
            try {
                Field f = Unsafe.class.getDeclaredField("theUnsafe");
                f.setAccessible(true);
                unsafe_ = (Unsafe)f.get(null);            
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }            
        }
        unsafe = unsafe_;          
    }
    static final long arrayOffset = (long) unsafe.arrayBaseOffset(byte[].class); 
    static void put(byte[] ba, int pos, long adr){
        unsafe.copyMemory(ba, arrayOffset + pos, null, adr + pos, ba.length);
    } 
    
    final short dateOffset;
    final short expiresOffset;
    final short ccMaxeAgeOffset;
    final short maxAgeOffset;
    final short etagOffset;
    final ByteBuffer data;
    final long dataAdr;
    
    public UpdateAbleResponse(String firstResponseLine, String... extraHeaderPairs) {
        this(firstResponseLine, null,0, extraHeaderPairs);
    }

    public UpdateAbleResponse(String firstResponseLine, byte[] payload,int payloadLength, String... extraHeaderPairs) {
        StringBuilder sb = new StringBuilder(100 + extraHeaderPairs.length * 50);
        sb.append("HTTP/1.1 ").append(firstResponseLine).append(eol).append("Date: ").append(HttpFileCache.currentHTTPtimestamp).append(eol);
        for (int i = 0; i < extraHeaderPairs.length;) {
            sb.append(extraHeaderPairs[i++]).append(extraHeaderPairs[i]).append(extraHeaderPairs[i++].length() > 0 ? eol : "");
        }
        String ss = sb.append(eol).toString();
        this.dateOffset = endIndexOf(ss, "Date: ");
        this.expiresOffset = endIndexOf(ss, "Expires: ");
        this.ccMaxeAgeOffset = endIndexOf(ss, "max-age=");
        this.maxAgeOffset = endIndexOf(ss, "max-age: ");
        this.etagOffset = endIndexOf(ss, "Etag: ");
        byte[] ssb = ss.getBytes();
        ByteBuffer bt = ByteBuffer.allocateDirect(ssb.length + payloadLength);
        bt.put(ssb);
        if (payload != null) {
            bt.put(payload,0,payloadLength);
        }
        bt.flip();
        this.data = bt;
        this.dataAdr = ((DirectBuffer) bt).address();
    }        
    
    int getRamUsage(){
        return data.capacity()+ 7*2+4+8+4+16+96;       
    }

    private short endIndexOf(String s, String tofind) {
        int i = s.indexOf(tofind);
        return (short) (i < 0 ? -1 : i + tofind.length());
    }

    void updateHeaders(byte[] maxage, byte[] currentTime, byte[] expires) {
        put(currentTime, dateOffset, dataAdr);
        put(maxage, maxAgeOffset, dataAdr);
        put(maxage, ccMaxeAgeOffset, dataAdr);
        put(expires, expiresOffset, dataAdr);
    }
    
}
