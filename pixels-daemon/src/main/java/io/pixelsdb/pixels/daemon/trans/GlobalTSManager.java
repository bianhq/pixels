/*
 * Copyright 2021 PixelsDB.
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

package io.pixelsdb.pixels.daemon.trans;

import io.pixelsdb.pixels.daemon.Server;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created at: 3/27/21
 * Author: hank
 */
public class GlobalTSManager implements Server
{
    private static Logger log = LogManager.getLogger(GlobalTSManager.class);

    private AtomicLong currTimestamp = null;
    private boolean running = false;

    public GlobalTSManager ()
    {
        this.currTimestamp = new AtomicLong(0L);
    }

    @Override
    public boolean isRunning()
    {
        return this.running;
    }

    @Override
    public void shutdown()
    {
        this.running = false;
    }

    @Override
    public void run()
    {
        this.running = true;
        while (this.running)
        {
            try
            {
                TimeUnit.SECONDS.sleep(60);
            } catch (InterruptedException e)
            {
                log.error("interrupted in main loop of global timestamp manager.", e);
            }
        }
    }

    public long getTimestamp ()
    {
        return this.currTimestamp.getAndIncrement();
    }

    public long currentTimestamp ()
    {
        return this.currTimestamp.get();
    }

    public long rebaseTimestamp (long timestamp)
    {
        return this.currTimestamp.getAndSet(timestamp);
    }
}
