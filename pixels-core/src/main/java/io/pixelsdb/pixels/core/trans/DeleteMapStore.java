package io.pixelsdb.pixels.core.trans;

import java.util.HashMap;
import java.util.Map;

/**
 * Created at: 17.09.20, for Issue #82.
 * Author: bian
 */
public class DeleteMapStore
{
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
        this.bitMaps.put(path, new DeleteBitMap(rowNum));
        this.tsMaps.put(path, new DeleteTsMap());
    }

    public DeleteBitMap getDeleteBitMap (String path)
    {
        return this.bitMaps.getOrDefault(path, null);
    }

    public DeleteTsMap getDeleteTSMap (String path)
    {
        return this.tsMaps.getOrDefault(path, null);
    }
}
