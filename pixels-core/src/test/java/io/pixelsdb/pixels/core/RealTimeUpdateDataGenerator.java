package io.pixelsdb.pixels.core;

import io.pixelsdb.pixels.core.vector.ColumnVector;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.util.Random;

/**
 * Created at: 28.09.20
 * Author: bian
 */
public class RealTimeUpdateDataGenerator
{
    @Test
    public void generateData () throws IOException
    {
        BufferedWriter writer = new BufferedWriter(new FileWriter("/scratch/data/realtime.csv"));
        Random random = new Random(System.nanoTime());
        for (int i = 0; i < 100*1000*1000; ++i)
        {
            writer.write(i + ",");
            for (int j = 0; j < 8; ++j)
            {
                writer.write(random.nextInt() + ",");
            }
            writer.write(random.nextInt() + "\n");
        }
        writer.close();
    }

    @Test
    public void generatePixelsFiles () throws IOException
    {
        BufferedReader reader = new BufferedReader(new FileReader("/scratch/data/realtime.csv"));
        String line;
        long rowCounter = 0;
        PixelsWriter pixelsWriter = null;

        // construct pixels schema based on the column order of the latest writing layout
        StringBuilder schemaBuilder = new StringBuilder("struct<");
        for (int i = 0; i < 10; i++)
        {
            schemaBuilder.append("col" + i).append(":").append("bigint")
                    .append(",");
        }
        schemaBuilder.replace(schemaBuilder.length() - 1, schemaBuilder.length(), ">");
        TypeDescription schema = TypeDescription.fromString(schemaBuilder.toString());
        VectorizedRowBatch rowBatch = schema.createRowBatch();
        ColumnVector[] columnVectors = rowBatch.cols;


        Configuration conf = new Configuration();
        // conf.set("fs.hdfs.impl", LocalFileSystem.class.getName());
        conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());
        FileSystem fs = FileSystem.get(URI.create("file://scratch/data/realtime/"), conf);

        while ((line = reader.readLine()) != null)
        {
            if (rowCounter % (10*1000*1000) == 0)
            {
                // we create a new pixels file if we can read a next line from the source file.
                String pixelsFilePath = "file://scratch/data/realtime/" + (rowCounter / (10*1000*1000)) + ".pxl";
                pixelsWriter = PixelsWriterImpl.newBuilder()
                        .setSchema(schema)
                        .setPixelStride(10000)
                        .setRowGroupSize(256*1024*1024)
                        .setFS(fs)
                        .setFilePath(new Path(pixelsFilePath))
                        .setBlockSize(1024L*1024L*1024L)
                        .setReplication((short) 1)
                        .setBlockPadding(true)
                        .setEncoding(true)
                        .setCompressionBlockSize(1)
                        .build();
            }
            rowCounter++;

            int rowId = rowBatch.size++;
            String[] colsInLine = line.split(",");
            for (int i = 0; i < columnVectors.length; i++)
            {
                columnVectors[i].add(colsInLine[i]);
            }

            if (rowBatch.size >= rowBatch.getMaxSize())
            {
                pixelsWriter.addRowBatch(rowBatch);
                rowBatch.reset();
                if (rowCounter % (10*1000*1000) == 0)
                {
                    pixelsWriter.close();
                }
            }
        }
        reader.close();
    }
}
