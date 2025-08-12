/*
 * Copyright 2022 PixelsDB.
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
package io.pixelsdb.pixels.cache.legacy;

import io.pixelsdb.pixels.cache.PixelsCacheIdx;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface CacheContentReader
{
    // caller should guarantee that buf.length is enough to hold the CacheIndex
    void read(PixelsCacheIdx idx, byte[] buf) throws IOException;

    ByteBuffer readZeroCopy(PixelsCacheIdx idx) throws IOException;

    default void read(PixelsCacheIdx idx, ByteBuffer buf) throws IOException
    {
        read(idx, buf.array());
    }

    // caller should guarantee that buf.length is enough to hold all CacheIndexes
    void batchRead(PixelsCacheIdx[] idxs, ByteBuffer buf) throws IOException;
}
