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
            deleteBitMap.getBatchRowIds(invisibleRowIds, intentRowIds);
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

    /**
     * This method is only used for test purpose before index update is implemented.
     * @return
     */
    public RowIdentifier generateRowIdentifier (long tableRowId)
    {
        /*
        int numFiles = this.bitMaps.keySet().size();
        Random random = new Random(System.nanoTime());
        int fileId = random.nextInt(numFiles);
        List<String> filePaths = new ArrayList<>(this.bitMaps.keySet());
        String path = filePaths.get(fileId);
        int rowNum = this.bitMaps.get(path).getRowNum();
        int rowId = random.nextInt(rowNum);
        double existsRate = random.nextDouble();
        */
        String path = "/home/hank/data/realtime/" + (tableRowId / (10*1000*1000)) + ".pxl";
        int rowId = (int) (tableRowId % (10*1000*1000));
        boolean exists = false;
        if (tableRowId < (100*1000*1000))
        {
            exists = true;
        }
        return new RowIdentifier(path, rowId, exists);
    }
}
