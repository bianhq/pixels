/*
 * Copyright 2025 PixelsDB.
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
package io.pixelsdb.pixels.common.transaction;

import io.pixelsdb.pixels.common.exception.TransException;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author hank
 * @create 2025-10-01
 */
public class TestTransService
{
    @Test
    public void test() throws TransException, InterruptedException
    {
        TransService transService = TransService.CreateInstance("10.77.110.37", 18889);
        ExecutorService executorService = Executors.newFixedThreadPool(64);
        for (int i = 0; i < 64; i++)
        {
            executorService.submit(() -> {
                List<TransContext> contexts = new LinkedList<>();
                long start = System.currentTimeMillis();
                for (int j = 0; j < 10240; j++)
                {
                    try
                    {
                        TransContext context = transService.beginTrans(false);
                        contexts.add(context);
                    } catch (TransException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println(System.currentTimeMillis() - start);

                start = System.currentTimeMillis();
                for (TransContext context : contexts)
                {
                    try
                    {
                        transService.commitTrans(context.getTransId(), context.getTimestamp());
                    } catch (TransException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
                System.out.println(System.currentTimeMillis() - start);
            });
        }
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.HOURS);
    }
}
