/*
 * Copyright 2019 PixelsDB.
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
package io.pixelsdb.pixels.daemon.metadata;

import io.grpc.stub.StreamObserver;
import io.pixelsdb.pixels.daemon.MetadataProto;
import io.pixelsdb.pixels.daemon.MetadataServiceGrpc;
import io.pixelsdb.pixels.daemon.metadata.dao.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static io.pixelsdb.pixels.common.error.ErrorCode.*;

/**
 * Created at: 19-4-16
 * Author: hank
 */
public class MetadataServiceImpl extends MetadataServiceGrpc.MetadataServiceImplBase
{
    private static Logger log = LogManager.getLogger(MetadataServiceImpl.class);

    private SchemaDao schemaDao = DaoFactory.Instance().getSchemaDao("rdb");
    private TableDao tableDao = DaoFactory.Instance().getTableDao("rdb");
    private ColumnDao columnDao = DaoFactory.Instance().getColumnDao("rdb");
    private LayoutDao layoutDao = DaoFactory.Instance().getLayoutDao("rdb");

    public MetadataServiceImpl () { }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getSchemas(MetadataProto.GetSchemasRequest request, StreamObserver<MetadataProto.GetSchemasResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());
        List<MetadataProto.Schema> schemas = this.schemaDao.getAll();
        MetadataProto.ResponseHeader header;
        MetadataProto.GetSchemasResponse response;
        if (schemas == null || schemas.isEmpty())
        {
            header = headerBuilder.setErrorCode(METADATA_SCHEMA_NOT_FOUND).setErrorMsg("schema not found").build();
            response = MetadataProto.GetSchemasResponse.newBuilder().setHeader(header).build();
        }
        else
        {
            header = headerBuilder.setErrorCode(0).setErrorMsg("").build();
            response = MetadataProto.GetSchemasResponse.newBuilder().setHeader(header)
                .addAllSchemas(schemas).build();
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getTables(MetadataProto.GetTablesRequest request, StreamObserver<MetadataProto.GetTablesResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());
        MetadataProto.ResponseHeader header;
        MetadataProto.GetTablesResponse response;
        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        List<MetadataProto.Table> tables;

        if(schema != null)
        {
            tables = tableDao.getBySchema(schema);
            /**
             * Issue #85:
             *  tables.isEmpty() is normal for empty schema.
             */
            if (tables == null)
            {
                header = headerBuilder.setErrorCode(METADATA_TABLE_NOT_FOUND)
                        .setErrorMsg("metadata server failed to get tables").build();
                response = MetadataProto.GetTablesResponse.newBuilder()
                        .setHeader(header).build();
            }
            else
            {
                header = headerBuilder.setErrorCode(0).setErrorMsg("").build();
                response = MetadataProto.GetTablesResponse.newBuilder()
                        .setHeader(header)
                        .addAllTables(tables).build();
            }
        }
        else
        {
            header = headerBuilder.setErrorCode(METADATA_SCHEMA_NOT_FOUND).setErrorMsg("schema not found").build();
            response = MetadataProto.GetTablesResponse.newBuilder().setHeader(header).build();
        }
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getLayouts(MetadataProto.GetLayoutsRequest request, StreamObserver<MetadataProto.GetLayoutsResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());
        MetadataProto.GetLayoutsResponse response;
        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        List<MetadataProto.Layout> layouts = null;
        if(schema != null)
        {
            MetadataProto.Table table = tableDao.getByNameAndSchema(request.getTableName(), schema);
            if (table != null)
            {
                layouts = layoutDao.getByTable(table, -1,
                        MetadataProto.GetLayoutRequest.PermissionRange.READABLE); // version < 0 means get all versions
                if (layouts == null || layouts.isEmpty())
                {
                    headerBuilder.setErrorCode(METADATA_LAYOUT_NOT_FOUND).setErrorMsg("layout not found");
                }
            }
            else
            {
                headerBuilder.setErrorCode(METADATA_TABLE_NOT_FOUND).setErrorMsg("table not found");
            }
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_SCHEMA_NOT_FOUND).setErrorMsg("schema not found");
        }
        if(layouts != null && layouts.isEmpty() == false)
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
            response = MetadataProto.GetLayoutsResponse.newBuilder()
                    .setHeader(headerBuilder.build())
                    .addAllLayouts(layouts).build();
        }
        else
        {
            response = MetadataProto.GetLayoutsResponse.newBuilder()
                    .setHeader(headerBuilder.build()).build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getLayout(MetadataProto.GetLayoutRequest request, StreamObserver<MetadataProto.GetLayoutResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());
        MetadataProto.GetLayoutResponse response;
        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        MetadataProto.Layout layout = null;
        if(schema != null)
        {
            MetadataProto.Table table = tableDao.getByNameAndSchema(request.getTableName(), schema);
            if (table != null)
            {
                if (request.getVersion() < 0)
                {
                    layout = layoutDao.getLatestByTable(table, request.getPermissionRange());
                    if (layout == null)
                    {
                        headerBuilder.setErrorCode(METADATA_LAYOUT_NOT_FOUND).setErrorMsg("no layout exists");
                    }
                }
                else
                {
                    List<MetadataProto.Layout> layouts = layoutDao.getByTable(table, request.getVersion(),
                            request.getPermissionRange());
                    if (layouts == null || layouts.isEmpty())
                    {
                        headerBuilder.setErrorCode(METADATA_LAYOUT_NOT_FOUND).setErrorMsg("layout version not found");
                    } else if (layouts.size() != 1)
                    {
                        headerBuilder.setErrorCode(METADATA_LAYOUT_DUPLICATED).setErrorMsg("duplicate layouts found");
                    } else
                    {
                        layout = layouts.get(0);
                    }
                }
            }
            else
            {
                headerBuilder.setErrorCode(METADATA_TABLE_NOT_FOUND).setErrorMsg("table not found");
            }
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_SCHEMA_NOT_FOUND).setErrorMsg("schema not found");
        }
        if(layout != null)
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
            response = MetadataProto.GetLayoutResponse.newBuilder()
                    .setHeader(headerBuilder.build())
                    .setLayout(layout).build();
        }
        else
        {
            response = MetadataProto.GetLayoutResponse.newBuilder()
                    .setHeader(headerBuilder.build()).build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void addLayout (MetadataProto.AddLayoutRequest request, StreamObserver<MetadataProto.AddLayoutResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        if (layoutDao.save(request.getLayout()))
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_ADD_LAYOUT_FAILED).setErrorMsg("add layout failed");
        }

        MetadataProto.AddLayoutResponse response = MetadataProto.AddLayoutResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateLayout (MetadataProto.UpdateLayoutRequest request, StreamObserver<MetadataProto.UpdateLayoutResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        if (layoutDao.update(request.getLayout()))
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_UPDATE_LAYOUT_FAILED).setErrorMsg("layout not found");
        }

        MetadataProto.UpdateLayoutResponse response = MetadataProto.UpdateLayoutResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getColumns(MetadataProto.GetColumnsRequest request, StreamObserver<MetadataProto.GetColumnsResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());
        MetadataProto.GetColumnsResponse response;
        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        List<MetadataProto.Column> columns = null;
        if(schema != null)
        {
            MetadataProto.Table table = tableDao.getByNameAndSchema(request.getTableName(), schema);
            if (table != null)
            {
                columns = columnDao.getByTable(table);
            }
            else
            {
                headerBuilder.setErrorCode(METADATA_TABLE_NOT_FOUND).setErrorMsg("table not found");
            }
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_SCHEMA_NOT_FOUND).setErrorMsg("schema not found");
        }
        if(columns != null && columns.isEmpty() == false)
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
            response = MetadataProto.GetColumnsResponse.newBuilder()
                    .setHeader(headerBuilder.build())
                    .addAllColumns(columns).build();
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_COLUMN_NOT_FOUND).setErrorMsg("column not found");
            response = MetadataProto.GetColumnsResponse.newBuilder()
                    .setHeader(headerBuilder.build())
                    .addAllColumns(columns).build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void updateColumn (MetadataProto.UpdateColumnRequest request, StreamObserver<MetadataProto.UpdateColumnResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        boolean res = columnDao.update(request.getColumn());

        if (res)
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_UPDATE_COUMN_FAILED).setErrorMsg("column not found");
        }

        MetadataProto.UpdateColumnResponse response = MetadataProto.UpdateColumnResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void createSchema(MetadataProto.CreateSchemaRequest request, StreamObserver<MetadataProto.CreateSchemaResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        MetadataProto.Schema schema= MetadataProto.Schema.newBuilder()
        .setName(request.getSchemaName())
        .setDesc(request.getSchemaDesc()).build();
        if (schemaDao.exists(schema))
        {
            headerBuilder.setErrorCode(METADATA_SCHEMA_EXIST).setErrorMsg("schema already exist");
        }
        else
        {
            if (schemaDao.insert(schema))
            {
                headerBuilder.setErrorCode(0).setErrorMsg("");
            }
            else
            {
                headerBuilder.setErrorCode(METADATA_ADD_SCHEMA_FAILED).setErrorMsg("failed to add schema");
            }
        }
        MetadataProto.CreateSchemaResponse response = MetadataProto.CreateSchemaResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void dropSchema(MetadataProto.DropSchemaRequest request, StreamObserver<MetadataProto.DropSchemaResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        if (schemaDao.deleteByName(request.getSchemaName()))
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_DELETE_SCHEMA_FAILED).setErrorMsg("failed to delete schema");
        }
        MetadataProto.DropSchemaResponse response = MetadataProto.DropSchemaResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void createTable(MetadataProto.CreateTableRequest request, StreamObserver<MetadataProto.CreateTableResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        MetadataProto.Table table = MetadataProto.Table.newBuilder()
        .setName(request.getTableName())
        .setType("user")
        .setSchemaId(schema.getId()).build();
        if (tableDao.exists(table))
        {
            headerBuilder.setErrorCode(METADATA_TABLE_EXIST).setErrorMsg("table already exist");
        }
        else
        {
            boolean res = tableDao.insert(table);
            List<MetadataProto.Column> columns = request.getColumnsList();
            // to get table id from database.
            table = tableDao.getByNameAndSchema(table.getName(), schema);
            if (res && columns.size() == columnDao.insertBatch(table, columns))
            {
                headerBuilder.setErrorCode(0).setErrorMsg("");
            }
            else
            {
                tableDao.deleteByNameAndSchema(table.getName(), schema);
                headerBuilder.setErrorCode(METADATA_ADD_COUMNS_FAILED).setErrorMsg("failed to add columns");
            }
        }

        MetadataProto.CreateTableResponse response = MetadataProto.CreateTableResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void dropTable(MetadataProto.DropTableRequest request, StreamObserver<MetadataProto.DropTableResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        if (tableDao.deleteByNameAndSchema(request.getTableName(), schema))
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
        }
        else
        {
            headerBuilder.setErrorCode(METADATA_DELETE_TABLE_FAILED).setErrorMsg("failed to delete table");
        }
        MetadataProto.DropTableResponse response = MetadataProto.DropTableResponse.newBuilder()
                .setHeader(headerBuilder.build()).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void existTable(MetadataProto.ExistTableRequest request, StreamObserver<MetadataProto.ExistTableResponse> responseObserver)
    {
        MetadataProto.ResponseHeader.Builder headerBuilder = MetadataProto.ResponseHeader.newBuilder()
                .setToken(request.getHeader().getToken());

        MetadataProto.Schema schema = schemaDao.getByName(request.getSchemaName());
        MetadataProto.Table table = MetadataProto.Table.newBuilder()
        .setId(-1)
        .setName(request.getTableName())
        .setSchemaId(schema.getId()).build();
        MetadataProto.ExistTableResponse response;
        if (tableDao.exists(table))
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
            response = MetadataProto.ExistTableResponse.newBuilder()
                    .setExists(true).setHeader(headerBuilder.build()).build();
        }
        else
        {
            headerBuilder.setErrorCode(0).setErrorMsg("");
            response = MetadataProto.ExistTableResponse.newBuilder()
                    .setExists(false).setHeader(headerBuilder.build()).build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
