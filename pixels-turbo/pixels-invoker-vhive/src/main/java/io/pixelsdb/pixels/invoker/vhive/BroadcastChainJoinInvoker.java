
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
package io.pixelsdb.pixels.invoker.vhive;

import com.alibaba.fastjson.JSON;
import com.google.common.util.concurrent.ListenableFuture;
import io.pixelsdb.pixels.common.turbo.Input;
import io.pixelsdb.pixels.common.turbo.Output;
import io.pixelsdb.pixels.planner.plan.physical.input.BroadcastChainJoinInput;
import io.pixelsdb.pixels.planner.plan.physical.output.JoinOutput;
import io.pixelsdb.pixels.turbo.TurboProto;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.CompletableFuture;

public class BroadcastChainJoinInvoker extends VhiveInvoker
{
    private final Logger log = LogManager.getLogger(BroadcastChainJoinInvoker.class);

    protected BroadcastChainJoinInvoker(String functionName)
    {
        super(functionName);
    }

    @Override
    public Output parseOutput(String outputJson)
    {
        return JSON.parseObject(outputJson, JoinOutput.class);
    }

    @Override
    public CompletableFuture<Output> invoke(Input input)
    {
//        log.info(String.format("invoke BroadcastChainJoinInput: %s", JSON.toJSONString(input, SerializerFeature.PrettyFormat, SerializerFeature.DisableCircularReferenceDetect)));
        ListenableFuture<TurboProto.vHiveWorkerResponse> future = Vhive.Instance().getAsyncClient().broadcastChainJoin((BroadcastChainJoinInput) input);
        return genCompletableFuture(future);
    }
}
