/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.performance.experiment.maven

import org.gradle.performance.AbstractGradleVsMavenPerformanceTest
import org.gradle.performance.mutator.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

import static org.gradle.performance.generator.JavaTestProject.MEDIUM_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.MEDIUM_MONOLITHIC_JAVA_PROJECT

/**
 * Performance tests aimed at comparing the performance of Gradle for compiling and executing test suites, making
 * sure we are always faster than Maven.
 */
class JavaTestGradleVsMavenPerformanceTest extends AbstractGradleVsMavenPerformanceTest {

    @Unroll
    def "#gradleTasks on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.args = testProject.parallel ? ['-T', testProject.parallelForks] : []
        runner.gradleTasks = gradleTasks
        runner.equivalentMavenTasks = equivalentMavenTasks

        when:
        def results = runner.run()

        then:
        if (enableComparison) {
            results.assertComparesWithMaven()
        } else {
            true
        }

        where:
        testProject                    | gradleTasks       | equivalentMavenTasks | enableComparison
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'clean assemble'  | 'clean package'      | true
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'cleanTest test'  | 'test'               | true

        MEDIUM_JAVA_MULTI_PROJECT      | 'clean assemble'  | 'clean package'      | true
        MEDIUM_JAVA_MULTI_PROJECT      | 'cleanTest test'  | 'test'               | false // TODO accepted difference: this scenario spends most of the time on forking test workers,
                                                                                          // which is more expensive in Gradle at the moment
    }

    @Unroll
    def "#gradleTasks for non-abi change on #testProject (Gradle vs Maven)"() {
        given:
        runner.testGroup = "Gradle vs Maven test build using Java plugin"
        runner.testProject = testProject
        runner.jvmOpts = ["-Xms${testProject.daemonMemory}", "-Xmx${testProject.daemonMemory}"]
        runner.args = testProject.parallel ? ['-T', testProject.parallelForks] : []
        runner.gradleTasks = gradleTasks
        runner.equivalentMavenTasks = equivalentMavenTasks
        runner.buildExperimentListener = new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange)

        when:
        def results = runner.run()

        then:
        results.assertComparesWithMaven()

        where:
        testProject                    | gradleTasks | equivalentMavenTasks | fileToChange
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'test'      | 'test'               | "src/main/java/org/gradle/test/performance/mediummonolithicjavaproject/p0/Production0.java"
        MEDIUM_MONOLITHIC_JAVA_PROJECT | 'assemble'  | 'package'            | "src/main/java/org/gradle/test/performance/mediummonolithicjavaproject/p0/Production0.java"

        MEDIUM_JAVA_MULTI_PROJECT      | 'test'      | 'test'               | "project0/src/main/java/org/gradle/test/performance/mediumjavamultiproject/project0/p0/Production0.java"
        MEDIUM_JAVA_MULTI_PROJECT      | 'assemble'  | 'package'            | "project0/src/main/java/org/gradle/test/performance/mediumjavamultiproject/project0/p0/Production0.java"
    }
}
