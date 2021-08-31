/*
 * Copyright 2019 PixelsDB.
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
package io.pixelsdb.pixels.test;

import io.pixelsdb.pixels.cache.MemoryMappedFile;
import io.pixelsdb.pixels.cache.PixelsCacheIdx;
import io.pixelsdb.pixels.cache.PixelsCacheReader;
import io.pixelsdb.pixels.common.exception.MetadataException;
import io.pixelsdb.pixels.common.metadata.MetadataService;
import io.pixelsdb.pixels.common.metadata.domain.Layout;
import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.common.utils.ConfigFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * pixels
 * java -jar xxx.jar hostname metahost layout_version thread_num
 * java -jar pixels-tools-0.1.0-SNAPSHOT-full.jar dbiir24 dbiir27 3 1
 *
 * @author guodong
 */
public class CacheReadPerf
{
    private static ConfigFactory config = ConfigFactory.Instance();
    private static List<String> cachedColumnlets;
    private static List<String> cachedPaths = new ArrayList<>();

    public CacheReadPerf()
    {
    }

    // args: hostname layout_version log_path
    public static void main(String[] args)
    {
        try
        {
            long prepareStart = System.currentTimeMillis();
            CacheReadPerf checkCacheConcurrentReader = new CacheReadPerf();
            checkCacheConcurrentReader.prepare(args[0], args[1], Integer.parseInt(args[2]));
            int threadNum = Integer.parseInt(args[3]);

            CacheReader[] readers = new CacheReader[threadNum];
            Thread[] threads = new Thread[threadNum];
            int readCount = cachedColumnlets.size() * cachedPaths.size();

            MemoryMappedFile indexFile = new MemoryMappedFile(config.getProperty("index.location"),
                    Long.parseLong(config.getProperty("index.size")));
            MemoryMappedFile cacheFile = new MemoryMappedFile(config.getProperty("cache.location"),
                    Long.parseLong(config.getProperty("cache.size")));
            PixelsCacheReader cacheReader = PixelsCacheReader
                    .newBuilder()
                    .setIndexFile(indexFile)
                    .setCacheFile(cacheFile)
                    .build();
            PixelsCacheIdx[] cacheIdxes = new PixelsCacheIdx[readCount];
            int idx = 0;
            for (String path : cachedPaths)
            {
                for (int i = 0; i < cachedColumnlets.size(); i++)
                {
                    String[] columnletIdSplits = cachedColumnlets.get(i).split(":");
                    PixelsCacheIdx pixelsCacheIdx = cacheReader
                            .search(-1, Short.parseShort(columnletIdSplits[0]),
                                    Short.parseShort(columnletIdSplits[1]));
                    cacheIdxes[idx] = pixelsCacheIdx;
                    idx++;
                }
            }

            for (int i = 0; i < threadNum; i++)
            {
                Random random = new Random(System.nanoTime());
                long[] offsets = new long[readCount];
                int[] lengths = new int[readCount];
                for (int k = 0; k < readCount; k++)
                {
                    int id = random.nextInt(readCount);
                    offsets[k] = cacheIdxes[id].offset;
                    lengths[k] = cacheIdxes[id].length;
                }
                CacheReader reader = new CacheReader(cacheFile, offsets, lengths);
                readers[i] = reader;
            }
            long prepareEnd = System.currentTimeMillis();
            System.out.println("[prepare]: " + (prepareEnd - prepareStart) + "ms");

            long readStart = System.currentTimeMillis();
            for (int i = 0; i < threadNum; i++)
            {
                Thread t = new Thread(readers[i]);
                threads[i] = t;
                t.start();
            }
            for (Thread t : threads)
            {
                t.join();
            }
            long readEnd = System.currentTimeMillis();
            System.out.println("[read]: " + (readEnd - readStart) + "ms");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    // prepare correct answers
    private void prepare(String hostName, String metaHost, int layoutVersion)
            throws MetadataException, IOException
    {
        MetadataService metadataService = new MetadataService(metaHost, 18888);
        Layout layout = metadataService.getLayout("pixels", "test_1187", layoutVersion);
        cachedColumnlets =
                layout.getCompactObject().getColumnletOrder().subList(0, layout.getCompactObject().getCacheBorder());
        Storage storage = StorageFactory.Instance().getStorage("hdfs");
        List<String> paths = storage.listPaths(layout.getCompactPath());
        for (String path : paths)
        {
            if (storage.getHosts(path)[0].equalsIgnoreCase(hostName))
            {
                cachedPaths.add(path);
            }
        }
    }

    static class CacheReader implements Runnable
    {
        private final long[] offsets;
        private final int[] lengths;
        private final MemoryMappedFile cacheFile;

        public CacheReader(MemoryMappedFile cacheFile,
                           long[] offsets, int[] lengths)
        {
            this.cacheFile = cacheFile;
            this.offsets = offsets;
            this.lengths = lengths;
        }

        @Override
        public void run()
        {
            long readStart = System.nanoTime();
            long readSize = 0;
            for (int i = 0; i < offsets.length; i++)
            {
                byte[] content = new byte[lengths[i]];
                cacheFile.getBytes(offsets[i], content, 0, lengths[i]);
                readSize += content.length;
            }
            long readEnd = System.nanoTime();
            System.out.println(
                    "[read]: " + readSize + "," + (readEnd - readStart) + "," + (readSize * 1.0 / (readEnd - readStart)));
        }
    }
}

