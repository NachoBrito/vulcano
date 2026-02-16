/*
 *    Copyright 2025 Nacho Brito
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package es.nachobrito.vulcanodb.core.store.axon.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * @author nacho
 */
public class ForkJoinPoolProvider implements ExecutorProvider {

    static final ForkJoinPoolProvider INSTANCE = new ForkJoinPoolProvider();


    private ExecutorService executor;

    protected ForkJoinPoolProvider() {
        executor = new ForkJoinPool(getConcurrency());
    }

    protected int getConcurrency() {
        int cores = Runtime.getRuntime().availableProcessors();
        return Math.max(cores / 4, 2);
    }

    @Override
    public ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = new ForkJoinPool(getConcurrency());
        }
        return executor;
    }
}
