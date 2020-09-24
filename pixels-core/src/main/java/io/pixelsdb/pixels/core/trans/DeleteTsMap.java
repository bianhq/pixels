package io.pixelsdb.pixels.core.trans;

import net.openhft.collections.HugeConfig;
import net.openhft.collections.HugeHashMap;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created at: 20.09.20, for Issue #82.
 * Author: bian
 */
public class DeleteTsMap
{
    private ReentrantLock lock = null;
    private Map<Integer, Long> rowIdToTimestampMap = null;

    public DeleteTsMap()
    {
        HugeConfig config = HugeConfig.DEFAULT.clone()
                .setSegments(128)
                .setSmallEntrySize(128)
                .setCapacity(128);

        final HugeHashMap<Integer, Long> map =
                new HugeHashMap<Integer, Long>(config, Integer.class, Long.class);
        rowIdToTimestampMap = new HashMap<>();
        lock = new ReentrantLock();
    }

    public void setDeleteTimestamp (int rowId, long timestamp)
    {
        this.lock.lock();
        this.rowIdToTimestampMap.put(rowId, timestamp);
        this.lock.unlock();
    }

    public boolean isVisible (int rowId, long timestamp)
    {
        this.lock.lock();
        boolean visible = this.rowIdToTimestampMap.getOrDefault(rowId, 0L) > timestamp;
        this.lock.unlock();
        return visible;
    }

    public Map<Integer, Long> dumpAndRemove (long timestamp)
    {
        this.lock.lock();
        Map<Integer, Long> dumped = new HashMap<>();
        for (int rowId : this.rowIdToTimestampMap.keySet())
        {
            dumped.put(rowId, this.rowIdToTimestampMap.get(rowId));
            this.rowIdToTimestampMap.remove(rowId);
        }
        this.lock.unlock();
        return dumped;
    }
}
