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

import com.facebook.presto.spi.Plugin;
import com.facebook.presto.spi.block.BlockEncoding;
import com.facebook.presto.spi.connector.ConnectorFactory;
import com.google.common.collect.ImmutableList;
import io.pixelsdb.pixels.presto.block.TimeArrayBlockEncoding;
import io.pixelsdb.pixels.presto.block.VarcharArrayBlockEncoding;

public class PixelsPlugin
        implements Plugin
{
    @Override
    public Iterable<BlockEncoding> getBlockEncodings()
    {
        return ImmutableList.of(VarcharArrayBlockEncoding.Instance(), TimeArrayBlockEncoding.Instance());
    }

    @Override
    public Iterable<ConnectorFactory> getConnectorFactories()
    {
        return ImmutableList.of(new PixelsConnectorFactory());
    }
}
