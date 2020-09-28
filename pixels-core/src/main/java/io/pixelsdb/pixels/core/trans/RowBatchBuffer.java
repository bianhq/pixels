package io.pixelsdb.pixels.core.trans;

import com.google.common.collect.ImmutableList;
import io.pixelsdb.pixels.core.vector.LongColumnVector;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Created at: 25.09.20
 * Author: bian
 */
public class RowBatchBuffer
{
    private List<VectorizedRowBatch> rowBatches = new ArrayList<>();

    private RowBatchBuffer () {}

    private static RowBatchBuffer instance = null;

    public static RowBatchBuffer Instance ()
    {
        if (instance == null)
        {
            instance = new RowBatchBuffer();
        }
        return instance;
    }

    synchronized public void putRowBatch (VectorizedRowBatch rowBatch)
    {
        this.rowBatches.add(rowBatch);
        // TODO: we have not implemented indexes, so we just use the first column as index column.
        LongColumnVector indexColumn = (LongColumnVector) rowBatch.cols[0];
        LongColumnVector versionColumn = (LongColumnVector) rowBatch.getVersionColumn();
        for (int i = 0; i < rowBatch.size; ++i)
        {
            long timestamp = versionColumn.vector[i];
            long indexValue = indexColumn.vector[i];
            // TODO: find the target file and update its delete maps.
            RowIdentifier rowIdentifier = DeleteMapStore.Instance().generateRowIdentifier(indexValue);
            String targetFilePath = rowIdentifier.getFilePath();
            int targetRowId = rowIdentifier.getRowIdInFile();
            boolean find = rowIdentifier.isExistsInFile();
            if (find)
            {
                DeleteMapStore.Instance().getDeleteBitMap(targetFilePath)
                        .setIntent(targetRowId);
                DeleteMapStore.Instance().getDeleteTSMap(targetFilePath)
                        .setDeleteTimestamp(targetRowId, timestamp);
            }
        }
    }

    synchronized public List<VectorizedRowBatch> getRowBatches ()
    {
        return ImmutableList.copyOf(this.rowBatches);
    }

    synchronized void clear ()
    {
        this.rowBatches.clear();
    }
}
