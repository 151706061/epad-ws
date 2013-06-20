/*
 * Copyright 2012 The Board of Trustees of the Leland Stanford Junior University.
 * Author: Daniel Rubin, Alan Snyder, Debra Willrett. All rights reserved. Possession
 * or use of this program is subject to the terms and conditions of the Academic
 * Software License Agreement available at:
 *   http://epad.stanford.edu/license/
 */
package edu.stanford.isis.epadws.server.managers.pipeline;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 *
 */
public class MoverProcess extends AbstractPipelineProcess<File>{

    public MoverProcess(ExecutorService moveExec, BlockingQueue<File> moverQueue, BlockingQueue<File> orderQueue){
        super(moveExec,moverQueue,orderQueue);
    }

    @Override
    public Callable<File> getTask(File submitFile) {
        return new MoverTask(submitFile);
    }

    @Override
    public void process()
        throws InterruptedException, ExecutionException
    {
        submitTask();

        forwardCompletedTasks();
    }

    @Override
    public String getProcessName() {
        return "Mover";
    }
}
