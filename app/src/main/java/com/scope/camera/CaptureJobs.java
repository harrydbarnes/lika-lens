package com.scope.camera;

import java.util.LinkedHashMap;
import java.util.Map;

/** The shutter and the development queue are independent lifecycles. */
final class CaptureJobs {
    enum Phase { DEVELOPING, ARCHIVING, PUBLISHING, SAVED, FAILED }
    private final Map<Long, Phase> active = new LinkedHashMap<>();
    private long nextId;
    private final Map<Long, Phase> completed=new LinkedHashMap<>();
    synchronized long begin() { long id = ++nextId; active.put(id, Phase.DEVELOPING); return id; }
    synchronized void move(long id, Phase phase) { if (active.containsKey(id)) active.put(id, phase); }
    synchronized void finish(long id, boolean success) {
        if(active.remove(id)==null) return;
        completed.put(id,success?Phase.SAVED:Phase.FAILED);
        if(completed.size()>20) completed.remove(completed.keySet().iterator().next());
    }
    synchronized Phase phase(long id) { return active.containsKey(id)?active.get(id):completed.get(id); }
    synchronized int size() { return active.size(); }
}
