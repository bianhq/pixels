/*
 * Copyright 2024 PixelsDB.
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
package io.pixelsdb.pixels.scaling.general;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.pixelsdb.pixels.common.utils.ConfigFactory;
import io.pixelsdb.pixels.turbo.ScalingServiceGrpc;
import io.pixelsdb.pixels.turbo.TurboProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ScalingMetricsClient
{
    private final ManagedChannel managerChannel;
    private final ScalingServiceGrpc.ScalingServiceStub ScalingServiceStub;
    private String ScalingName;
    private static final Logger log = LogManager.getLogger(ScalingMetricsClient.class);


    public ScalingMetricsClient(int port)
    {
        String host = ConfigFactory.Instance().getProperty("scaling.metrics.server.host");
        managerChannel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        ScalingServiceStub = ScalingServiceGrpc.newStub(managerChannel);
        ScalingName = "default";
    }

    public ScalingMetricsClient(String name, int port)
    {
        this(port);
        ScalingName = name;
    }

    public void reportMetric(int concurrency)
    {
        StreamObserver<TurboProto.CollectMetricsResponse> responseStreamObserver = new StreamObserver<TurboProto.CollectMetricsResponse>()
        {
            @Override
            public void onNext(TurboProto.CollectMetricsResponse CollectMetricsResponse)
            {
                log.info("Received response");
            }

            @Override
            public void onError(Throwable throwable)
            {
                log.error("Error: " + throwable.getMessage());
            }

            @Override
            public void onCompleted()
            {
                log.info("Stream completed");

            }
        };
        StreamObserver<TurboProto.CollectMetricsRequest> requestObserver = ScalingServiceStub.collectMetrics(responseStreamObserver);
        TurboProto.CollectMetricsRequest request = TurboProto.CollectMetricsRequest.newBuilder()
                .setQueryConcurrency(concurrency)
                .build();
        requestObserver.onNext(request);
        log.info("Send metric: " + request.getQueryConcurrency());
    }

    public void stopReportMetric()
    {
        if (managerChannel != null)
        {
            managerChannel.shutdown();
        }
    }
}
