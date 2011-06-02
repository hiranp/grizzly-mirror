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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.ByteBufferArray;

/**
 *
 * @author gustav trede
 */
class DirectBufferWraper implements Buffer {

    final ByteBuffer buf;

    public DirectBufferWraper(int size) {
        this(ByteBuffer.allocateDirect(size));
    }    
    public DirectBufferWraper(ByteBuffer buf) {
        this.buf = buf.asReadOnlyBuffer();
    }        
    
    @Override
    public ByteBuffer toByteBuffer() {
        return buf;//.asReadOnlyBuffer();
    }
    
    @Override
    public Object underlying() {
        return buf;//.asReadOnlyBuffer();
    }
    
    @Override
    public boolean isDirect() {
        return true;
    }
    
    @Override
    public boolean isComposite() {
        return false;
    }

    @Override
    public int remaining() {
        return buf.remaining();
    }

    @Override
    public boolean hasRemaining() {
        return buf.hasRemaining();
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }
    
   @Override
    public int position() {
        return buf.position();
    }

    @Override
    public Buffer position(int newPosition) {
        buf.position(newPosition);        
        return this;
    }
    
    @Override
    public boolean allowBufferDispose() {
        return false;
    }

    @Override
    public void allowBufferDispose(boolean allowBufferDispose) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean tryDispose() {
        return false;
        //throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void dispose() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public ByteBuffer toByteBuffer(int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public Buffer prepend(Buffer header) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void trim() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void shrink() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer split(int splitPosition) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int capacity() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int limit() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer limit(int newLimit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer mark() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer reset() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer clear() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer flip() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer rewind() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer slice() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer slice(int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer duplicate() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer asReadOnlyBuffer() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public byte get() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(byte b) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public byte get(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(int index, byte b) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer get(byte[] dst) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer get(byte[] dst, int offset, int length) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer get(ByteBuffer dst) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer get(ByteBuffer dst, int offset, int length) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(Buffer src) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(Buffer src, int position, int length) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(ByteBuffer src) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(ByteBuffer src, int position, int length) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(byte[] src) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put(byte[] src, int offset, int length) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer compact() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ByteOrder order() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer order(ByteOrder bo) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public char getChar() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putChar(char value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public char getChar(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putChar(int index, char value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public short getShort() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putShort(short value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public short getShort(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putShort(int index, short value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getInt() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putInt(int value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int getInt(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putInt(int index, int value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long getLong() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putLong(long value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public long getLong(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putLong(int index, long value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float getFloat() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putFloat(float value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public float getFloat(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putFloat(int index, float value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getDouble() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putDouble(double value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public double getDouble(int index) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer putDouble(int index, double value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String toStringContent() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String toStringContent(Charset charset) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String toStringContent(Charset charset, int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ByteBufferArray toByteBufferArray() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ByteBufferArray toByteBufferArray(ByteBufferArray array) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ByteBufferArray toByteBufferArray(int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ByteBufferArray toByteBufferArray(ByteBufferArray array, int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BufferArray toBufferArray() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BufferArray toBufferArray(BufferArray array) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BufferArray toBufferArray(int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public BufferArray toBufferArray(BufferArray array, int position, int limit) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int indexOf(byte[] ba, int spos) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public int compareTo(Buffer o) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Buffer put8BitString(String s) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
}
