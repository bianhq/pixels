package io.pixelsdb.pixels.core.trans;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Created at: 17.09.20
 * Author: bian
 */
public class DeleteBitMap
{
    private final AtomicIntegerArray backedArray;

    public DeleteBitMap(int numEntry)
    {
        int numElements = (numEntry * 2 + 31) / 32;
        backedArray = new AtomicIntegerArray(numElements);
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
        int n = entryIndex >> 4;
        int i = (entryIndex % 16) * 2;
        int originValue = this.backedArray.get(n);
        if ((originValue & (0x03 << i)) > 0)
        {
            return false;
        }
        int newValue = originValue | (0x02 << i);
        while (this.backedArray.compareAndSet(n, originValue, newValue) == false)
        {
            originValue = this.backedArray.get(n);
            if ((originValue & (0x03 << i)) > 0)
            {
                return false;
            }
            newValue = originValue | (0x02 << i);
        }
        return true;
    }

    public boolean setDeleted (int entryIndex)
    {
        int n = entryIndex >> 4;
        int i = (entryIndex % 16) * 2;
        int originValue = this.backedArray.get(n);
        if ((originValue & (0x02 << i)) == 0)
        {
            return false;
        }
        int newValue = originValue | (0x03 << i);
        while (this.backedArray.compareAndSet(n, originValue, newValue) == false)
        {
            originValue = this.backedArray.get(n);
            if ((originValue & (0x02 << i)) == 0)
            {
                return false;
            }
            newValue = originValue | (0x03 << i);
        }
        return true;
    }

    public EntryStatus getStatus (int entryIndex)
    {
        int n = entryIndex >> 4;
        int i = (entryIndex % 16) * 2;
        switch (this.backedArray.get(n) >> i)
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
}
