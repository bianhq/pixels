package io.pixelsdb.pixels.core.trans;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created at: 17.09.20, for Issue #82.
 * Author: bian
 */
public class DeleteMapStore
{
    private ReentrantLock lock = new ReentrantLock();
    private Map<String, DeleteBitMap> bitMaps = new HashMap<>();
    private Map<String, DeleteTsMap> tsMaps = new HashMap<>();

    private DeleteMapStore () {}

    private static DeleteMapStore instance = null;

    public static DeleteMapStore Instance ()
    {
        if (instance == null)
        {
            instance = new DeleteMapStore();
        }
        return instance;
    }

    public void createDeleteMapForFile (String path, int rowNum)
    {
        this.lock.lock();
        this.bitMaps.put(path, new DeleteBitMap(rowNum));
        this.tsMaps.put(path, new DeleteTsMap());
        this.lock.unlock();
    }

    public DeleteBitMap getDeleteBitMap (String path)
    {
        this.lock.lock();
        DeleteBitMap bitMap = this.bitMaps.getOrDefault(path, null);
        this.lock.unlock();
        return bitMap;
    }

    public DeleteTsMap getDeleteTSMap (String path)
    {
        this.lock.lock();
        DeleteTsMap tsMap = this.tsMaps.getOrDefault(path, null);
        this.lock.unlock();
        return tsMap;
    }

    public Set<Integer> getInvisibleRowIds (String path, long timestamp)
    {
        Set<Integer> invisibleRowIds = new HashSet<>();
        this.lock.lock();
        DeleteBitMap deleteBitMap = this.bitMaps.getOrDefault(path, null);
        DeleteTsMap deleteTsMap = this.tsMaps.getOrDefault(path, null);
        if (deleteBitMap != null && deleteTsMap != null)
        {
            Set<Integer> intentRowIds = new HashSet<>();
            deleteBitMap.getBatchRowIds(intentRowIds, intentRowIds);
            for (int intendRowId : intentRowIds)
            {
                if (deleteTsMap.isVisible(intendRowId, timestamp) == false)
                {
                    invisibleRowIds.add(intendRowId);
                }
            }
        }
        this.lock.unlock();
        return invisibleRowIds;
    }
}
