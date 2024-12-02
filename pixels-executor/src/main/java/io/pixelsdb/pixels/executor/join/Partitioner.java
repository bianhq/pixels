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
package io.pixelsdb.pixels.executor.join;

import io.pixelsdb.pixels.core.TypeDescription;
import io.pixelsdb.pixels.core.vector.VectorizedRowBatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * Partition a set of row batches into N sets of row batches.
 * Each result row batch set corresponds to a hash partition.
 *
 * @author hank
 * @date 07/05/2022
 */
public class Partitioner
{
    private final int numPartition;
    private final int batchSize;
    private final TypeDescription schema;
    private final int numFields;
    private final int[] keyColumnIds;
    private final VectorizedRowBatch[] rowBatches;
    private final int[][] selectedArrays;
    private final int[] selectedArrayIndexes;
    private final int[] hashCode;
    private int hashCodeLength;

    /**
     * Create a partitioner to partition the input row batches into a number of
     * partitions, using the hash function (abs(key.hashCode())%numPartition).
     * <p/>
     * For combined partition key, the key column must be the first numKeyColumns columns
     * int the input row batches. And the hash code of the key is computed as:
     * <blockquote>
     * col[0].hashCode()*524287^(n-1) + col[1].hashCode()*524287^(n-2) + ... + col[n-1].hashCode()
     * </blockquote>
     * Where col[i] is the value of the ith column in the partition key.
     *
     * @param numPartition the number of partitions
     * @param batchSize the number of rows in each output row batches
     * @param schema the schema of the input and output row batches
     * @param keyColumnIds the ids of the key columns
     */
    public Partitioner(int numPartition, int batchSize, TypeDescription schema, int[] keyColumnIds)
    {
        checkArgument(numPartition > 0, "partitionNum must be positive");
        checkArgument(batchSize > 0, "batchSize must be positive");
        requireNonNull(schema, "schema is null");
        requireNonNull(schema.getChildren(), "schema is empty");
        checkArgument(keyColumnIds != null && keyColumnIds.length > 0,
                "keyColumnIds is null or empty");
        this.numPartition = numPartition;
        this.batchSize = batchSize;
        this.schema = schema;
        this.numFields = schema.getChildren().size();
        this.keyColumnIds = keyColumnIds;
        this.rowBatches = new VectorizedRowBatch[numPartition];
        this.selectedArrays = new int[numPartition][];
        this.selectedArrayIndexes = new int[numPartition];
        for (int i = 0; i < numPartition; ++i)
        {
            this.rowBatches[i] = schema.createRowBatch(batchSize, TypeDescription.Mode.NONE);
            this.selectedArrays[i] = new int[batchSize];
            this.selectedArrayIndexes[i] = 0;
        }
        this.hashCode = new int[batchSize];
        this.hashCodeLength = 0;
    }

    /**
     * Partition the rows in the input row batch. This method is not thread-safe.
     *
     * @param input the input row batch
     * @return the output row batches that are full.
     */
    public Map<Integer, VectorizedRowBatch> partition(VectorizedRowBatch input)
    {
        requireNonNull(input, "input is null");
        checkArgument(input.size <= batchSize, "input is oversize");
        // this.schema.getChildren() has been checked not null.
        checkArgument(input.numCols == this.numFields,
                "input.numCols does not match the number of fields in the schema");
        computeHashCode(input);
        for (int i = 0; i < this.hashCodeLength; ++i)
        {
            int hashKey = Math.abs(this.hashCode[i]) % this.numPartition;
            // add the row id to the selected array of the partition.
            this.selectedArrays[hashKey][this.selectedArrayIndexes[hashKey]++] = i;
        }

        Map<Integer, VectorizedRowBatch> output = new HashMap<>();
        for (int hash = 0; hash < numPartition; ++hash)
        {
            if (this.selectedArrayIndexes[hash] == 0)
            {
                // this partition is empty
                continue;
            }
            int freeSlots = rowBatches[hash].freeSlots();
            int[] selected = this.selectedArrays[hash];
            int selectedLength = this.selectedArrayIndexes[hash];
            this.selectedArrayIndexes[hash] = 0;
            if (freeSlots == 0)
            {
                output.put(hash, rowBatches[hash]);
                rowBatches[hash] = schema.createRowBatch(batchSize, TypeDescription.Mode.NONE);
                rowBatches[hash].addSelected(selected, 0, selectedLength, input);
            }
            else if (freeSlots <= selectedLength)
            {
                rowBatches[hash].addSelected(selected, 0, freeSlots, input);
                output.put(hash, rowBatches[hash]);
                rowBatches[hash] = schema.createRowBatch(batchSize, TypeDescription.Mode.NONE);
                if (freeSlots < selectedLength)
                {
                    rowBatches[hash].addSelected(selected, freeSlots, selectedLength - freeSlots, input);
                }
            }
            else
            {
                rowBatches[hash].addSelected(selected, 0, selectedLength, input);
            }
        }
        return output;
    }

    /**
     * Get the hash code of the partition key of the rows in the input row batch.
     * @param input the input row batch
     */
    private void computeHashCode(VectorizedRowBatch input)
    {
        this.hashCodeLength = input.size;
        Arrays.fill(hashCode, 0, this.hashCodeLength, 0);
        for (int columnId : keyColumnIds)
        {
            input.cols[columnId].accumulateHashCode(hashCode);
        }
    }

    public int getNumPartition()
    {
        return numPartition;
    }

    public VectorizedRowBatch[] getRowBatches()
    {
        return rowBatches;
    }
}
