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
package io.pixelsdb.pixels.presto.exception;

import com.facebook.presto.spi.ErrorCode;
import com.facebook.presto.spi.ErrorCodeSupplier;
import com.facebook.presto.spi.ErrorType;

import static com.facebook.presto.spi.ErrorType.*;

public enum PixelsErrorCode
        implements ErrorCodeSupplier
{
    PIXELS_METASTORE_ERROR(0, EXTERNAL),
    PIXELS_CURSOR_ERROR(1, EXTERNAL),
    PIXELS_TABLE_OFFLINE(2, USER_ERROR),
    PIXELS_CANNOT_OPEN_SPLIT(3, EXTERNAL),
    PIXELS_FILE_NOT_FOUND(4, EXTERNAL),
    PIXELS_UNKNOWN_ERROR(5, EXTERNAL),
    PIXELS_PARTITION_OFFLINE(6, USER_ERROR),
    PIXELS_BAD_DATA(7, EXTERNAL),
    PIXELS_PARTITION_SCHEMA_MISMATCH(8, EXTERNAL),
    PIXELS_MISSING_DATA(9, EXTERNAL),
    PIXELS_INVALID_PARTITION_VALUE(10, EXTERNAL),
    PIXELS_TIMEZONE_MISMATCH(11, EXTERNAL),
    PIXELS_INVALID_METADATA(12, EXTERNAL),
    PIXELS_INVALID_VIEW_DATA(13, EXTERNAL),
    PIXELS_DATABASE_LOCATION_ERROR(14, EXTERNAL),
    PIXELS_PATH_ALREADY_EXISTS(15, EXTERNAL),
    PIXELS_FILESYSTEM_ERROR(16, EXTERNAL),
    // code PIXELS_WRITER_ERROR(17) is deprecated
    PIXELS_SERDE_NOT_FOUND(18, EXTERNAL),
    PIXELS_UNSUPPORTED_FORMAT(19, EXTERNAL),
    PIXELS_PARTITION_READ_ONLY(20, USER_ERROR),
    PIXELS_TOO_MANY_OPEN_PARTITIONS(21, USER_ERROR),
    PIXELS_CONCURRENT_MODIFICATION_DETECTED(22, EXTERNAL),
    PIXELS_COLUMN_ORDER_MISMATCH(23, USER_ERROR),
    PIXELS_FILE_MISSING_COLUMN_NAMES(24, EXTERNAL),
    PIXELS_WRITER_OPEN_ERROR(25, EXTERNAL),
    PIXELS_WRITER_CLOSE_ERROR(26, EXTERNAL),
    PIXELS_WRITER_DATA_ERROR(27, EXTERNAL),
    PIXELS_INVALID_BUCKET_FILES(28, EXTERNAL),
    PIXELS_EXCEEDED_PARTITION_LIMIT(29, USER_ERROR),
    PIXELS_WRITE_VALIDATION_FAILED(30, INTERNAL_ERROR),
    PIXELS_PARTITION_DROPPED_DURING_QUERY(31, EXTERNAL),
    PIXELS_TABLE_READ_ONLY(32, USER_ERROR),
    PIXELS_PARTITION_NOT_READABLE(33, USER_ERROR),
    PIXELS_TABLE_NOT_READABLE(34, USER_ERROR),
    PIXELS_TABLE_DROPPED_DURING_QUERY(35, EXTERNAL),
    PIXELS_READER_ERROR(36, EXTERNAL),
    PIXELS_HDFS_FILE_ERROR(37, EXTERNAL),
    PIXELS_CONFIG_ERROR(38, EXTERNAL),
    PIXELS_HDFS_BLOCK_ERROR(39, EXTERNAL),
    PIXELS_SQL_EXECUTE_ERROR(40, EXTERNAL),
    PIXELS_CLIENT_ERROR(41, EXTERNAL),
    PIXELS_THREAD_ERROR(42, EXTERNAL),
    PIXELS_CLIENT_SERIVCE_ERROR(43, EXTERNAL),
    PIXELS_CONNECTOR_ERROR(44, EXTERNAL),
    PIXELS_INVERTED_INDEX_ERROR(45, EXTERNAL),
    PIXELS_CACHE_NODE_FILE_ERROR(46, EXTERNAL),
    PIXELS_CACHE_VERSION_ERROR(47, EXTERNAL),
    PIXELS_SPLIT_BALANCER_ERROR(48, EXTERNAL),
    PIXELS_READER_CLOSE_ERROR(49, EXTERNAL),
    PIXELS_STORAGE_ERROR(50, EXTERNAL),
    PIXELS_TRANS_SERVICE_ERROR(51, EXTERNAL),
    PIXELS_TRANS_HANDLE_TYPE_ERROR(52, EXTERNAL)
    /**/;

    private final ErrorCode errorCode;

    PixelsErrorCode(int code, ErrorType type)
    {
        errorCode = new ErrorCode(code + 0x0100_0000, name(), type);
    }

    @Override
    public ErrorCode toErrorCode()
    {
        return errorCode;
    }
}
