package io.pixelsdb.pixels.core.reader;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Created at: 2/16/23
 * Author: hank
 */
public class ColumnChunkCache
{
    public static final ColumnChunkCache Instance = new ColumnChunkCache();

    public static class CacheKey implements Comparable<CacheKey>
    {
        private String path;
        private int rowGroupId;
        private int columnId;

        public CacheKey(String path, int rowGroupId, int columnId)
        {
            this.path = path;
            this.rowGroupId = rowGroupId;
            this.columnId = columnId;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return path.equals(cacheKey.path) && rowGroupId == cacheKey.rowGroupId && columnId == cacheKey.columnId;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(path, rowGroupId, columnId);
        }

        @Override
        public int compareTo(CacheKey o)
        {
            int a = path.compareTo(o.path);
            if (a != 0)
            {
                return a;
            }
            if (rowGroupId != o.rowGroupId)
            {
                return Integer.compare(rowGroupId, o.rowGroupId);
            }
            return Integer.compare(columnId, o.columnId);
        }
    }

    private Map<CacheKey, ByteBuffer> cache = new HashMap<>(1024);
    private ColumnChunkCache() {}

    public synchronized void put(CacheKey key, ByteBuffer buffer)
    {
        this.cache.put(key, buffer);
    }

    public synchronized ByteBuffer get(CacheKey key)
    {
        return this.cache.get(key);
    }
}
