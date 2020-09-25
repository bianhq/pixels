package io.pixelsdb.pixels.core.trans;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Created at: 17.09.20, for Issue #82.
 * Author: bian
 */
public class DeleteBitMap
{
    private final AtomicLongArray backedArray;

    public DeleteBitMap(int numEntry)
    {
        int numElements = (numEntry * 2 + 31) / 32;
        backedArray = new AtomicLongArray(numElements);
    }

    public enum EntryStatus
    {
        EXISTING,
        DELETE_INTENT,
        DELETED,
        ERROR
    }

    public boolean setIntent (int entryIndex)
    {
        int n = entryIndex >> 5;
        int i = (entryIndex % 32) * 2;
        long originValue = this.backedArray.get(n);
        if (((originValue >>> i) & 0x03L) > 0)
        {
            // if the bits for entryIndex is already set.
            return false;
        }
        // start from the lowest bits.
        long newValue = originValue | (0x02L << i);
        while (this.backedArray.compareAndSet(n, originValue, newValue) == false)
        {
            originValue = this.backedArray.get(n);
            if (((originValue >>> i) & 0x03L) > 0)
            {
                return false;
            }
            newValue = originValue | (0x02L << i);
        }
        return true;
    }

    public boolean setDeleted (int entryIndex)
    {
        int n = entryIndex >> 5;
        int i = (entryIndex % 32) * 2;
        long originValue = this.backedArray.get(n);
        if (((originValue >>> i) & 0x02L) == 0)
        {
            // if the bits for entryIndex is already set.
            return false;
        }
        // start from the lowest bits.
        long newValue = originValue | (0x03L << i);
        while (this.backedArray.compareAndSet(n, originValue, newValue) == false)
        {
            originValue = this.backedArray.get(n);
            if (((originValue >>> i) & 0x02L) == 0)
            {
                return false;
            }
            newValue = originValue | (0x03L << i);
        }
        return true;
    }

    public EntryStatus getStatus (int entryIndex)
    {
        int n = entryIndex >> 5;
        int i = (entryIndex % 32) * 2;
        switch ((int)(this.backedArray.get(n) >>> i) & 0x03)
        {
            case 0x00:
                return EntryStatus.EXISTING;
            case 0x02:
                return EntryStatus.DELETE_INTENT;
            case 0x03:
                return EntryStatus.DELETED;
            default:
                return EntryStatus.ERROR;
        }
    }

    public void getBatchRowIds (Set<Integer> deletedRowIds, Set<Integer> intentRowIds)
    {
        assert deletedRowIds != null && intentRowIds != null;

        for (int i = 0; i < this.backedArray.length(); ++i)
        {
            long v = this.backedArray.get(i);
            for (int j = 0; j < 64; j+=2)
            {
                switch ((int)(v >>> j) & 0x03)
                {
                    case 0x02:
                        intentRowIds.add(i*32+j/2);
                        break;
                    case 0x03:
                        deletedRowIds.add(i*32+j/2);
                        break;
                }
            }
        }
    }
}
