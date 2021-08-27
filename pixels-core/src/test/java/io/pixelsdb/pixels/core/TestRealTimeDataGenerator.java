package io.pixelsdb.pixels.core;

import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.core.reader.PixelsReaderOption;
import io.pixelsdb.pixels.core.reader.PixelsRecordReader;
import io.pixelsdb.pixels.core.trans.GlobalTsManager;
import io.pixelsdb.pixels.core.vector.LongColumnVector;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.util.Random;

/**
 * Created at: 28.09.20
 * Author: bian
 */
public class TestRealTimeDataGenerator
{
    @Test
    public void generateData () throws IOException
    {
        BufferedWriter writer = new BufferedWriter(new FileWriter("/home/hank/data/realtime.csv"));
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
        BufferedReader reader = new BufferedReader(new FileReader("/home/hank/data/realtime.csv"));
        String line;
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
        VectorizedRowBatch rowBatch = schema.createRowBatch(10000);
        int[] orderMap = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};

        Storage storage = StorageFactory.Instance().getStorage(URI.create("file:///home/hank/data/realtime/").getScheme());

        for (int i = 0; i < 10; ++i)
        {
            String pixelsFilePath = "file:///home/hank/data/realtime/" + i + ".pxl";
            pixelsWriter = PixelsWriterImpl.newBuilder()
                    .setSchema(schema)
                    .setPixelStride(10000)
                    .setRowGroupSize(128*1024*1024)
                    .setStorage(storage)
                    .setFilePath(pixelsFilePath)
                    .setBlockSize(1024L*1024L*1024L)
                    .setReplication((short) 1)
                    .setBlockPadding(true)
                    .setEncoding(true)
                    .setCompressionBlockSize(1)
                    .build();

            for (int j = 0; j < 10*1000*1000; ++j)
            {
                line = reader.readLine();
                String[] colsInLine = line.split(",");
                rowBatch.putRow(GlobalTsManager.Instance().getTimestamp(), colsInLine, orderMap);
                if (rowBatch.size >= rowBatch.getMaxSize())
                {
                    pixelsWriter.addRowBatch(rowBatch);
                    rowBatch.reset();
                }
            }

            pixelsWriter.close();
        }

        reader.close();
    }

    @Test
    public void readPixelsFiles () throws IOException
    {
        PixelsReaderOption option = new PixelsReaderOption();
        String[] cols = {"col0", "col3"};
        option.skipCorruptRecords(true);
        option.tolerantSchemaEvolution(true);
        long start = System.currentTimeMillis();
        option.includeCols(cols);
        for (int j = 0; j < 10; ++j)
        {
            PixelsReader pixelsReader = null;
            String filePath = "file:///home/hank/data/realtime/" + j + ".pxl";
            try
            {
                Storage storage = StorageFactory.Instance().getStorage(URI.create(filePath).getScheme());
                pixelsReader = PixelsReaderImpl
                        .newBuilder()
                        .setStorage(storage)
                        .setPath(filePath)
                        .setEnableCache(false)
                        .setPixelsFooterCache(new PixelsFooterCache())
                        .build();
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            PixelsRecordReader recordReader = pixelsReader.read(option);

            while (true)
            {
                recordReader.prepareBatch(21000);
                VectorizedRowBatch rowBatch = recordReader.readBatch(21000);

                if (rowBatch.endOfFile)
                {
                    break;
                }

                LongColumnVector col0 = (LongColumnVector) rowBatch.cols[0];
                LongColumnVector col3 = (LongColumnVector) rowBatch.cols[1];
                LongColumnVector version = (LongColumnVector) rowBatch.cols[2];

                for (int i = 0; i < 10; ++i)
                {
                    System.out.println(col0.vector[i] + "," + col3.vector[i] + "," + version.vector[i]);
                }
            }

            recordReader.close();
            pixelsReader.close();
        }
        System.out.println((System.currentTimeMillis() - start) / 1000.0);
    }
}
