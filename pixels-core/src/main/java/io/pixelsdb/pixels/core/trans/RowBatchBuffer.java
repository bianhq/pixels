package io.pixelsdb.pixels.core.trans;

import com.google.common.collect.ImmutableList;
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
        // TODO: update delete bitmap and delete ts map.
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
