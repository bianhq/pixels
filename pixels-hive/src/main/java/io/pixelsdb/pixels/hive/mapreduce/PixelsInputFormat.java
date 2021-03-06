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
package io.pixelsdb.pixels.hive.mapreduce;

import io.pixelsdb.pixels.core.PixelsReader;
import io.pixelsdb.pixels.hive.common.PixelsRW;
import io.pixelsdb.pixels.hive.common.PixelsSplit;
import io.pixelsdb.pixels.hive.common.PixelsStruct;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hive.shims.ShimLoader;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;

import java.io.IOException;
import java.util.List;

/**
 * Pixels input format for new MapReduce InputFormat API.
 * Created at: 19-6-15
 * Author: hank
 */
public class PixelsInputFormat extends FileInputFormat<NullWritable, PixelsStruct>
{
    /**
     * Generate the list of files and make them into FileSplits.
     *
     * @param job the job context
     * @throws IOException
     */
    @Override
    public List<InputSplit> getSplits(JobContext job) throws IOException
    {
        // TODO: implement dynamic splitting.
        return super.getSplits(job);
    }

    /**
     * Create a record reader for a given split. The framework will call
     * {@link RecordReader#initialize(InputSplit, TaskAttemptContext)} before
     * the split is used.
     *
     * @param split   the split to be read
     * @param context the information about the task
     * @return a new record reader
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public RecordReader createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException
    {
        Configuration conf = ShimLoader.getHadoopShims()
                .getConfiguration(context);
        PixelsSplit pixelsSplit = (PixelsSplit) split;
        PixelsRW.ReaderOptions options = PixelsRW.readerOptions(conf, pixelsSplit);
        PixelsReader reader = PixelsRW.createReader(pixelsSplit.getPath(), options);
        return new PixelsMapReduceRecordReader(reader, options);
    }
}
