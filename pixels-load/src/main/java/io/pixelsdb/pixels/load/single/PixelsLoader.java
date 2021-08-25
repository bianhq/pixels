/*
 * Copyright 2018 PixelsDB.
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
package io.pixelsdb.pixels.load.single;

import io.pixelsdb.pixels.common.physical.Status;
import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import io.pixelsdb.pixels.common.utils.DateUtil;
import io.pixelsdb.pixels.common.utils.StringUtil;
import io.pixelsdb.pixels.core.PixelsWriter;
import io.pixelsdb.pixels.core.PixelsWriterImpl;
import io.pixelsdb.pixels.core.TypeDescription;
import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * pixels
 *
 * @author guodong
 */
public class PixelsLoader
        extends Loader
{
    PixelsLoader(String originalDataPath, String dbName, String tableName, int maxRowNum, String regex)
    {
        super(originalDataPath, dbName, tableName, maxRowNum, regex);
    }

    @Override
    protected boolean executeLoad(String originalDataPath, String loadingDataPath, String schemaStr,
                                  int[] orderMapping, ConfigFactory configFactory, int maxRowNum, String regex)
            throws IOException
    {
        Storage storage = StorageFactory.Instance().getStorage("hdfs");
        TypeDescription schema = TypeDescription.fromString(schemaStr);
        VectorizedRowBatch rowBatch = schema.createRowBatch();
        ColumnVector[] columnVectors = rowBatch.cols;
        int pixelStride = Integer.parseInt(configFactory.getProperty("pixel.stride"));
        int rowGroupSize = Integer.parseInt(configFactory.getProperty("row.group.size")) * 1024 * 1024;
        long blockSize = Long.parseLong(configFactory.getProperty("block.size")) * 1024l * 1024l;
        short replication = Short.parseShort(configFactory.getProperty("block.replication"));

        // read original data
        List<Status> statuses = storage.listStatus(originalDataPath);
        List<String> originalFilePaths = new ArrayList<>();
        for (Status status : statuses)
        {
            if (status.isFile())
            {
                originalFilePaths.add(status.getPath());
            }
        }
        BufferedReader reader = null;
        String line;
        String loadingFilePath = loadingDataPath + DateUtil.getCurTime() + ".pxl";
        PixelsWriter pixelsWriter = PixelsWriterImpl.newBuilder()
                .setSchema(schema)
                .setPixelStride(pixelStride)
                .setRowGroupSize(rowGroupSize)
                .setStorage(storage)
                .setFilePath(loadingFilePath)
                .setBlockSize(blockSize)
                .setReplication(replication)
                .setBlockPadding(true)
                .setEncoding(true)
                .setCompressionBlockSize(1)
                .build();
        int rowCounter = 0;
        for (String originalFilePath : originalFilePaths)
        {
            reader = new BufferedReader(new InputStreamReader(storage.open(originalFilePath)));
            while ((line = reader.readLine()) != null)
            {
                line = StringUtil.replaceAll(line, "false", "0");
                line = StringUtil.replaceAll(line, "False", "0");
                line = StringUtil.replaceAll(line, "true", "1");
                line = StringUtil.replaceAll(line, "True", "1");
                int rowId = rowBatch.size++;
                rowCounter++;
                if(regex.equals("\\s")){
                    regex = " ";
                }
                String[] colsInLine = line.split(regex);
                for (int i = 0; i < columnVectors.length; i++)
                {
                    int valueIdx = orderMapping[i];
                    if (colsInLine[valueIdx].equalsIgnoreCase("\\N"))
                    {
                        columnVectors[i].isNull[rowId] = true;
                    }
                    else
                    {
                        columnVectors[i].add(colsInLine[valueIdx]);
                    }
                }

                if (rowBatch.size >= rowBatch.getMaxSize())
                {
                    pixelsWriter.addRowBatch(rowBatch);
                    rowBatch.reset();
                    if (rowCounter >= maxRowNum)
                    {
                        pixelsWriter.close();
                        loadingFilePath = loadingDataPath + DateUtil.getCurTime() + ".pxl";
                        pixelsWriter = PixelsWriterImpl.newBuilder()
                                .setSchema(schema)
                                .setPixelStride(pixelStride)
                                .setRowGroupSize(rowGroupSize)
                                .setStorage(storage)
                                .setFilePath(loadingFilePath)
                                .setBlockSize(blockSize)
                                .setReplication(replication)
                                .setBlockPadding(true)
                                .setEncoding(true)
                                .setCompressionBlockSize(1)
                                .build();
                        rowCounter = 0;
                    }
                }
            }
        }
        if (rowBatch.size != 0)
        {
            pixelsWriter.addRowBatch(rowBatch);
            rowBatch.reset();
        }
        pixelsWriter.close();
        if (reader != null)
        {
            reader.close();
        }

        return true;
    }
}
