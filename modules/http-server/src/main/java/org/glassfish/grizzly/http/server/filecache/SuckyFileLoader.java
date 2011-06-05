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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.MessageDigest;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import org.glassfish.grizzly.http.server.filecache.HttpFileCacheEntry.MyByteArrayOutputStream;

/**
 * Sucky 1.6 fileloader.
 * @author gustav trede
 */
public class SuckyFileLoader implements HttpFileCacheLoader {

    private FileChangedListener fcli;
    
    public SuckyFileLoader() {
    }

    @Override
    public void setFileChangedListener(FileChangedListener fcli) {
        this.fcli = fcli;
    }        
    
    @Override
    public void loadFile(File file, String custname, String prefixMapingIfDir, boolean followSymlinks) throws IOException {        
        String name = file.toString();
        RandomAccessFile ra = new RandomAccessFile(file, "r");
        try{
            fcli.fileChanged(name, custname!=null?custname:name, ra.getChannel(), file.length(), file.lastModified(), followSymlinks);
        }finally{
            ra.close();
        }
    }

    @Override
    public void loadFile(MessageDigest MD5_, ByteChannel bs, byte[] data, MyByteArrayOutputStream bout) throws IOException {
        final boolean compress = bout !=null;
        Deflater def = compress?new Deflater(Deflater.DEFAULT_COMPRESSION):null;
        DeflaterOutputStream zout = compress?new DeflaterOutputStream(bout, def):null;
        ByteBuffer bb_ = ByteBuffer.wrap(data);
        int r;
        int pos = 0;
        while ((r = bs.read(bb_)) > 0) {
            MD5_.update(data, pos, r);
            if (compress) {
                zout.write(data, pos, r);
            }
            pos += r;
        }
        if (compress){
            zout.finish();
            zout.close();
            def.end();
        }        
    }
    
    @Override
    public void setEnabled(boolean setEnabled) throws IOException {        
    }

    @Override
    public void remove(File f,String optionalmapedname, String host) {
        fcli.fileChanged(optionalmapedname==null?"/"+f.getName():optionalmapedname, host,null,0,0,true);
    }
}
