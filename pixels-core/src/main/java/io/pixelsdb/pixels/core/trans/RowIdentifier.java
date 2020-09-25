package io.pixelsdb.pixels.core.trans;

/**
 * This class is only used for test purpose before index update is implemented.
 * Created at: 25.09.20
 * Author: bian
 */
public class RowIdentifier
{
    private String filePath;
    private int rowIdInFile;
    private boolean existsInFile;

    public RowIdentifier(String filePath, int rowIdInFile, boolean existsInFile)
    {
        this.filePath = filePath;
        this.rowIdInFile = rowIdInFile;
        this.existsInFile = existsInFile;
    }

    public String getFilePath()
    {
        return filePath;
    }

    public int getRowIdInFile()
    {
        return rowIdInFile;
    }

    public boolean isExistsInFile ()
    {
        return existsInFile;
    }
}
