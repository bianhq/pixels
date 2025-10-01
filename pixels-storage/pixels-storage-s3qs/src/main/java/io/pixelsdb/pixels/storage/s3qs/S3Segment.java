/*
 * Copyright 2025 PixelsDB.
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
package io.pixelsdb.pixels.storage.s3qs;

/**
 * @author hank
 * @create 2025-10-01
 */
public class S3Segment
{
    private final String path;
    private final int size;
    private final int id;
    private final int partition;
    private final boolean eof;

    public S3Segment(String path, int size, int id, int partition, boolean eof)
    {
        this.path = path;
        this.size = size;
        this.id = id;
        this.partition = partition;
        this.eof = eof;
    }

    public String getPath()
    {
        return path;
    }

    public int getSize()
    {
        return size;
    }

    public int getId()
    {
        return id;
    }

    public int getPartition()
    {
        return partition;
    }

    public boolean isEof()
    {
        return eof;
    }
}
