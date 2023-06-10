/*
 * Copyright 2023 PixelsDB.
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
package io.pixelsdb.pixels.daemon.metadata.dao.impl;

import io.pixelsdb.pixels.daemon.MetadataProto;
import io.pixelsdb.pixels.daemon.metadata.dao.PeerPathDao;

import java.util.List;

/**
 * @author hank
 * @create 2023-06-10
 */
public class RdbPeerPathDao extends PeerPathDao
{
    @Override
    public MetadataProto.PeerPath getById(long id)
    {
        return null;
    }

    @Override
    public List<MetadataProto.PeerPath> getAllByPath(MetadataProto.Path path)
    {
        return null;
    }

    @Override
    public List<MetadataProto.PeerPath> getAllByPeer(MetadataProto.Peer peer)
    {
        return null;
    }

    @Override
    public boolean exists(MetadataProto.PeerPath peerPath)
    {
        return false;
    }

    @Override
    public boolean insert(MetadataProto.PeerPath peerPath)
    {
        return false;
    }

    @Override
    public boolean update(MetadataProto.PeerPath peerPath)
    {
        return false;
    }

    @Override
    public boolean deleteById(long id)
    {
        return false;
    }
}
