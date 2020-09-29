package io.pixelsdb.pixels.core.trans;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Created at: 23.09.20, for Issue #82.
 * Author: bian
 */
public class GlobalTsManager
{
    private AtomicLong currTimestamp = null;

    private GlobalTsManager ()
    {
        this.currTimestamp = new AtomicLong(0L);
    }

    private static GlobalTsManager instance = null;

    public static GlobalTsManager Instance ()
    {
        if (instance == null)
        {
            instance = new GlobalTsManager();
        }
        return instance;
    }

    public long getTimestamp ()
    {
        return this.currTimestamp.getAndIncrement();
    }
}
