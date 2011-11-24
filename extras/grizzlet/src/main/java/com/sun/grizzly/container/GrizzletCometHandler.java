/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */


package com.sun.grizzly.container;


import java.io.IOException;

import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.grizzlet.Grizzlet;
import java.util.logging.Logger;


/**
 * Grizzlet implementation of Grizzly {@link CometHandler}.
 * 
 * @author Jeanfrancois Arcand,
 */
public class GrizzletCometHandler implements CometHandler<GrizzletRequest> {
     
    /**
     * The request which will be parked/paused by the Grizzly ARP mechaCometnism.
     */
    private GrizzletRequest req;
    
    /**
     * The Grizzly associated with this CometHandler.
     */
    private Grizzlet grizzlet;
    
    
    /**
     * GrizzletEvent implementatation used wheh the CometSelector close the
     * expires the connection.
     */
    private AsyncConnectionImpl gEvent = new AsyncConnectionImpl();
    
    
    /**
     * Attach the GrizzletRequest which is the connection that will 
     * be paused/parked.
     */
    public void attach(GrizzletRequest req) {
        this.req = req;
    }

    
    /**
     * Invoke the Grizzlet.onPush method in reaction to a CometComet.notify()
     * operations.
     */
    public void onEvent(CometEvent event) throws IOException {
        if (event.getType() == CometEvent.NOTIFY){
            AsyncConnectionImpl grizzletEvent = (AsyncConnectionImpl)event.attachment();
            grizzletEvent.setRequest(req);
            grizzletEvent.setResponse(req.getGrizzletResponse());
            grizzlet.onPush(grizzletEvent);
        }
    }

    
    public void onInitialize(CometEvent event) throws IOException {
    }

    
    /**
     * When the CometContext times out, this method will be invoked and 
     * the associated Grizzlet invoked.
     */
    public void onInterrupt(CometEvent event) throws IOException {
        gEvent.setRequest(req);
        gEvent.setResponse(req.getGrizzletResponse());
        gEvent.setIsResuming(true);
        grizzlet.onPush(gEvent);
        gEvent.setIsResuming(false);
    }

    
    /**
     * Invoked when the Grizzly resume the continuation.
     */ 
    public void onTerminate(CometEvent event) throws IOException {
        onInterrupt(event);
    }

    
    /**
     * Return the associated Grizzlet.
     */
    public Grizzlet getGrizzlet() {
        return grizzlet;
    }

    
    /**
     * Set the Grizzlet.
     */
    public void setGrizzlet(Grizzlet grizzlet) {
        this.grizzlet = grizzlet;
    }

}