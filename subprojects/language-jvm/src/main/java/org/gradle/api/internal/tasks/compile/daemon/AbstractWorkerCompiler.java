/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.compile.daemon;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.UncheckedException;
import org.gradle.language.base.internal.compile.CompileSpec;
import org.gradle.language.base.internal.compile.Compiler;
import org.gradle.workers.ForkMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.internal.DefaultWorkResult;
import org.gradle.workers.internal.WorkerConfigurationInternal;

import java.io.Serializable;

public abstract class AbstractWorkerCompiler<T extends CompileSpec> implements Compiler<T> {
    private final Compiler<T> delegate;
    private final WorkerExecutor workerExecutor;
    protected final ForkMode forkMode;

    public AbstractWorkerCompiler(Compiler<T> delegate, WorkerExecutor workerExecutor, ForkMode forkMode) {
        Preconditions.checkArgument(delegate instanceof Serializable, "Delegate compiler must be Serializable");
        this.delegate = delegate;
        this.workerExecutor = workerExecutor;
        this.forkMode = forkMode;
    }

    protected abstract void applyWorkerConfiguration(T spec, WorkerConfigurationInternal config);

    @Override
    public WorkResult execute(final T spec) {
        workerExecutor.submit(CompilerWorkerRunnable.class, new Action<WorkerConfiguration>() {
            @Override
            public void execute(WorkerConfiguration config) {
                config.setForkMode(forkMode);
                config.setParams((Serializable) delegate, spec);
                applyWorkerConfiguration(spec, (WorkerConfigurationInternal) config);
            }
        });
        try {
            workerExecutor.await();
            return new DefaultWorkResult(true, null);
        } catch (Exception ex) {
            throw UncheckedException.throwAsUncheckedException(ex);
        }
    }

    @VisibleForTesting
    public Compiler<T> getDelegate() {
        return delegate;
    }

    public static class CompilerWorkerRunnable<T extends CompileSpec> implements Runnable {
        private final Compiler<T> compiler;
        private final T compileSpec;

        public CompilerWorkerRunnable(Compiler<T> compiler, T compileSpec) {
            this.compiler = compiler;
            this.compileSpec = compileSpec;
        }

        @Override
        public void run() {
            compiler.execute(compileSpec);
        }
    }
}