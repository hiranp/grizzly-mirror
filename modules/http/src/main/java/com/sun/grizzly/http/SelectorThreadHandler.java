/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
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

package com.sun.grizzly.http;

import com.sun.grizzly.Context;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.util.Copyable;
import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * {@link SelectorHandler} implementation {@link SelectorThread} 
 * passes to {@link Controller}. It is very similar to 
 * {@link TCPSelectorHandler}, however has some difference in preSelect()
 * processing
 * 
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class SelectorThreadHandler extends TCPSelectorHandler
        implements HttpSelectorHandler {
    private SelectorThread selectorThread;
    
    public SelectorThreadHandler() {}

    public SelectorThreadHandler(SelectorThread selectorThread) {
        this.selectorThread = selectorThread;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        SelectorThreadHandler copyHandler = (SelectorThreadHandler) copy;
        copyHandler.selectorThread = selectorThread;
    }

    public SelectorThread getSelectorThread() {
        return selectorThread;
    }

    public void setSelectorThread(SelectorThread selectorThread) {
        this.selectorThread = selectorThread;
    }

    @Override
    public void configureChannel(SelectableChannel channel) throws IOException {
        if (channel instanceof SocketChannel) {
            selectorThread.setSocketOptions(((SocketChannel) channel).socket());
        }
        
        super.configureChannel(channel);
    }
    
    @Override
    public boolean onAcceptInterest(SelectionKey key, Context ctx) throws IOException{
        SelectableChannel channel = acceptWithoutRegistration(key);

        if (channel != null) {
            configureChannel(channel);
            SelectionKey readKey =
                    channel.register(selector, SelectionKey.OP_READ);
            readKey.attach(System.currentTimeMillis());
              
            if (selectorThread.getThreadPool() instanceof StatsThreadPool){
                selectorThread.getRequestGroupInfo().increaseCountOpenConnections();
                ThreadPoolStatistic tps = ((StatsThreadPool)selectorThread
                        .getThreadPool()).getStatistic();
                if (tps != null){
                    tps.incrementTotalAcceptCount();
                    tps.incrementOpenConnectionsCount(readKey.channel());
                }
            }
        }
        return false;
    }
}
