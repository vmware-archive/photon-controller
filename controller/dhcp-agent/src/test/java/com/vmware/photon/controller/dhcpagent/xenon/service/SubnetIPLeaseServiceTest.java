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
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.Constants;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DHCPDriver;
import com.vmware.photon.controller.dhcpagent.dhcpdrivers.DnsmasqDriver;
import com.vmware.photon.controller.dhcpagent.xenon.DHCPAgentXenonHost;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.concurrent.Executors;


/**
 * This class implements tests for {@link SubnetIPLeaseService}.
 */
public class SubnetIPLeaseServiceTest {

    private TestHost testHost;
    private SubnetIPLeaseService taskService;
    private final String ipAddress = "192.168.0.2";
    private final String macAddress = "08:00:27:d8:7d:8e";
    private final String subnetId = "subnet1";

    /**
     * Dummy test case to make IntelliJ recognize this as a test class.
     */
    @Test
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
            taskService = new SubnetIPLeaseService();
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
            SubnetIPLeaseTask startState = buildValidState(taskStage, true,
                    SubnetIPLeaseTask.SubnetOperation.UPDATE);
            Operation startOp = testHost.startServiceSynchronously(taskService, startState);
            assertThat(startOp.getStatusCode(), is(Operation.STATUS_CODE_OK));
            assertThat(startState.controlFlags, is(ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED));
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
            SubnetIPLeaseTask startState = buildValidState(taskStage, true,
                    SubnetIPLeaseTask.SubnetOperation.UPDATE);
            Operation op = testHost.startServiceSynchronously(taskService, startState);
            assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

            SubnetIPLeaseTask serviceState = testHost.getServiceState(SubnetIPLeaseTask.class);
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
            taskService = new SubnetIPLeaseService();
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
            SubnetIPLeaseTask startState = buildValidState(startStage, true,
                    SubnetIPLeaseTask.SubnetOperation.UPDATE);
            Operation op = testHost.startServiceSynchronously(taskService, startState);
            assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

            Operation patchOperation = Operation
                    .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
                    .setBody(taskService.buildPatch(patchStage, null));

            op = testHost.sendRequestAndWait(patchOperation);
            assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

            SubnetIPLeaseTask serviceState = testHost.getServiceState(SubnetIPLeaseTask.class);
            assertThat(serviceState.taskState.stage, is(patchStage));
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
            SubnetIPLeaseTask startState = buildValidState(startStage, true,
                    SubnetIPLeaseTask.SubnetOperation.UPDATE);
            Operation op = testHost.startServiceSynchronously(taskService, startState);
            assertThat(op.getStatusCode(), is(Operation.STATUS_CODE_OK));

            Operation patchOperation = Operation
                    .createPatch(UriUtils.buildUri(testHost, TestHost.SERVICE_URI))
                    .setBody(taskService.buildPatch(patchStage, null));

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
     * End-to-end tests for {@link SubnetIPLeaseService} task.
     */
    public class EndToEndTest {
        private TestEnvironment testEnvironment;
        private DnsmasqDriver dnsmasqDriver;
        private DHCPDriver dhcpDriver;
        private DHCPAgentXenonHost dhcpAgentXenonHost;

        @Mock
        private DHCPAgentConfig config;

        private ListeningExecutorService listeningExecutorService;


        @BeforeMethod
        public void setUpTest() throws Throwable {
            MockitoAnnotations.initMocks(this);
            listeningExecutorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(1));
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

        public void setUpEnvironment(String hostDirPath) throws Throwable {
            dnsmasqDriver = mock(DnsmasqDriver.class);
            testEnvironment = TestEnvironment.create(dnsmasqDriver, 1, listeningExecutorService);
        }

        public void setUpEnvironment() throws Throwable {
            setUpEnvironment(SubnetIPLeaseServiceTest.class.getResource("/hosts").getPath());
        }

        /**
         * Test subnet IP lease update success.
         */
        @Test
        public void testSubnetLeaseIPSuccess() throws Throwable {
            setUpEnvironment();
            doReturn(true).when(dnsmasqDriver).reload();
            SubnetIPLeaseTask subnetIPLeaseTask = buildValidState(TaskState.TaskStage.CREATED, false,
                    SubnetIPLeaseTask.SubnetOperation.UPDATE);

            SubnetIPLeaseTask finalState = testEnvironment.callServiceAndWaitForState(
                    SubnetIPLeaseService.FACTORY_LINK,
                    subnetIPLeaseTask,
                    SubnetIPLeaseTask.class,
                    (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

            assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FINISHED));
        }

        /**
         * Test subnet IP lease failure.
         */
        @Test
        public void testSubnetLeaseIPFailure() throws Throwable {
            setUpEnvironment(Constants.DNSMASQ_HOST_DIR_PATH);

            SubnetIPLeaseTask subnetIPLeaseTask = buildValidState(TaskState.TaskStage.CREATED, false,
                    SubnetIPLeaseTask.SubnetOperation.UPDATE);

            SubnetIPLeaseTask finalState = testEnvironment.callServiceAndWaitForState(
                    SubnetIPLeaseService.FACTORY_LINK,
                    subnetIPLeaseTask,
                    SubnetIPLeaseTask.class,
                    (state) -> TaskUtils.finalTaskStages.contains(state.taskState.stage));

            assertThat(finalState.taskState.stage, is(TaskState.TaskStage.FAILED));
        }
    }

    private SubnetIPLeaseTask buildValidState(TaskState.TaskStage stage, boolean isProcessingDisabled,
                                              SubnetIPLeaseTask.SubnetOperation subnetOperation) {
        SubnetIPLeaseTask subnetIPLeaseTask = new SubnetIPLeaseTask();
        subnetIPLeaseTask.subnetIPLease = new SubnetIPLeaseTask.SubnetIPLease();
        subnetIPLeaseTask.subnetIPLease.subnetId = subnetId;
        subnetIPLeaseTask.subnetIPLease.subnetOperation = subnetOperation;
        subnetIPLeaseTask.subnetIPLease.version = 1L;

        if (subnetOperation == SubnetIPLeaseTask.SubnetOperation.UPDATE) {
            subnetIPLeaseTask.subnetIPLease.ipToMACAddressMap = new HashMap<>();
            subnetIPLeaseTask.subnetIPLease.ipToMACAddressMap.put(ipAddress, macAddress);
        }

        if (stage != null) {
            subnetIPLeaseTask.taskState = new TaskState();
            subnetIPLeaseTask.taskState.stage = stage;
        }

        if (isProcessingDisabled) {
            subnetIPLeaseTask.controlFlags = ControlFlags.CONTROL_FLAG_OPERATION_PROCESSING_DISABLED;
        }

        return subnetIPLeaseTask;
    }
}
