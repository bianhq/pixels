package io.pixelsdb.pixels.core;

import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.core.reader.PixelsReaderOption;
import io.pixelsdb.pixels.core.reader.PixelsRecordReader;
import io.pixelsdb.pixels.core.trans.DeleteBitMap;
import io.pixelsdb.pixels.core.trans.DeleteMapStore;
import io.pixelsdb.pixels.core.trans.DeleteTsMap;
import io.pixelsdb.pixels.core.trans.GlobalTsManager;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Created at: 25.09.20
 * Author: bian
 */
public class TestRealTimeUpdate
{
    private void buildDeleteMaps()
    {
        for (int i = 0; i < 10; ++i)
        {
            DeleteMapStore.Instance().createDeleteMapForFile(
                    "file:///home/hank/data/realtime/" + i + ".pxl",
                    10*1000*1000);
        }
    }

    private Set<Integer> delete (String filePath, int numRows)
    {
        //Set<Integer> deletedRowIds = new HashSet<>();
        Random random = new Random(System.nanoTime());
        for (int i = 0; i < numRows; ++i)
        {
            int rowId = random.nextInt(10*1000*1000);
            //while (deletedRowIds.contains(rowId))
            {
                rowId = random.nextInt(10*1000*1000);
            }
            //deletedRowIds.add(rowId);
            DeleteMapStore.Instance().getDeleteBitMap(filePath).setIntent(rowId);
            DeleteMapStore.Instance().getDeleteTSMap(filePath).setDeleteTimestamp(rowId,
                    GlobalTsManager.Instance().getTimestamp());
        }
        //return deletedRowIds;
        return new HashSet<>();
    }

    private void cleanIntents (String filePath, long lowWaterMark)
    {
        DeleteTsMap deleteTsMap = DeleteMapStore.Instance().getDeleteTSMap(filePath);
        Map<Integer, Long> dumped = deleteTsMap.dumpAndRemove(lowWaterMark);
        // TODO: write dumped timestamps to disk.
        DeleteBitMap deleteBitMap = DeleteMapStore.Instance().getDeleteBitMap(filePath);
        for (int rowId : dumped.keySet())
        {
            deleteBitMap.setDeleted(rowId);
        }
    }

    private String[] generateColValues (long indexCol)
    {
        Random random = new Random(System.nanoTime());
        String[] colValues = new String[10];
        colValues[0] = "" + indexCol;
        for (int i = 1; i < 10; ++i)
        {
            colValues[i] = "" + random.nextInt();
        }
        return colValues;
    }

    private void insert (String filePath, long indexColStart, int numRows) throws IOException
    {
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

        Storage storage = StorageFactory.Instance().getStorage(URI.create(filePath).getScheme());
        PixelsWriter pixelsWriter = PixelsWriterImpl.newBuilder()
                .setSchema(schema)
                .setPixelStride(10000)
                .setRowGroupSize(128*1024*1024)
                .setStorage(storage)
                .setFilePath(filePath)
                .setBlockSize(1024L*1024L*1024L)
                .setReplication((short) 1)
                .setBlockPadding(true)
                .setEncoding(true)
                .setCompressionBlockSize(1)
                .build();

        for (int j = 0; j < numRows; ++j)
        {
            String[] colsInLine = generateColValues(indexColStart++);
            rowBatch.putRow(GlobalTsManager.Instance().getTimestamp(), colsInLine, orderMap);
            if (rowBatch.size >= rowBatch.getMaxSize())
            {
                pixelsWriter.addRowBatch(rowBatch);
                rowBatch.reset();
            }
        }

        pixelsWriter.close();
    }

    private long scanAll (int fileNum) throws IOException
    {
        long numRows = 0;
        int invisibleNum = 0;
        PixelsReaderOption option = new PixelsReaderOption();
        String[] cols = {"col0", "col3"};
        option.skipCorruptRecords(true);
        option.tolerantSchemaEvolution(true);
        option.includeCols(cols);
        for (int j = 0; j < fileNum; ++j)
        {
            int rowIdInFile = 0;
            PixelsReader pixelsReader = null;
            String filePath = "file:///home/hank/data/realtime/" + j + ".pxl";
            Set<Integer> invisibleRowIds = DeleteMapStore.Instance().getInvisibleRowIds(
                    filePath, GlobalTsManager.Instance().currentTimestamp());
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
                recordReader.prepareBatch(10000);
                VectorizedRowBatch rowBatch = recordReader.readBatch(10000);

                if (rowBatch.size == 0 && rowBatch.endOfFile)
                {
                    break;
                }

                //LongColumnVector col0 = (LongColumnVector) rowBatch.cols[0];
                //LongColumnVector col3 = (LongColumnVector) rowBatch.cols[1];
                //LongColumnVector version = (LongColumnVector) rowBatch.cols[2];

                for (int i = 0; i < rowBatch.size; ++i)
                {
                    if (invisibleRowIds.contains(rowIdInFile))
                    {
                        invisibleNum++;
                    }
                    else
                    {
                        numRows++;
                    }
                    //if (this.endTimestamp[rowIdInFile] > GlobalTsManager.Instance().currentTimestamp())
                    {

                    }
                    rowIdInFile++;

                }

                if (rowBatch.endOfFile)
                {
                    break;
                }
            }

            recordReader.close();
            pixelsReader.close();
        }
        System.out.println(invisibleNum);
        return numRows;
    }

    @Test
    public void test () throws IOException
    {
        GlobalTsManager.Instance().rebaseTimestamp(100*1000*1000);
        buildDeleteMaps();

        long start = System.currentTimeMillis();
        long rowNum = scanAll(10);
        System.out.println("scan " + rowNum + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");

        //List<Set<Integer>> allDeletedRowIds = new ArrayList<>(10);
        long deletedNum = 0;
        start = System.currentTimeMillis();
        for (int i = 0; i < 10; ++i)
        {
            String filePath = "file:///home/hank/data/realtime/" + i + ".pxl";
            deletedNum += delete(filePath, 1000*1000).size();
            // Set<Integer> deletedRowIds = delete(filePath, 100*1000);
            //allDeletedRowIds.add(deletedRowIds);
        }
        System.out.println("delete " + deletedNum + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");

        start = System.currentTimeMillis();
        rowNum = scanAll(10);
        System.out.println("scan " + rowNum + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");

        start = System.currentTimeMillis();
        for (int i = 0; i < 10; ++i)
        {
            String filePath = "file:///home/hank/data/realtime/" + i + ".pxl";
            cleanIntents(filePath, GlobalTsManager.Instance().currentTimestamp());
        }
        System.out.println("clean intents cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");

        start = System.currentTimeMillis();
        rowNum = scanAll(10);
        System.out.println("scan " + rowNum + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");

        start = System.currentTimeMillis();
        String newFilePath = "file:///home/hank/data/realtime/10.pxl";
        insert(newFilePath, 100*1000*1000, 10*1000*1000);
        System.out.println("insert cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");

        start = System.currentTimeMillis();
        rowNum = scanAll(11);
        System.out.println("scan " + rowNum + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");
    }

    private long[] endTimestamp = new long[110*1000*1000];

    @Test
    public void testPixelsRead () throws IOException
    {
        GlobalTsManager.Instance().rebaseTimestamp(100*1000*1000);
        buildDeleteMaps();
        DeleteMapStore.Instance().createDeleteMapForFile(
                "file:///home/hank/data/realtime/10.pxl",
                10*1000*1000);
        long start = System.currentTimeMillis();
        long rowNum = scanAll(11);
        System.out.println("scan " + rowNum + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");
    }

    @Test
    public void testCStoreRead () throws IOException
    {long start = System.currentTimeMillis();
        GlobalTsManager.Instance().rebaseTimestamp(100*1000*1000);
        for (int i = 0; i < 110*1000*1000; ++i)
        {
            this.endTimestamp[i] = GlobalTsManager.Instance().getTimestamp();
        }System.out.println("scan " + 0 + " rows, cost: " + (System.currentTimeMillis()-start)/1000.0 + " (s)");


        //long rowNum = scanAll(11);

    }
}
