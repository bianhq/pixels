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
package io.pixelsdb.pixels.daemon.rest;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.pixelsdb.pixels.common.exception.InvalidArgumentException;
import io.pixelsdb.pixels.common.exception.MetadataException;
import io.pixelsdb.pixels.common.metadata.MetadataService;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import io.pixelsdb.pixels.daemon.rest.request.GetColumns;
import io.pixelsdb.pixels.daemon.rest.request.GetSchemas;
import io.pixelsdb.pixels.daemon.rest.request.GetTables;
import io.pixelsdb.pixels.daemon.rest.request.GetViews;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;

import static java.util.Objects.requireNonNull;

/**
 * The request handler for Pixels REST server.
 * Created at: 3/13/23
 * Author: hank
 */
public class RestServerHandler extends SimpleChannelInboundHandler<FullHttpRequest>
{
    private static Logger log = LogManager.getLogger(RestServerHandler.class);
    private static final String URI_METADATA_GET_SCHEMAS = "/metadata/get_schemas";
    private static final String URI_METADATA_GET_TABLES = "/metadata/get_tables";
    private static final String URI_METADATA_GET_VIEWS = "/metadata/get_views";
    private static final String URI_METADATA_GET_COLUMNS = "/metadata/get_columns";

    private static final MetadataService metadataService;

    static
    {
        String host = ConfigFactory.Instance().getProperty("metadata.server.host");
        int port = Integer.parseInt(ConfigFactory.Instance().getProperty("metadata.server.port"));
        metadataService = new MetadataService(host, port);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx)
    {
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request)
    {
        ByteBuf responseContent;
        try
        {
            responseContent = processRequest(request.uri(), request.content());
        } catch (Throwable e)
        {
            log.error("failed to process request content", e);
            return;
        }

        FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(),
                HttpResponseStatus.OK, responseContent);

        boolean keepAlive = HttpUtil.isKeepAlive(request);

        ByteBuf buffer = request.content();
        System.out.println(request.uri());
        System.out.println(buffer.toString(StandardCharsets.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        if (keepAlive)
        {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        else
        {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        }

        ChannelFuture f = ctx.writeAndFlush(response);

        if (!keepAlive)
        {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
    {
        cause.printStackTrace();
        ctx.close();
    }

    private ByteBuf processRequest(String uri, ByteBuf requestContent)
            throws InvalidArgumentException, MetadataException
    {
        requireNonNull(uri, "uri is null");
        requireNonNull(requestContent, "requestContent is null");
        String content = requestContent.toString(StandardCharsets.UTF_8);
        if (uri.startsWith(URI_METADATA_GET_SCHEMAS))
        {
            GetSchemas request = JSON.parseObject(content, GetSchemas.class);
            requireNonNull(request, "failed to parse request content");
            return Unpooled.wrappedBuffer(JSON.toJSONString(
                    metadataService.getSchemas()).getBytes(StandardCharsets.UTF_8));
        } else if (uri.startsWith(URI_METADATA_GET_TABLES))
        {
            GetTables request = JSON.parseObject(content, GetTables.class);
            requireNonNull(request, "failed to parse request content");
            return Unpooled.wrappedBuffer(JSON.toJSONString(
                    metadataService.getTables(request.getSchemaName())).getBytes(StandardCharsets.UTF_8));
        } else if (uri.startsWith(URI_METADATA_GET_VIEWS))
        {
            GetViews request = JSON.parseObject(content, GetViews.class);
            requireNonNull(request, "failed to parse request content");
            return Unpooled.wrappedBuffer(JSON.toJSONString(
                    metadataService.getViews(request.getSchemaName())).getBytes(StandardCharsets.UTF_8));
        } else if (uri.startsWith(URI_METADATA_GET_COLUMNS))
        {
            GetColumns request = JSON.parseObject(content, GetColumns.class);
            requireNonNull(request, "failed to parse request content");
            return Unpooled.wrappedBuffer(JSON.toJSONString(
                    metadataService.getColumns(request.getSchemaName(), request.getTableName(), false))
                    .getBytes(StandardCharsets.UTF_8));
        } else
        {
            throw new InvalidArgumentException("invalid uri: " + uri);
        }
    }
}
