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
package io.pixelsdb.pixels.cache;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created at: 2/16/23
 * Author: hank
 */
public class OnHeapChunkCache
{
    public static final OnHeapChunkCache Instance = new OnHeapChunkCache();

    public static class ChunkKey implements Comparable<ChunkKey>
    {
        private final String path;
        private final int rowGroupId;
        private final int columnId;

        public ChunkKey(String path, int rowGroupId, int columnId)
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
            ChunkKey chunkKey = (ChunkKey) o;
            return path.equals(chunkKey.path) && rowGroupId == chunkKey.rowGroupId && columnId == chunkKey.columnId;
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(path, rowGroupId, columnId);
        }

        @Override
        public int compareTo(ChunkKey o)
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

    private final Map<ChunkKey, ByteBuffer> chunkCache = new ConcurrentHashMap<>(1024);

    private OnHeapChunkCache() {}

    public void put(ChunkKey key, ByteBuffer buffer)
    {
        this.chunkCache.put(key, buffer);
    }

    public ByteBuffer get(ChunkKey key)
    {
        return this.chunkCache.get(key);
    }
}
