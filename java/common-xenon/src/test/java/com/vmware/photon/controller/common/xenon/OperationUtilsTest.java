/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.common.xenon;

import com.vmware.xenon.common.Operation;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.net.URI;

/**
 * Tests {@link OperationUtils}.
 */
public class OperationUtilsTest {

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests the isCompleted method.
   */
  public class IsCompletedTest {

    Operation op;

    @BeforeMethod
    private void setUp() throws Throwable {
      op = Operation
          .createGet(new URI("http://test"))
          .setCompletion(new Operation.CompletionHandler() {
            @Override
            public void handle(Operation operation, Throwable throwable) {
            }
          });
    }

    /**
     * Tests that the method returns a list of local host ip addresses.
     */
    @Test
    public void testGetLocalHostIpAddresses() {
      assertThat(OperationUtils.getLocalHostIpAddresses().size(), greaterThan(0));
    }

    /**
     * Tests that an operation with a completion handler that has not been completed yet
     * returns false.
     *
     * @throws Throwable
     */
    @Test
    public void testOperationNotCompleted() throws Throwable {
      assertFalse(OperationUtils.isCompleted(op));
    }

    /**
     * Tests that an operation with a completion handler that has already been completed
     * returns true.
     *
     * @throws Throwable
     */
    @Test
    public void testOperationCompleted() throws Throwable {
      op.complete();
      assertTrue(OperationUtils.isCompleted(op));
    }

    /**
     * Tests that an operation with a completion handler that has already been failed
     * returns true.
     *
     * @throws Throwable
     */
    @Test
    public void testOperationFailed() throws Throwable {
      op.fail(new Exception());
      assertTrue(OperationUtils.isCompleted(op));
    }

    /**
     * Tests that an operation without a completion handler returns true.
     *
     * @throws Throwable
     */
    @Test
    public void testOperationWithNoCompletionHandler() throws Throwable {
      op.setCompletion(null);
      assertTrue(OperationUtils.isCompleted(op));
    }
  }
}
