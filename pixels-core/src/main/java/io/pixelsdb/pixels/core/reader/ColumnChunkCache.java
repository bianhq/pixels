/*
 * Copyright 2013 PixelsDB.
 *
 * This file is part of Pixels.
 *
 * Pixels is free software: you can redistribute it and/or modify
 * it under the terms of the Affero GNU General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Pixels is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public
 * License along with Pixels.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
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
