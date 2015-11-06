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

package com.vmware.photon.controller.common.clients.exceptions;

import com.vmware.photon.controller.status.gen.Status;
import com.vmware.photon.controller.status.gen.StatusType;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.concurrent.TimeoutException;

/**
 * Tests {@link ComponentClientExceptionHandler}.
 */
public class ComponentClientExceptionHandlerTest {
  ComponentClientExceptionHandler subject;

  /**
   * Dummy test case to help IntelliJ recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Test the handle method.
   */
  public class HandleTest {
    @BeforeMethod
    public void setUp() {
      subject = new ComponentClientExceptionHandler();
    }

    @Test
    public void testException() {
      Status status = subject.handle(new Exception("error"));

      assertThat(status, notNullValue());
      assertThat(status.getType(), is(StatusType.ERROR));
      assertThat(status.getMessage(), is("error"));
    }

    @Test
    public void testRpcException() {
      Status status = subject.handle(new RpcException("error"));

      assertThat(status, notNullValue());
      assertThat(status.getType(), is(StatusType.ERROR));
      assertThat(status.getMessage(), is("error"));
    }

    @Test
    public void testRpcExceptionWithNestedException() {
      Status status = subject.handle(new RpcException(new Exception("error")));

      assertThat(status, notNullValue());
      assertThat(status.getType(), is(StatusType.ERROR));
      assertThat(status.getMessage(), is("java.lang.Exception: error"));
    }

    @Test
    public void testRpcExceptionWithNestedTimeoutException() {
      Status status = subject.handle(new RpcException(new TimeoutException("error")));

      assertThat(status, notNullValue());
      assertThat(status.getType(), is(StatusType.UNREACHABLE));
      assertThat(status.getMessage(), is("java.util.concurrent.TimeoutException: error"));
    }
  }
}
