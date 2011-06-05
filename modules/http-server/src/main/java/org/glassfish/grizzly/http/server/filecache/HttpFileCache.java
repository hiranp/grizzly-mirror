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
import org.glassfish.grizzly.http.server.filecache.jmx.FileCache;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.ProcessingState;
import org.glassfish.grizzly.monitoring.jmx.AbstractJmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringAware;
import org.glassfish.grizzly.monitoring.jmx.JmxMonitoringConfig;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.MessageDigest;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**s
 * HTTP1.1 file cache.<br>
 * @author Gustav Trede
 */
public class HttpFileCache implements JmxMonitoringAware<FileCacheProbe> {               
    
    private static final Map<String,String> fileToContentType = 
            new HashMap<String, String>();    
    
    private static final DateFormat dateformatcloner = 
            new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z",Locale.UK);

    private static final byte[] deflateHeader = " deflate".getBytes();
    static volatile StringArray currentHTTPtimestamp;
    static volatile StringArray expireHTTPtimestamp;                     
      
    static{              
        currentHTTPtimestamp = new StringArray(dateformatcloner.format(new Date()));        
        //TODO add more file types and validate that all are valid by the spec.
        addtype("image", "png","gif","jpg","jpeg","bmp");
        addtype("text", "html","css","xml");
        fileToContentType.put("htm","text/html");
        fileToContentType.put("js","application/x-javascript");
        fileToContentType.put("jar","application/octet-stream");
        fileToContentType.put("exe","application/octet-stream");
        fileToContentType.put("bin","application/octet-stream");        
    }
          
    
    private static String getFileContentType(String fname) throws IOException{
        int index = fname.lastIndexOf('.');
        if (index++<=0 && fname.length()==index){
            return null;
        }
        return fileToContentType.get(fname.substring(index).toLowerCase());
    }

    private static void addtype(String a,String...b){
        for (String s:b)
            fileToContentType.put(s.toLowerCase(),(a+'/'+s).toLowerCase());
    }       
        
    private final AbstractJmxMonitoringConfig<FileCacheProbe> monitoringConfig =
         new AbstractJmxMonitoringConfig<FileCacheProbe>(FileCacheProbe.class) {
        @Override
        public JmxObject createManagementObject() {
            return new FileCache(HttpFileCache.this);
        }
    };
    
    private final ConcurrentHashMap<HttpRequestPacket, HttpFileCacheEntry> 
        filecache = new ConcurrentHashMap<HttpRequestPacket, HttpFileCacheEntry>
                (64,(float)0.75,1); 
    final MessageDigest MD5_;   
    final HttpFileCacheLoader fileLoader;      
    
    private volatile ScheduledExecutorService sexs;                  
    
    volatile String xpoweredbyheader = "";//TODO: implement xpoweredby header
    volatile boolean useContentMD5header = false;
    volatile int HTTP_HEADER_EXPIRES_MINUTES = 15;
    volatile long MAX_TOTAL_RAMUSAGE_BYTES = 1L<<32;
    volatile long MAX_PER_FILE_INRAMSIZE_BYTES=1L<<28;
    private volatile long usedRamSizeBytes;
            

    public HttpFileCache(HttpFileCacheLoader fileCacheLoader) {
        fileLoader = fileCacheLoader;
        fileCacheLoader.setFileChangedListener(createListener());
        try {            
            MD5_ = MessageDigest.getInstance("MD5");            
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }        
        setEnabled(true);
    }

    public void setXpoweredbyheader(String xpoweredbyheader) {
        this.xpoweredbyheader = xpoweredbyheader;
    }

    public String getXpoweredbyheader() {
        return xpoweredbyheader;
    }        

    public void setUseContentMD5header(boolean useContentMD5header) {
        this.useContentMD5header = useContentMD5header;
    }

    public boolean getUseContentMD5header() {
        return useContentMD5header;
    }        

    /**
     * Propagates to the entire cache by roughly 1 second delay.
     */
    public void setHTTP_HEADER_EXPIRES_MINUTES(int HTTP_HEADER_EXPIRES_MINUTES) {
        if (HTTP_HEADER_EXPIRES_MINUTES<0)
            HTTP_HEADER_EXPIRES_MINUTES=0;
        this.HTTP_HEADER_EXPIRES_MINUTES = HTTP_HEADER_EXPIRES_MINUTES;        
    }

    public int getHTTP_HEADER_EXPIRES_MINUTES() {        
        return HTTP_HEADER_EXPIRES_MINUTES;
    }
            
    public long getMaxTotalRamUsageBytes() {
        return MAX_TOTAL_RAMUSAGE_BYTES;
    }
    
    public void setMaxTotalRamUsageBytes(long MAX_PER_FILE_INRAMSIZE_BYTES){
        this.MAX_PER_FILE_INRAMSIZE_BYTES = MAX_PER_FILE_INRAMSIZE_BYTES;
    }

    public long getUsedRamSizeBytes() {
        return usedRamSizeBytes;
    }
    
    public long getMaxEntrySizeBytes() {
        return MAX_PER_FILE_INRAMSIZE_BYTES;
    }
    
    public void setMaxEntrySizeBytes(int MAX_PER_FILE_INRAMSIZE_BYTES){
        this.MAX_PER_FILE_INRAMSIZE_BYTES = MAX_PER_FILE_INRAMSIZE_BYTES;
    }       
    
    private ScheduledExecutorService startTimeStampUpdater(){
        //final HTTPstatus[] AllHTTPstatus = HTTPstatus.values();
        final DateFormat df = (DateFormat) dateformatcloner.clone();
        Runnable tupdater = new Runnable() {@Override
            public void run() {
                final long t = System.currentTimeMillis();
                StringArray currentTime = new StringArray(df.format(new Date(t)));
                final int expminutes = HTTP_HEADER_EXPIRES_MINUTES;
                StringArray expireTime  = new StringArray(df.format(new Date(t+expminutes*60000L)));
                byte[] cba= currentTime.ba;
                byte[] eba = expireTime.ba;
                byte[] expminbytes = String.valueOf(expminutes*60).getBytes();
                //for (HTTPstatus hs:AllHTTPstatus)
                  //  put(cba, hs.upr.dateOffset, hs.upr.dataAdr);
                for (HttpFileCacheEntry fr:filecache.values()){
                    fr.updateHeaders(expminbytes, cba, eba);
                }
                expireHTTPtimestamp = expireTime;
                currentHTTPtimestamp = currentTime;//volatile write so others see the updated bytes in the above code.
            }
        }; 
        ScheduledExecutorService se = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                Thread t = new Thread(r,"FIXEDRATETIMESTAMPUPDATER");
                t.setPriority(Thread.MAX_PRIORITY);
                t.setDaemon(true);
                return t;
            }
        });   
        tupdater.run();
        se.scheduleAtFixedRate(tupdater,0,1,TimeUnit.SECONDS);        
        return se;
    }
    
    private FileChangedListener createListener(){
        return new FileChangedListener() {
            final DateFormat df = (DateFormat) dateformatcloner.clone();
            @Override
            public void fileChanged(String filename,String mapname,ByteChannel rb,long size,long modifiedMilliSec, boolean deleted) {                                        
                final String ruri = mapname.replace('\\', '/');
                final String host = null;//"localhost"; //TODO:p1 fix host config
                final int hcode = ruri.hashCode();
                HttpRequestPacket r = new HttpRequestPacket() { @Override
                    public ProcessingState getProcessingState() {
                        throw new UnsupportedOperationException("Not supported yet.");
                    }
                    @Override
                    public boolean equals(Object obj) {
                        return ((HttpRequestPacket)obj).getRequestURIRef().
                                getRequestURIBC().equalsIgnoreCase(ruri)
                        && (host==null ||  //TODO:p3 perf: use buffer instead of String creation:
                            host.equals(((HttpRequestPacket)obj).getHeader("Host")));
                    }
                    @Override
                    public int hashCode() {
                        return hcode;
                    }
                };
                if (deleted){
                    HttpFileCacheEntry fr = filecache.remove(r);
                    if (fr!=null){
                        usedRamSizeBytes-=(fr.getRamUsage());
                        final FileCacheProbe[] probes = monitoringConfig.getProbesUnsafe();
                        if (probes != null) 
                            for (FileCacheProbe probe : probes) 
                                probe.onEntryRemovedEvent(fr);
                    }
                    return;
                }
                System.err.println("CACHED:'"+ruri+"'");
                try { 
                    final String contentType = getFileContentType(filename);
                    if (contentType == null)
                        throw new IOException("Unknown file extension for file:"+filename);
                    String modtime = df.format(new Date(modifiedMilliSec-900));//-900 used for Date: field that is updated once per second by another thread cant be larger then Last-Modified:
                    HttpFileCacheEntry fr = new HttpFileCacheEntry(filename,HttpFileCache.this,size,rb,contentType,modtime);
                    HttpFileCacheEntry frold = filecache.put(r,fr);
                    final boolean added = frold == null;          
                    usedRamSizeBytes+=fr.getRamUsage()-(added?0:frold.getRamUsage());
                    if (usedRamSizeBytes>MAX_TOTAL_RAMUSAGE_BYTES){
                        usedRamSizeBytes-=fr.getRamUsage();
                        filecache.remove(r);
                        throw new IOException("Httpfilecache limit reached: not caching: "+ruri);
                    }
                    final FileCacheProbe[] probes = monitoringConfig.getProbesUnsafe();
                    if (probes != null) {
                        for (FileCacheProbe probe : probes) {
                            if (added)
                                probe.onEntryAddedEvent(fr);
                            else
                                probe.onEntryUpdatedEvent(fr);                            
                        }
                    }
                } catch (Throwable ex) {
                    notifyProbesError(ex);
                }
            }
        };
    }  

    /*
     * Async cache add in Java 1.7 (There will be a delay before the file is available).
     * Sync cache add  in 1.6. can only be files not directory in 1.6.
     * TODO: add host mapping param.
     */
    public void add(File file, String customMapNameIfFile, 
            String prefixMapingIfDir,boolean followSymlinks)  {        
        try {
            if (sexs==null)
                throw new IOException("HttpFileCache is not Enabled.");                        
            if (prefixMapingIfDir==null)
                prefixMapingIfDir="";
            fileLoader.loadFile(file,customMapNameIfFile,prefixMapingIfDir,followSymlinks);
        } catch (IOException ex) {
            notifyProbesError(ex);            
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Only works on mapped single files or root directories.
     * @param file
     * @return 
     */
    public void remove(File file,String optionalmapedname, String host){
        fileLoader.remove(file,optionalmapedname,host);
    }
    
    public Buffer get(final HttpRequestPacket req) {          
        HttpFileCacheEntry fr = filecache.get(req);//TODO: perf: remove the two buffer wraps.            
        if (fr != null){//TODO: if deflate header is with ;q=0 we must return null.
            Buffer buf = req.getRequestURIRef().getRequestURIBC().getBufferChunk().getBuffer();
            if (buf.indexOf(deflateHeader, 10)>0){//only supporting deflate requests 
                notifyProbesEntryHit(fr);
                Object b=currentHTTPtimestamp;//volatile read to ensure buffer timestamp bytes are updated.
                ByteBuffer bb =fr.getResponse(buf);
                System.err.println(bb);
                return new DirectBufferWraper(bb); //fr==null?HTTPstatus.NotFound.upr.data :  
            }
        }
        notifyProbesEntryMissed(req);
        return null;
    }
    
    public boolean isEnabled() {
        return sexs != null;
    }
    
    public synchronized final void setEnabled(boolean toBoolean) {
        try {
            fileLoader.setEnabled(toBoolean);
            if (toBoolean){
                if (sexs == null){
                    sexs = startTimeStampUpdater();
                }                
            }else{
                usedRamSizeBytes = 0;
                filecache.clear();                
                if (sexs!=null){
                    sexs.shutdownNow();
                    sexs=null;
                }                    
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public JmxMonitoringConfig<FileCacheProbe> getMonitoringConfig() {
        return monitoringConfig;
    }

    private void notifyProbesEntryHit(HttpFileCacheEntry entry) {
        final FileCacheProbe[] probes = monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onEntryHitEvent(entry);
            }
        }
    }

    private void notifyProbesEntryMissed(final HttpRequestPacket req) {        
        final FileCacheProbe[] probes = monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onEntryMissedEvent(req);
            }
        }
    }

    protected void notifyProbesError(final Throwable error) {
        final FileCacheProbe[] probes = monitoringConfig.getProbesUnsafe();
        if (probes != null) {
            for (FileCacheProbe probe : probes) {
                probe.onErrorEvent(this, error);
            }
        }
    }
    
    /*public enum HTTPstatus {
        BadRequest("400 Bad Request"),
        NotFound("404 Not Found"),        
        RequestTimeout("408 Request Timeout"), //TODO:p1 implement request timeout
        RequestEntityTooLarge("413 Request Entity Too Large"),
        RequestURItooLong("414 Request-URI Too Long") //TODO:p1 implement request uri toolong
        ;                        
        HTTPstatus(String value){
            upr = new UpdateAbleResponse(value,"Connection: ","close");//TODO:p1 close connection after first write
        }                
        private final UpdateAbleResponse upr;              
        //public final Handshaker.HandShakeException ex = new Handshaker.HandShakeException(this);        
        public final ByteBuffer getData(){
            Object a=currentHTTPtimestamp;//volatile read to ensure buffer timestamp bytes etc are updated.        
            return upr.data;
        }                
    }*/        
}
