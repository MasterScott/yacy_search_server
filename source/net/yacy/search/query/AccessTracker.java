/**
 *  AccessTracker
 *  an interface for Adaptive Replacement Caches
 *  Copyright 2009 by Michael Peter Christen, mc@yacy.net, Frankfurt a. M., Germany
 *  First released 29.08.2009 at http://yacy.net
 *
 * $LastChangedDate$
 * $LastChangedRevision$
 * $LastChangedBy$
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.search.query;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import net.yacy.cora.date.GenericFormatter;
import net.yacy.cora.document.UTF8;
import net.yacy.cora.document.WordCache;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.MemoryControl;

public class AccessTracker {

    private final static long DUMP_PERIOD = 60000L;

    public static final int minSize = 100;
    public static final int maxSize = 1000;
    public static final int maxAge = 24 * 60 * 60 * 1000;

    public enum Location {local, remote}

    private static final LinkedList<QueryParams> localSearches = new LinkedList<QueryParams>();
    private static final LinkedList<QueryParams> remoteSearches = new LinkedList<QueryParams>();
    private static final ArrayList<String> log = new ArrayList<String>();
    private static long lastLogDump = System.currentTimeMillis();
    private static File dumpFile = null;

    public static void setDumpFile(File f) {
        dumpFile = f;
    }

    public static void add(final Location location, final QueryParams query) {
        if (location == Location.local) synchronized (localSearches) {add(localSearches, query);}
        if (location == Location.remote) synchronized (remoteSearches) {add(remoteSearches, query);}
    }

    private static void add(final LinkedList<QueryParams> list, final QueryParams query) {
        // learn that this word can be a word completion for the DidYouMeanLibrary
        if (query.resultcount > 10 && query.queryString != null && query.queryString.length() > 0) {
            final StringBuilder sb = new StringBuilder(query.queryString);
            sb.append(query.queryString);
            WordCache.learn(sb);
        }

        // add query to statistics list
        list.add(query);

        // shrink dump list but keep essentials in dump
        while (list.size() > maxSize || (!list.isEmpty() && MemoryControl.shortStatus())) {
            synchronized (list) {
                if (!list.isEmpty()) addToDump(list.removeFirst()); else break;
            }
        }

        // if the list is small we can terminate
        if (list.size() <= minSize) return;

        // if the list is large we look for too old entries
        final long timeout = System.currentTimeMillis() - maxAge;
        while (!list.isEmpty()) {
            final QueryParams q = list.getFirst();
            if (q.starttime > timeout) break;
            addToDump(list.removeFirst());
        }
    }

    public static Iterator<QueryParams> get(final Location location) {
        if (location == Location.local) return localSearches.descendingIterator();
        if (location == Location.remote) return remoteSearches.descendingIterator();
        return null;
    }

    public static int size(final Location location) {
        if (location == Location.local) synchronized (localSearches) {return localSearches.size();}
        if (location == Location.remote) synchronized (remoteSearches) {return remoteSearches.size();}
        return 0;
    }

    private static void addToDump(final QueryParams query) {
        if (query.queryString == null || query.queryString.isEmpty()) return;
        addToDump(query.queryString, Integer.toString(query.resultcount), new Date(query.starttime));
    }

    public static void addToDump(String querystring, String resultcount) {
        addToDump(querystring, resultcount, new Date());
        if (lastLogDump + DUMP_PERIOD < System.currentTimeMillis()) {
            lastLogDump = System.currentTimeMillis();
            dumpLog();
        }
    }

    private static void addToDump(String querystring, String resultcount, Date d) {
        //if (query.resultcount == 0) return;
        if (querystring == null || querystring.isEmpty()) return;
        final StringBuilder sb = new StringBuilder(40);
        sb.append(GenericFormatter.SHORT_SECOND_FORMATTER.format(d));
        sb.append(' ');
        sb.append(resultcount);
        sb.append(' ');
        sb.append(querystring);
        synchronized (log) {
            log.add(sb.toString());
        }
    }

    public static void dumpLog() {
        while (!localSearches.isEmpty()) {
            addToDump(localSearches.removeFirst());
        }
        Thread t = new Thread() {
            @Override
            public void run() {
                ArrayList<String> logCopy = new ArrayList<String>();
                synchronized (log) {
                    logCopy.addAll(log);
                    log.clear();
                }
                RandomAccessFile raf = null;
                try {
                    raf = new RandomAccessFile(dumpFile, "rw");
                    raf.seek(raf.length());
                    for (final String s: logCopy) {
                        raf.write(UTF8.getBytes(s));
                        raf.writeByte(10);
                    }
                    logCopy.clear();
                } catch (final FileNotFoundException e) {
                    Log.logException(e);
                } catch (final IOException e) {
                    Log.logException(e);
                } finally {
                    if (raf != null) try {raf.close();} catch (IOException e) {}
                }
            }
        };
        t.start();
    }
}
