/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.dhcpagent.xenon.service;

import com.vmware.photon.controller.common.xenon.ControlFlags;
import com.vmware.photon.controller.common.xenon.TaskUtils;
import com.vmware.photon.controller.common.xenon.exceptions.XenonRuntimeException;
import com.vmware.photon.controller.dhcpagent.DHCPAgentConfig;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DnsmasqDriver;
import com.vmware.photon.controller.dhcpagent.xenon.helpers.TestEnvironment;
import com.vmware.photon.controller.dhcpagent.xenon.helpers.TestHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.TaskState;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.testng.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Executors;


/**
 * This class implements tests for {@link ReleaseIPService}.
 */
public class ReleaseIPServiceTest {

    private TestHost testHost;
    private ReleaseIPService taskService;

    /**
     * Dummy test case to make IntelliJ recognize this as a test class.
     */
    @Test(enabled = false)
    public void dummy() {
    }

    /**
     * Tests the handleStart method.
     */
    public class HandleStartTest {

        @BeforeClass
        public void setUpClass() throws Throwable {
            testHost = TestHost.create();
        }

        @BeforeMethod
        public void setUpTest() {
            taskService = new ReleaseIPService();
        }

        @AfterMethod
        public void tearDownTest() throws Throwable {
            try {
                testHost.deleteServiceSynchronously();
            } catch (ServiceHost.ServiceNotFoundException e) {
                // Exceptions are expected in the case where a service was not successfully created.
            }
        }

        @AfterClass
        public void tearDownClass() throws Throwable {
            if (testHost != null) {
                TestHost.destroy(testHost);
                testHost = null;
            }
        }

        @Test(dataProvider = "ValidStartStages")
        public void testValidStartStage(TaskState.TaskStage taskStage) throws Throwable {
            ReleaseIPTask startState = buildValidStartState(taskStage);
            Operation startOp = testHost.startServiceSynchronously(taskService, startState);
            assertThat(startOp.getStatusCode(), is(200));

            ReleaseIPTask serviceState = testHost.getServiceState(ReleaseIPTask.class);
            assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
        }

        @DataProvider(name = "ValidStartStages")
        public Object[][] getValidStartStages() {
            return new Object[][]{
                    {TaskState.TaskStage.CREATED},
                    {TaskState.TaskStage.STARTED},
                    {TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.CANCELLED},
            };
        }

        @Test(dataProvider = "TerminalStartStages")
        public void testTerminalStartStage(TaskState.TaskStage taskStage) throws Throwable {
            ReleaseIPTask startState = buildValidStartState(taskStage);
            Operation op = testHost.startServiceSynchronously(taskService, startState);
            assertThat(op.getStatusCode(), is(200));

            ReleaseIPTask serviceState = testHost.getServiceState(ReleaseIPTask.class);
            assertThat(serviceState.taskState.stage, is(taskStage));
        }

        @DataProvider(name = "TerminalStartStages")
        public Object[][] getTerminalStartStages() {
            return new Object[][]{
                    {TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.CANCELLED},
            };
        }
    }

    /**
     * Tests for the handlePatch method.
     */
    public class HandlePatchTest {

        @BeforeClass
        public void setUpClass() throws Throwable {
            testHost = TestHost.create();
        }

        @BeforeMethod
        public void setUpTest() {
            taskService = new ReleaseIPService();
        }

        @AfterMethod
        public void tearDownTest() throws Throwable {
            try {
                testHost.deleteServiceSynchronously();
            } catch (ServiceHost.ServiceNotFoundException e) {
                // Exceptions are expected in the case where a taskService instance was not successfully created.
            }
        }

        @AfterClass
        public void tearDownClass() throws Throwable {
            if (testHost != null) {
                TestHost.destroy(testHost);
                testHost = null;
            }
        }

        @Test(dataProvider = "ValidStageTransitions")
        public void testValidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
                throws Throwable {
            ReleaseIPTask startState = buildValidStartState(startStage);
            Operation op = testHost.startServiceSynchronously(taskService, startState);
            assertThat(op.getStatusCode(), is(200));

            Operation patchOperation = Operation
                    .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
                    .setBody(taskService.buildPatch(patchStage, false, null));

            op = testHost.sendRequestAndWait(patchOperation);
            assertThat(op.getStatusCode(), is(200));

            ReleaseIPTask serviceState = testHost.getServiceState(ReleaseIPTask.class);
            assertThat(serviceState.taskState.stage, is(patchStage));
            assertThat(serviceState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
        }

        @DataProvider(name = "ValidStageTransitions")
        public Object[][] getValidStageTransitions() {
            return new Object[][]{
                    {TaskState.TaskStage.CREATED, TaskState.TaskStage.STARTED},
                    {TaskState.TaskStage.CREATED, TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.CREATED, TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.CREATED, TaskState.TaskStage.CANCELLED},

                    {TaskState.TaskStage.STARTED, TaskState.TaskStage.STARTED},
                    {TaskState.TaskStage.STARTED, TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.STARTED, TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.STARTED, TaskState.TaskStage.CANCELLED},
            };
        }

        @Test(dataProvider = "InvalidStageTransitions", expectedExceptions = XenonRuntimeException.class)
        public void testInvalidStageTransition(TaskState.TaskStage startStage, TaskState.TaskStage patchStage)
                throws Throwable {
            ReleaseIPTask startState = buildValidStartState(startStage);
            Operation op = testHost.startServiceSynchronously(taskService, startState);
            assertThat(op.getStatusCode(), is(200));

            Operation patchOperation = Operation
                    .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
                    .setBody(taskService.buildPatch(patchStage, false, null));

            testHost.sendRequestAndWait(patchOperation);
        }

        @DataProvider(name = "InvalidStageTransitions")
        public Object[][] getInvalidStageTransitions() {
            return new Object[][]{
                    {TaskState.TaskStage.CREATED, TaskState.TaskStage.CREATED},

                    {TaskState.TaskStage.STARTED, TaskState.TaskStage.CREATED},

                    {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CREATED},
                    {TaskState.TaskStage.FINISHED, TaskState.TaskStage.STARTED},
                    {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.FINISHED, TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.FINISHED, TaskState.TaskStage.CANCELLED},

                    {TaskState.TaskStage.FAILED, TaskState.TaskStage.CREATED},
                    {TaskState.TaskStage.FAILED, TaskState.TaskStage.STARTED},
                    {TaskState.TaskStage.FAILED, TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.FAILED, TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.FAILED, TaskState.TaskStage.CANCELLED},

                    {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CREATED},
                    {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.STARTED},
                    {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FINISHED},
                    {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.FAILED},
                    {TaskState.TaskStage.CANCELLED, TaskState.TaskStage.CANCELLED},
            };
        }
    }

    /**
     * End-to-end tests for {@link ReleaseIPService} task.
     */
    public class EndToEndTest {
        private TestEnvironment testEnvironment;
        private DnsmasqDriver dnsmasqDriver;

        @Mock
        private DHCPAgentConfig config;

        private ListeningExecutorService listeningExecutorService;

        @BeforeClass
        public void setUpClass() {
            try {
                String command = String.format("chmod +x %s",
                        ReleaseIPServiceTest.class.getResource("/scripts/release-ip.sh").getPath());
                Runtime.getRuntime().exec(command);
                command = String.format("chmod +x %s",
                        ReleaseIPServiceTest.class.getResource("/scripts/dhcp-status.sh").getPath());
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                fail(String.format("Failed with IOException: %s", e.toString()));
            }

            dnsmasqDriver = new DnsmasqDriver(
                    ReleaseIPServiceTest.class.getResource("/scripts/release-ip.sh").getPath(),
                    ReleaseIPServiceTest.class.getResource("/scripts/dhcp-status.sh").getPath());
        }

        @BeforeMethod
        public void setUpTest() throws Throwable {
            MockitoAnnotations.initMocks(this);
            listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
            testEnvironment = TestEnvironment.create(dnsmasqDriver, 1, listeningExecutorService);
        }

        @AfterMethod
        public void tearDownTest() throws Throwable {
            if (testEnvironment != null) {
                testEnvironment.stop();
                testEnvironment = null;
            }
        }

        @AfterClass
        public void tearDownClass() throws Throwable {
            listeningExecutorService.shutdown();
        }

        /**
         * Test release IP success.
         */
        @Test
        public void testReleaseIPSuccess() throws Throwable {
            setupDHCPConfig("none");

            ReleaseIPTask releaseIPTask = new ReleaseIPTask();
            releaseIPTask.networkInterface = "VMLAN";
            releaseIPTask.ipAddress = "192.0.0.1";
            releaseIPTask.macAddress = "01:23:45:67:89:ab";
            releaseIPTask.taskState = new TaskState();
            releaseIPTask.taskState.stage = TaskState.TaskStage.CREATED;
            releaseIPTask.taskState.isDirect = true;

            ReleaseIPTask finalState = testEnvironment.callServiceAndWaitForState(
                    ReleaseIPService.FACTORY_LINK,
                    releaseIPTask,
                    ReleaseIPTask.class,
                    (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

            assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
            assertThat(finalState.response.exitCode, is(0));
            assertThat(finalState.response.stdError,  isEmptyOrNullString());
        }

        /**
         * Test release IP failure.
         */
        @Test
        public void testReleaseIPFailure() throws Throwable {
            setupDHCPConfig("error");

            ReleaseIPTask releaseIPTask = new ReleaseIPTask();
            releaseIPTask.networkInterface = "VMLAN";
            releaseIPTask.ipAddress = "192.0.0.1";
            releaseIPTask.macAddress = "01:23:45:67:89:ab";
            releaseIPTask.taskState = new TaskState();
            releaseIPTask.taskState.stage = TaskState.TaskStage.CREATED;
            releaseIPTask.taskState.isDirect = true;

            ReleaseIPTask finalState = testEnvironment.callServiceAndWaitForState(
                    ReleaseIPService.FACTORY_LINK,
                    releaseIPTask,
                    ReleaseIPTask.class,
                    (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

            assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
            assertThat(finalState.response.exitCode, is(113));
            assertThat(finalState.response.stdError,  is("error"));
        }

        /**
         * Test task completes before release IP is completed.
         */
        @Test
        public void testReleaseIPNotDirect() throws Throwable {
            setupDHCPConfig("none");

            ReleaseIPTask releaseIPTask = new ReleaseIPTask();
            releaseIPTask.networkInterface = "VMLAN";
            releaseIPTask.ipAddress = "192.0.0.1";
            releaseIPTask.macAddress = "01:23:45:67:89:ab";
            releaseIPTask.taskState = new TaskState();
            releaseIPTask.taskState.stage = TaskState.TaskStage.CREATED;
            releaseIPTask.taskState.isDirect = false;

            ReleaseIPTask finalState = testEnvironment.callServiceAndWaitForState(
                    ReleaseIPService.FACTORY_LINK,
                    releaseIPTask,
                    ReleaseIPTask.class,
                    (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

            assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
            assertThat(finalState.response.exitCode, is(0));
            assertThat(finalState.response.stdError,  isEmptyOrNullString());
        }
    }

    private ReleaseIPTask buildValidStartState(TaskState.TaskStage stage) {
        ReleaseIPTask releaseIPTask = new ReleaseIPTask();
        releaseIPTask.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
        releaseIPTask.networkInterface = "VMLAN";
        releaseIPTask.ipAddress = "192.0.0.1";
        releaseIPTask.macAddress = "01:23:45:67:89:ab";
        if (stage != null) {
            releaseIPTask.taskState = new TaskState();
            releaseIPTask.taskState.stage = stage;
            releaseIPTask.taskState.isDirect = true;
        }

        return releaseIPTask;
    }

    private void setupDHCPConfig(String inputConfig) {
        try {
            PrintWriter writer = new PrintWriter(
                    ReleaseIPServiceTest.class.getResource("/scripts/dhcpServerTestConfig").getPath());
            writer.println(inputConfig);
            writer.close();
        } catch (FileNotFoundException e) {
            fail(String.format("Failed with file not found exception: %s", e.toString()));
        }
    }
}
