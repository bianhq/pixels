/*
 * Copyright 2018-2019 PixelsDB.
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
package io.pixelsdb.pixels.cli.load;

import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import io.pixelsdb.pixels.common.utils.DateUtil;
import io.pixelsdb.pixels.core.PixelsWriter;
import io.pixelsdb.pixels.core.PixelsWriterImpl;
import io.pixelsdb.pixels.core.TypeDescription;
import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author: tao
 * @author hank
 * @create in 2018-10-30 15:18
 **/
public class PixelsConsumer extends Consumer
{
    public static final AtomicInteger GlobalTargetPathId = new AtomicInteger(0);
    private final BlockingQueue<String> queue;
    private final Parameters parameters;
    private final int consumerId;

    public PixelsConsumer(BlockingQueue<String> queue, Parameters parameters, int consumerId)
    {
        this.queue = queue;
        this.parameters = parameters;
        this.consumerId = consumerId;
    }

    @Override
    public void run()
    {
        System.out.println("Start PixelsConsumer, " + currentThread().getName() +
                ", time: " + DateUtil.formatTime(new Date()));
        int count = 0;

        boolean isRunning = true;
        try
        {
            String pixelsPath = parameters.getLoadingPath();
            String schemaStr = parameters.getSchema();
            int[] orderMapping = parameters.getOrderMapping();
            int maxRowNum = parameters.getMaxRowNum();
            String regex = parameters.getRegex();
            boolean enableEncoding = parameters.isEnableEncoding();
            if (regex.equals("\\s"))
            {
                regex = " ";
            }

            ConfigFactory configFactory = ConfigFactory.Instance();
            int pixelStride = Integer.parseInt(configFactory.getProperty("pixel.stride"));
            int rowGroupSize = Integer.parseInt(configFactory.getProperty("row.group.size"));
            long blockSize = Long.parseLong(configFactory.getProperty("block.size"));
            short replication = Short.parseShort(configFactory.getProperty("block.replication"));

            TypeDescription schema = TypeDescription.fromString(schemaStr);
            final String[] targetPaths = pixelsPath.split(";");
            VectorizedRowBatch rowBatch = schema.createRowBatch();
            ColumnVector[] columnVectors = rowBatch.cols;

            BufferedReader reader;
            String line;

            boolean initPixelsFile = true;
            String targetFilePath;
            PixelsWriter pixelsWriter = null;
            int rowCounter = 0;

            while (isRunning)
            {
                String originalFilePath = queue.poll(2, TimeUnit.SECONDS);
                if (originalFilePath != null)
                {
                    count++;
                    Storage originStorage = StorageFactory.Instance().getStorage(originalFilePath);
                    reader = new BufferedReader(new InputStreamReader(originStorage.open(originalFilePath)));

                    // choose the target output directory using round-robin
                    int targetPathId = GlobalTargetPathId.getAndIncrement() % targetPaths.length;
                    String targetDirPath = targetPaths[targetPathId];
                    Storage targetStorage = StorageFactory.Instance().getStorage(targetDirPath);

                    System.out.println("loading data into directory: " + targetDirPath);

                    while ((line = reader.readLine()) != null)
                    {
                        if (initPixelsFile)
                        {
                            if(line.length() == 0)
                            {
                                System.err.println("thread: " + currentThread().getName() + " got empty line.");
                                continue;
                            }
                            // we create a new pixels file if we can read a next line from the source file.

                            if (!targetDirPath.endsWith("/"))
                            {
                                targetDirPath += "/";
                            }
                            targetFilePath = targetDirPath + DateUtil.getCurTime() + ".pxl";

                            pixelsWriter = PixelsWriterImpl.newBuilder()
                                    .setSchema(schema)
                                    .setPixelStride(pixelStride)
                                    .setRowGroupSize(rowGroupSize)
                                    .setStorage(targetStorage)
                                    .setPath(targetFilePath)
                                    .setBlockSize(blockSize)
                                    .setReplication(replication)
                                    .setBlockPadding(true)
                                    .setEncoding(enableEncoding)
                                    .setCompressionBlockSize(1)
                                    .build();
                        }
                        initPixelsFile = false;

                        rowBatch.size++;
                        rowCounter++;

                        String[] colsInLine = line.split(regex);
                        for (int i = 0; i < columnVectors.length; i++)
                        {
                            try
                            {
                                int valueIdx = orderMapping[i];
                                if (colsInLine[valueIdx].isEmpty() ||
                                        colsInLine[valueIdx].equalsIgnoreCase("\\N"))
                                {
                                    columnVectors[i].addNull();
                                } else
                                {
                                    columnVectors[i].add(colsInLine[valueIdx]);
                                }
                            }
                            catch (Exception e)
                            {
                                System.out.println("line: " + line);
                                e.printStackTrace();
                            }
                        }

                        if (rowBatch.size >= rowBatch.getMaxSize())
                        {
                            pixelsWriter.addRowBatch(rowBatch);
                            rowBatch.reset();
                            if (rowCounter >= maxRowNum)
                            {
                                pixelsWriter.close();
                                rowCounter = 0;
                                initPixelsFile = true;
                            }
                        }
                    }
                    reader.close();
                } else
                {
                    // no source file can be consumed within 2 seconds,
                    // loading is considered to be finished.
                    isRunning = false;
                }
            }

            if (rowCounter > 0)
            {
                // left last file to write
                if (rowBatch.size != 0)
                {
                    pixelsWriter.addRowBatch(rowBatch);
                    rowBatch.reset();
                }
                pixelsWriter.close();
            }
        } catch (InterruptedException e)
        {
            System.out.println("PixelsConsumer: " + e.getMessage());
            currentThread().interrupt();
        } catch (IOException e)
        {
            e.printStackTrace();
        } finally
        {
            System.out.println(currentThread().getName() + ":" + count);
            System.out.println("Exit PixelsConsumer, " + currentThread().getName() + ", time: " + DateUtil.formatTime(new Date()));
        }
    }
}
