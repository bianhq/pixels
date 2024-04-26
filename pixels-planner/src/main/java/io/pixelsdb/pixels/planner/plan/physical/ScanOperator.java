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
package io.pixelsdb.pixels.planner.plan.physical;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.ImmutableList;
import io.pixelsdb.pixels.common.task.Task;
import io.pixelsdb.pixels.common.turbo.Output;
import io.pixelsdb.pixels.planner.coordinate.PlanCoordinator;
import io.pixelsdb.pixels.planner.coordinate.StageCoordinator;
import io.pixelsdb.pixels.planner.coordinate.StageDependency;
import io.pixelsdb.pixels.planner.plan.physical.domain.InputSplit;
import io.pixelsdb.pixels.planner.plan.physical.input.ScanInput;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

/**
 * @author hank
 * @create 2024-04-26
 */
public abstract class ScanOperator extends Operator
{
    /**
     * The scan inputs of the scan workers that produce the partial aggregation
     * results. It should be empty if child is not null.
     */
    protected final List<ScanInput> scanInputs;
    /**
     * The outputs of the scan workers.
     */
    protected CompletableFuture<? extends Output>[] scanOutputs = null;

    public ScanOperator(String name, List<ScanInput> scanInputs)
    {
        super(name);
        requireNonNull(scanInputs, "scanInputs is null");
        checkArgument(!scanInputs.isEmpty(), "scanInputs is empty");
        this.scanInputs = ImmutableList.copyOf(scanInputs);
        for (ScanInput scanInput : this.scanInputs)
        {
            scanInput.setOperatorName(name);
        }
    }

    public List<ScanInput> getScanInputs()
    {
        return scanInputs;
    }

    @Override
    public void initPlanCoordinator(PlanCoordinator planCoordinator, int parentStageId, boolean wideDependOnParent)
    {
        int scanStageId = planCoordinator.assignStageId();
        StageDependency scanStageDependency = new StageDependency(scanStageId, parentStageId, wideDependOnParent);
        List<Task> tasks = new ArrayList<>();
        int taskId = 0;
        for (ScanInput scanInput : this.scanInputs)
        {
            List<InputSplit> inputSplits = scanInput.getTableInfo().getInputSplits();
            for (InputSplit inputSplit : inputSplits)
            {
                scanInput.getTableInfo().setInputSplits(ImmutableList.of(inputSplit));
                tasks.add(new Task(taskId++, JSON.toJSONString(scanInput)));
            }
        }
        StageCoordinator scanStageCoordinator = new StageCoordinator(scanStageId, tasks);
        planCoordinator.addStageCoordinator(scanStageCoordinator, scanStageDependency);
    }
}
