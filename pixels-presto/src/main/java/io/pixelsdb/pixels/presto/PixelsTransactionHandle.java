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
package io.pixelsdb.pixels.presto;

import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import io.pixelsdb.pixels.common.transaction.QueryTransInfo;

import static java.util.Objects.requireNonNull;

/**
 * @author hank
 * Finished at: 20/02/2022
 */
public class PixelsTransactionHandle
        implements ConnectorTransactionHandle
{

    private QueryTransInfo info;

    public PixelsTransactionHandle(QueryTransInfo info)
    {
        this.info = requireNonNull(info, "info is null");
    }

    public long getQueryId()
    {
        return this.info.getQueryId();
    }

    public long getQueryTimestamp()
    {
        return this.info.getQueryTimestamp();
    }

    public QueryTransInfo getInfo()
    {
        return info;
    }
}
