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

import com.alibaba.fastjson.JSON;
import com.coreos.jetcd.data.KeyValue;
import com.facebook.presto.spi.*;
import com.facebook.presto.spi.connector.ConnectorSplitManager;
import com.facebook.presto.spi.connector.ConnectorTransactionHandle;
import com.facebook.presto.spi.predicate.TupleDomain;
import com.google.common.collect.ImmutableList;
import io.airlift.log.Logger;
import io.pixelsdb.pixels.common.exception.MetadataException;
import io.pixelsdb.pixels.common.metadata.domain.*;
import io.pixelsdb.pixels.common.physical.Location;
import io.pixelsdb.pixels.common.physical.Storage;
import io.pixelsdb.pixels.common.physical.StorageFactory;
import io.pixelsdb.pixels.common.split.*;
import io.pixelsdb.pixels.common.utils.Constants;
import io.pixelsdb.pixels.common.utils.EtcdUtil;
import io.pixelsdb.pixels.presto.exception.CacheException;
import io.pixelsdb.pixels.presto.exception.PixelsErrorCode;
import io.pixelsdb.pixels.presto.impl.PixelsMetadataProxy;
import io.pixelsdb.pixels.presto.impl.PixelsPrestoConfig;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toSet;

/**
 * @author hank
 * @author guodong
 * @author tao
 * @date: Create in 2018-01-20 19:16
 **/
@SuppressWarnings("Duplicates")
public class PixelsSplitManager
        implements ConnectorSplitManager {
    private static final Logger logger = Logger.get(PixelsSplitManager.class);
    private final String connectorId;
    private final PixelsMetadataProxy metadataProxy;
    private final boolean cacheEnabled;
    private final boolean multiSplitForOrdered;
    private final String cacheSchema;
    private final String cacheTable;
    private final int fixedSplitSize;

    @Inject
    public PixelsSplitManager(PixelsConnectorId connectorId, PixelsMetadataProxy metadataProxy, PixelsPrestoConfig config) {
        this.connectorId = requireNonNull(connectorId, "connectorId is null").toString();
        this.metadataProxy = requireNonNull(metadataProxy, "metadataProxy is null");
        String enabled = config.getConfigFactory().getProperty("cache.enabled");
        String multiSplit = config.getConfigFactory().getProperty("multi.split.for.ordered");
        this.fixedSplitSize = Integer.parseInt(config.getConfigFactory().getProperty("fixed.split.size"));
        this.cacheEnabled = Boolean.parseBoolean(enabled);
        this.multiSplitForOrdered = Boolean.parseBoolean(multiSplit);
        this.cacheSchema = config.getConfigFactory().getProperty("cache.schema");
        this.cacheTable = config.getConfigFactory().getProperty("cache.table");
    }

    @Override
    public ConnectorSplitSource getSplits(ConnectorTransactionHandle handle, ConnectorSession session, ConnectorTableLayoutHandle tableLayout,
                                          SplitSchedulingStrategy splitSchedulingStrategy)
    {
        PixelsTableLayoutHandle layoutHandle = (PixelsTableLayoutHandle) tableLayout;
        PixelsTableHandle tableHandle = layoutHandle.getTable();

        TupleDomain<PixelsColumnHandle> constraint = layoutHandle.getConstraint()
                .transform(PixelsColumnHandle.class::cast);
        Set<PixelsColumnHandle> desiredColumns = layoutHandle.getDesiredColumns().stream().map(PixelsColumnHandle.class::cast)
                .collect(toSet());

        String schemaName = tableHandle.getSchemaName();
        String tableName = tableHandle.getTableName();
        Table table;
        Storage storage;
        List<Layout> layouts;
        try
        {
            table = metadataProxy.getTable(schemaName, tableName);
            storage = StorageFactory.Instance().getStorage(table.getStorageScheme());
            layouts = metadataProxy.getDataLayouts(schemaName, tableName);
        }
        catch (MetadataException e)
        {
            throw new PrestoException(PixelsErrorCode.PIXELS_METASTORE_ERROR, e);
        } catch (IOException e)
        {
            throw new PrestoException(PixelsErrorCode.PIXELS_STORAGE_ERROR, e);
        }

        /**
         * Issue #78:
         * Only try to use cache for the cached table.
         * By avoiding cache probing for uncached tables, query performance on
         * uncached tables is improved significantly (10%-20%).
         * this.cacheSchema and this.cacheTable are not null if this.cacheEnabled == true.
         */
        boolean usingCache = false;
        if (this.cacheEnabled)
        {
            if (schemaName.equalsIgnoreCase(this.cacheSchema) &&
                    tableName.equalsIgnoreCase(this.cacheTable))
            {
                usingCache = true;
            }
        }

        List<ConnectorSplit> pixelsSplits = new ArrayList<>();
        for (Layout layout : layouts)
        {
            // get index
            int version = layout.getVersion();
            IndexEntry indexEntry = new IndexEntry(schemaName, tableName);

            Order order = JSON.parseObject(layout.getOrder(), Order.class);
            Splits splits = JSON.parseObject(layout.getSplits(), Splits.class);

            // get split size
            int splitSize;
            if (this.fixedSplitSize > 0)
            {
                splitSize = this.fixedSplitSize;
            }
            else
            {
                ColumnSet columnSet = new ColumnSet();
                for (PixelsColumnHandle column : desiredColumns)
                {
                    columnSet.addColumn(column.getColumnName());
                }

                // log.info("columns to be accessed: " + columnSet.toString());

                Inverted index = (Inverted) IndexFactory.Instance().getIndex(indexEntry);
                if (index == null)
                {
                    logger.debug("index not exist in factory, building index...");
                    index = getInverted(order, splits, indexEntry);
                }
                else
                {
                    int indexVersion = index.getVersion();
                    if (indexVersion < version) {
                        logger.debug("index version is not up to date, updating index...");
                        index = getInverted(order, splits, indexEntry);
                    }
                }

                AccessPattern bestPattern = index.search(columnSet);
                // log.info("bestPattern: " + bestPattern.toString());
                splitSize = bestPattern.getSplitSize();
            }
            logger.debug("using split size: " + splitSize);
            int rowGroupNum = splits.getNumRowGroupInBlock();

            if(usingCache)
            {
                Compact compact = layout.getCompactObject();
                int cacheBorder = compact.getCacheBorder();
                List<String> cacheColumnletOrders = compact.getColumnletOrder().subList(0, cacheBorder);
                String cacheVersion;
                EtcdUtil etcdUtil = EtcdUtil.Instance();
                KeyValue keyValue = etcdUtil.getKeyValue(Constants.CACHE_VERSION_LITERAL);
                if(keyValue != null)
                {
                    // 1. get version
                    cacheVersion = keyValue.getValue().toStringUtf8();
                    logger.debug("cache version: " + cacheVersion);
                    // 2. get files of each node
                    List<KeyValue> nodeFiles = etcdUtil.getKeyValuesByPrefix(Constants.CACHE_LOCATION_LITERAL + cacheVersion);
                    if(nodeFiles.size() > 0)
                    {
                        Map<String, String> fileToNodeMap = new HashMap<>();
                        for (KeyValue kv : nodeFiles)
                        {
                            String node = kv.getKey().toStringUtf8().split("_")[2];
                            String[] files = kv.getValue().toStringUtf8().split(";");
                            for(String file : files)
                            {
                                fileToNodeMap.put(file, node);
                                // log.info("cache location: {file='" + file + "', node='" + node + "'");
                            }
                        }
                        try
                        {
                            // 3. add splits in orderedPath
//                            Balancer orderedBalancer = new Balancer();
                            List<String> orderedPaths = storage.listPaths(layout.getOrderPath());
//                            for (Path path : orderedPaths) {
//                                List<HostAddress> hostAddresses = fsFactory.getBlockLocations(path, 0, Long.MAX_VALUE);
//                                orderedBalancer.put(hostAddresses.get(0), path);
//                            }
//                            orderedBalancer.balance();

                            int numPath = orderedPaths.size();
                            for (int i = 0; i < numPath; ++i)
                            {
                                int firstPath = i;
                                List<String> paths = new ArrayList<>(this.multiSplitForOrdered ? splitSize : 1);
                                if (this.multiSplitForOrdered)
                                {
                                    for (int j = 0; j < splitSize && i < numPath; ++j, ++i)
                                    {
                                        paths.add(orderedPaths.get(i));
                                    }
                                }
                                else
                                {
                                    paths.add(orderedPaths.get(i));
                                }
//                              ImmutableList.Builder<HostAddress> builder = ImmutableList.builder();
//                              builder.add(orderedBalancer.get(orderedPaths.get(firstPath)));

                                List<HostAddress> orderedAddresses = toHostAddresses(
                                        storage.getLocations(orderedPaths.get(firstPath)));

                                PixelsSplit pixelsSplit = new PixelsSplit(connectorId,
                                        tableHandle.getSchemaName(), tableHandle.getTableName(),
                                        table.getStorageScheme(), paths, 0, 1, false, orderedAddresses,
                                        order.getColumnOrder(), new ArrayList<>(0), constraint);
                                // log.debug("Split in orderPath: " + pixelsSplit.toString());
                                pixelsSplits.add(pixelsSplit);
                            }
                            // 4. add splits in compactPath
                            int curFileRGIdx;
                            for (String path : storage.listPaths(layout.getCompactPath()))
                            {
                                curFileRGIdx = 0;
                                while (curFileRGIdx < rowGroupNum)
                                {
                                    String node = fileToNodeMap.get(path);
                                    List<HostAddress> compactAddresses;
                                    if (node == null)
                                    {
                                        compactAddresses = toHostAddresses(storage.getLocations(path));
                                    }
                                    else
                                    {
                                        // this file is cached.
                                        ImmutableList.Builder<HostAddress> builder = ImmutableList.builder();
                                        builder.add(HostAddress.fromString(node));
                                        compactAddresses = builder.build();
                                    }

                                    PixelsSplit pixelsSplit = new PixelsSplit(connectorId,
                                            tableHandle.getSchemaName(), tableHandle.getTableName(),
                                            table.getStorageScheme(), Arrays.asList(path), curFileRGIdx, splitSize,
                                            true, compactAddresses, order.getColumnOrder(),
                                            cacheColumnletOrders, constraint);
                                    pixelsSplits.add(pixelsSplit);
                                    // log.debug("Split in compactPath" + pixelsSplit.toString());
                                    curFileRGIdx += splitSize;
                                }
                            }
                        }
                        catch (IOException e)
                        {
                            throw new PrestoException(PixelsErrorCode.PIXELS_STORAGE_ERROR, e);
                        }
                    }
                    else
                    {
                        logger.error("Get caching files error when version is " + cacheVersion);
                        throw new PrestoException(PixelsErrorCode.PIXELS_CACHE_NODE_FILE_ERROR, new CacheException());
                    }
                }
                else
                {
                    throw new PrestoException(PixelsErrorCode.PIXELS_CACHE_VERSION_ERROR, new CacheException());
                }
            }
            else
            {
                logger.debug("cache is disabled or no cache available on this table");
                List<String> orderedPaths;
//                Balancer orderedBalancer = new Balancer();
                List<String> compactPaths;
//                Balancer compactBalancer = new Balancer();
                try
                {
                    orderedPaths = storage.listPaths(layout.getOrderPath());
//                    for (Path path : orderedPaths)
//                    {
//                        List<HostAddress> addresses = fsFactory.getBlockLocations(path, 0, Long.MAX_VALUE);
//                        orderedBalancer.put(addresses.get(0), path);
//                    }
//                    orderedBalancer.balance();
//                    log.info("ordered files balanced=" + orderedBalancer.isBalanced());

                    compactPaths = storage.listPaths(layout.getCompactPath());
//                    for (Path path : compactPaths)
//                    {
//                        List<HostAddress> addresses = fsFactory.getBlockLocations(path, 0, Long.MAX_VALUE);
//                        compactBalancer.put(addresses.get(0), path);
//                    }
//                    compactBalancer.balance();
//                    log.info("compact files balanced=" + compactBalancer.isBalanced());


                    // add splits in orderedPath
                    int numPath = orderedPaths.size();
                    for (int i = 0; i < numPath; ++i)
                    {
                        int firstPath = i;
                        List<String> paths = new ArrayList<>(this.multiSplitForOrdered ? splitSize : 1);
                        if (this.multiSplitForOrdered)
                        {
                            for (int j = 0; j < splitSize && i < numPath; ++j, ++i)
                            {
                                paths.add(orderedPaths.get(i));
                            }
                        }
                        else
                        {
                            paths.add(orderedPaths.get(i));
                        }
//                              ImmutableList.Builder<HostAddress> builder = ImmutableList.builder();
//                              builder.add(orderedBalancer.get(orderedPaths.get(firstPath)));

                        List<HostAddress> orderedAddresses = toHostAddresses(storage.getLocations(orderedPaths.get(firstPath)));
                        PixelsSplit pixelsSplit = new PixelsSplit(connectorId,
                                tableHandle.getSchemaName(), tableHandle.getTableName(),
                                table.getStorageScheme(), paths, 0, 1, false, orderedAddresses,
                                order.getColumnOrder(), new ArrayList<>(0), constraint);
                        // log.debug("Split in orderPath: " + pixelsSplit.toString());
                        pixelsSplits.add(pixelsSplit);
                    }
                    // add splits in compactPath
                    int curFileRGIdx;
                    for (String path : compactPaths)
                    {
//                      ImmutableList.Builder<HostAddress> builder = ImmutableList.builder();
//                      builder.add(compactBalancer.get(path));
                        // log.info("balanced path:" + compactBalancer.get(path).toString());
//                      List<HostAddress> hostAddresses = builder.build();
                        curFileRGIdx = 0;
                        while (curFileRGIdx < rowGroupNum)
                        {
                            List<HostAddress> compactAddresses = toHostAddresses(storage.getLocations(path));
                            PixelsSplit pixelsSplit = new PixelsSplit(connectorId,
                                    tableHandle.getSchemaName(), tableHandle.getTableName(),
                                    table.getStorageScheme(), Arrays.asList(path), curFileRGIdx, splitSize,
                                    false, compactAddresses, order.getColumnOrder(),
                                    new ArrayList<>(0), constraint);
                            pixelsSplits.add(pixelsSplit);
                            curFileRGIdx += splitSize;
                        }
                    }
                }
                catch (IOException e)
                {
                    throw new PrestoException(PixelsErrorCode.PIXELS_STORAGE_ERROR, e);
                }
            }
        }

        Collections.shuffle(pixelsSplits);

        return new FixedSplitSource(pixelsSplits);
    }

    private List<HostAddress> toHostAddresses(List<Location> locations)
    {
        ImmutableList.Builder<HostAddress> addressBuilder = ImmutableList.builder();
        for (Location location : locations)
        {
            for (String host : location.getHosts())
            {
                addressBuilder.add(HostAddress.fromString(host));
            }
        }
        return addressBuilder.build();
    }

    private Inverted getInverted(Order order, Splits splits, IndexEntry indexEntry) {
        List<String> columnOrder = order.getColumnOrder();
        Inverted index;
        try {
            index = new Inverted(columnOrder, AccessPattern.buildPatterns(columnOrder, splits), splits.getNumRowGroupInBlock());
            IndexFactory.Instance().cacheIndex(indexEntry, index);
        } catch (IOException e) {
            logger.error("getInverted error: " + e.getMessage());
            throw new PrestoException(PixelsErrorCode.PIXELS_INVERTED_INDEX_ERROR, e);
        }
        return index;
    }
}