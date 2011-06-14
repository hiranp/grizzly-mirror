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

import java.nio.file.StandardOpenOption;
import java.io.File;
import java.nio.file.LinkOption;
import java.nio.file.WatchService;
import org.glassfish.grizzly.Grizzly;
import java.nio.file.FileVisitor;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.logging.Logger;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.zip.Deflater;
import static java.nio.file.LinkOption.*;
import static java.nio.file.StandardWatchEventKinds.*;
import java.security.MessageDigest;
import java.util.zip.DeflaterOutputStream;
import org.glassfish.grizzly.http.server.filecache.HttpFileCacheEntry.MyByteArrayOutputStream;

/** 
 * TODO:p1 QA all functionality.
 * 
 * @author gustav trede
 */
public class FileWatcher implements HttpFileCacheLoader,Runnable{
    
    private static final Logger logger = Grizzly.logger(FileWatcher.class);
    private static final AtomicLong instanceCounter = new AtomicLong();
        
    private WatchService watcher;                
    private Thread worker;
    private volatile boolean stayAlive;    

    private final Map<WatchKey, RootDir> allKeys = new ConcurrentHashMap(64,(float)0.75,1);
    private final ConcurrentHashMap<RootDir,RootDir> allrootdirs = 
            new ConcurrentHashMap<>(64,(float)0.75,1);
    private FileChangedListener fcli;
    private final Deflater deflater=new Deflater(Deflater.DEFAULT_COMPRESSION);
    
    FileWatcher(){
        instanceCounter.incrementAndGet();
    }    

    @Override
    public void run() {
        this.worker = Thread.currentThread();
        try {
            while(stayAlive){
                for (RootDir rd:allrootdirs.keySet()){
                    if (rd.watcherKeys.isEmpty()){
                        rd.registerRecursive(rd.root);
                    }                    
                }
                try{
                    WatchKey key;
                    while((key=watcher.take())!=null) {
                        if (allKeys.get(key).handleKey(key))
                            break;
                    }
                }catch(InterruptedException ie){ }//check for new rootdirs
            }
        }catch (ClosedWatchServiceException e){ }
        catch (Throwable ex) {
            logger.log(Level.WARNING,this+" FAILED", ex);            
        }finally{
            shutdown();
        }        
    }
    /**
     * 
     * @param file is not closed by this method.
     * @param customMapNameIfFile
     * @param prefixMapingIfDir
     * @param followSymlinks
     * @throws IOException 
     */
    public void loadFile(String host,File file, String customMapNameIfFile, 
            String prefixMapingIfDir,boolean followSymlinks) throws IOException {               
        RootDir r = new RootDir(host,file.toPath(),fcli,customMapNameIfFile,prefixMapingIfDir,followSymlinks);
        if (allrootdirs.putIfAbsent(  //FileSystems.getDefault().getPath(fileOrRootDir)
                r,r)!=null){
            throw new IOException("File is already cached: "+file);
        }        
        worker.interrupt();
    }

    @Override
    public void remove(File file, String optionalmapedname, String host) {
        RootDir r=allrootdirs.remove(new RootDir(host,file.toPath(),null, optionalmapedname!=null?optionalmapedname:"/"+file.toPath().toString(),"",false));
        if (r!=null){
            for (WatchKey wk: r.watcherKeys.keySet()){
                wk.cancel();                
            }
        }
    }
    
    @Override
    public void setFileChangedListener(FileChangedListener fcli) {
        this.fcli = fcli;
    }
    
    @Override
    public void setEnabled(boolean setEnabled) throws IOException {
        if(setEnabled)
            start();
         else            
            shutdown();
    }
    
    private void start() throws IOException{
        if (worker == null){
            watcher = FileSystems.getDefault().newWatchService();
            Thread t = new Thread(this,"FileWatcher-"+instanceCounter.get());
            t.setDaemon(true);            
            worker = t;
            stayAlive = true;
            t.start();        
        }
    }
    
    private void shutdown(){
        if (worker != null){
            stayAlive = false;
            try {
                if (watcher !=null)
                    watcher.close();
            } catch (IOException ex) { }
            finally{
                watcher = null;
                if (worker != null)
                    try {                
                    worker.interrupt();    
                    worker.join();
                    worker = null;
                } catch (InterruptedException ex) { }
                allrootdirs.clear();
                allKeys.clear();
            }
        }
    }
    
    @Override
    public String toString() {
        return "FileWatcher: "+allKeys;
    }

    @Override
    public void loadFile(MessageDigest MD5_,ByteChannel bs,byte[] data,MyByteArrayOutputStream bout) throws IOException {
        deflater.reset();
        final boolean compress = bout !=null;
        DeflaterOutputStream zout = compress?new DeflaterOutputStream(bout, deflater,true):null;
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
            zout.flush();
        }        
    }
    
    private class RootDir{
        final FileChangedListener fcli;
        final Path root;    
        final Map<WatchKey, Path> watcherKeys = new ConcurrentHashMap<>(1,0.75f,1);
        final String prefix;
        final String customMapName;
        final LinkOption linkfollow;
        final String host;
        final int hashcode;        

        public RootDir(String host,Path root, FileChangedListener fcli,String customMapName,String prefix,boolean followsymlinks) {
            this.host = host;
            this.root = root;
            this.fcli = fcli;
            this.prefix = prefix;
            this.customMapName = customMapName;
            this.linkfollow = followsymlinks?null:NOFOLLOW_LINKS;
            this.hashcode = root.hashCode();            
        }

        @Override
        public boolean equals(Object obj) {
            return ((RootDir)obj).root.equals(root);
                  //&& ((RootDir)obj).customMapName.equals(customMapName);
        }

        @Override
        public int hashCode() {
            return hashcode;
        }        
        
        private void registerRecursive(final Path start) throws IOException { 
            Files.walkFileTree(start, new FileVisitor<Path>() {
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException ie) {                
                    return FileVisitResult.CONTINUE;
                }                        
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes atr) throws IOException {
                    try(SeekableByteChannel sb = Files.newByteChannel(file, StandardOpenOption.READ)){
                    fcli.fileChanged(host,file.toString(),prefix+root.relativize(file).toString(),sb,sb.size(),atr.lastModifiedTime().toMillis(), false);
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException ie) {
                    logger.log(Level.WARNING,"visitFileFailed", ie);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {  
                    WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
                    watcherKeys.put(key, dir);
                    allKeys.put(key, RootDir.this);
                    return FileVisitResult.CONTINUE;
                }
            });
        }        

        private void pollEvents(final WatchKey key){
            final Path dir = watcherKeys.get(key);
            if (dir == null) {
                logger.log(Level.WARNING, "Unregistered WatchKey at {0}", this);
                return;
            }        
            for (WatchEvent event: key.pollEvents()) {            
                if (event.kind() == OVERFLOW) {//TODO fix overflow, event discarded?      
                    continue; 
                }            
                final WatchEvent.Kind<Path> type = (Kind<Path>) event.kind();            
                Path child = dir.resolve(((WatchEvent<Path>) event).context());
                final Path relroot = root.relativize(child);
                try{ 
                   BasicFileAttributes atr=  Files.readAttributes(child, BasicFileAttributes.class,linkfollow);
                    final boolean isdir = atr.isDirectory();
                    if (child.getFileName().toString().startsWith(".")){
                        continue;
                    }
                    if (type == ENTRY_CREATE) {
                        if (isdir){
                            registerRecursive(child);                    
                            continue;
                        }
                    }
                    if (type == ENTRY_MODIFY) {            
                        if (isdir){                        
                            //TODO:p1 implement filewatcher dir modified
                            continue;
                        }
                    }
                    if (type == ENTRY_DELETE) {            
                        if (isdir){                        
                            //TODO:p1 implement filewatcher dir deleted
                            continue;
                        }                    
                    }
                    SeekableByteChannel sb = null;
                    try{
                    if (type!=ENTRY_DELETE)
                         sb = Files.newByteChannel(child, StandardOpenOption.READ);
                    fcli.fileChanged(host,child.toString(),prefix+(customMapName!=null?customMapName:relroot.toString()),sb,sb!=null?sb.size():0,atr.lastModifiedTime().toMillis(),type==ENTRY_DELETE);
                    }finally{
                        if (sb != null)
                            sb.close();
                    }
                } catch (IOException x) {
                    logger.log(Level.WARNING,"filewatcher failed handling :"+child , x);
                }                
            }        
        }
        boolean handleKey(WatchKey key){
            pollEvents(key);
            if (!key.reset()) { //TODO:p1 remove rootdir from datastructure if its a file thatdoes not exist anymore etc
                allKeys.remove(key);
                watcherKeys.remove(key);
                return watcherKeys.isEmpty();
            }        
            return false;
        }            
    }
}
