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
package io.pixelsdb.pixels.presto.impl;

import io.pixelsdb.pixels.common.exception.FSException;
import io.pixelsdb.pixels.common.physical.FSFactory;
import io.pixelsdb.pixels.common.physical.PhysicalFSReader;
import com.facebook.presto.spi.HostAddress;
import com.google.common.collect.Iterables;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.Path;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;

/**
 * @author: tao
 * @date: Create in 2018-01-22 21:53
 **/
public class TestFSFactory {

    @Test
    public void testListFiles() throws FSException
    {
        PixelsPrestoConfig config = new PixelsPrestoConfig().setPixelsHome("");
        FSFactory fsFactory = config.getFsFactory();
        String tablePath = "/pixels/pixels/test_105/v_0_compact";
        List<Path> files = fsFactory.listFiles(tablePath);
        for (Path s : files) {
            System.out.println(s.getName());
        }
    }

    @Test
    public void testGetBlockLocations() throws UnknownHostException, FSException
    {
        PixelsPrestoConfig config = new PixelsPrestoConfig().setPixelsHome("");
        FSFactory fsFactory = config.getFsFactory();
        String tablePath = "pixels/db/default/test/Point.txt";
        List<HostAddress> files = fsFactory.getBlockLocations(new Path(tablePath), 0, Long.MAX_VALUE);
        System.out.println(files.size());
        for (HostAddress hs : files) {
            System.out.println(hs.toInetAddress().toString());
            System.out.println(hs.getHostText());
        }
        files.remove(0);
        System.out.println(files.size());
        HostAddress hostAddress = Iterables.getOnlyElement(files);
        System.out.println(hostAddress.toInetAddress().toString());
        System.out.println(hostAddress.getHostText());
    }

    @Test
    public void testGetBlockIds() throws IOException, FSException
    {
        PixelsPrestoConfig config = new PixelsPrestoConfig().setPixelsHome("");
        FSFactory fsFactory = config.getFsFactory();
        String tablePath = "/pixels/pixels/test_105/source/000000_0";
        FSDataInputStream in = fsFactory.getFileSystem().get().open(new Path(tablePath));
        /*
        System.out.println(in.getPos());
        byte[] bytes = new byte[1024];
        in.read(bytes);
        System.out.println(in.getPos());
        System.out.println(((HdfsDataInputStream)in).getCurrentBlock().getBlockId());
        in.close();
        FSDataInputStream in2 = fsFactory.getFileSystem().get().open(new Path(tablePath));
        System.out.println(in2.getPos());
        in2.read(bytes);
        in2.getWrappedStream();
        System.out.println(((HdfsDataInputStream)in2).getCurrentBlock().getBlockId());
        List<LocatedBlock> blocks = ((HdfsDataInputStream)in2).getAllBlocks();
        for (LocatedBlock block : blocks)
        {
            System.out.println(block.getStartOffset());
        }
        */

        PhysicalFSReader fsReader = new PhysicalFSReader(fsFactory.getFileSystem().get(),
                new Path("/pixels/pixels/test_105/source/000000_0"), in);
        System.out.println(fsReader.getCurrentBlockId());
        fsReader.seek(2*1024l*1024l*1024l);
        System.out.println(fsReader.getCurrentBlockId());
    }
}
