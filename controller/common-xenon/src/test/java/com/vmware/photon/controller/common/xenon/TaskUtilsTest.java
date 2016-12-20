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
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.UriUtils;

import com.google.common.util.concurrent.FutureCallback;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.fail;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class implements tests for {@link TaskUtilsTest}.
 */
public class TaskUtilsTest {

  private Service service;
  private ServiceHost host;
  private Operation operation;

  @Test
  private void dummy() {
  }

  /**
   * This class tests the startTaskAsync method.
   */
  public class StartTaskAsync {

    @BeforeMethod
    public void setUp() {
      service = mock(Service.class);
      host = mock(ServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      when(service.getHost()).thenReturn(host);
      operation = mock(Operation.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void success() throws Throwable {
      final AtomicInteger count = new AtomicInteger(0);
      ServiceDocument document = new ServiceDocument();
      document.documentSelfLink = "selfLink";
      when(operation.getBody(any(Class.class))).thenReturn(document);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((Operation) invocation.getArguments()[0]).getCompletion().handle(operation, null);
          return null;
        }
      }).when(service).sendRequest(any(Operation.class));

      FutureCallback<ServiceDocument> futureCallback = new FutureCallback<ServiceDocument>() {
        @Override
        public void onSuccess(ServiceDocument result) {
          count.incrementAndGet();
        }

        @Override
        public void onFailure(Throwable t) {
          fail();
        }
      };

      TaskUtils.startTaskAsync(service,
          "factoryLink",
          new ServiceDocument(),
          (input) -> true,
          null,
          0,
          futureCallback);

      verify(service, times(2)).sendRequest(any(Operation.class));
      assertThat(count.get(), is(1));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void invokesFailureWhenThrowableNotNull() {
      final AtomicInteger count = new AtomicInteger(0);
      ServiceDocument document = new ServiceDocument();
      document.documentSelfLink = "selfLink";
      when(operation.getBody(any(Class.class))).thenReturn(document);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((Operation) invocation.getArguments()[0]).getCompletion().handle(operation, new RuntimeException());
          return null;
        }
      }).when(service).sendRequest(any(Operation.class));

      FutureCallback<ServiceDocument> futureCallback = new FutureCallback<ServiceDocument>() {
        @Override
        public void onSuccess(ServiceDocument result) {
          fail();
        }

        @Override
        public void onFailure(Throwable t) {
          count.incrementAndGet();
        }
      };

      TaskUtils.startTaskAsync(service, "factoryLink", new ServiceDocument(), null, null, 0, futureCallback);

      assertThat(count.get(), is(1));
    }
  }

  /**
   * This class tests the checkProgress method.
   */
  public class CheckProgress {

    @BeforeMethod
    public void setUp() {
      service = mock(Service.class);
      host = mock(ServiceHost.class);
      when(host.getUri()).thenReturn(UriUtils.buildUri("http://localhost:0/mock"));
      when(service.getHost()).thenReturn(host);
      operation = mock(Operation.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void successWithoutRetry() {
      final AtomicInteger count = new AtomicInteger(0);
      ServiceDocument document = new ServiceDocument();
      document.documentSelfLink = "selfLink";
      when(operation.getBody(any(Class.class))).thenReturn(document);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((Operation) invocation.getArguments()[0]).getCompletion().handle(operation, null);
          return null;
        }
      }).when(service).sendRequest(any(Operation.class));

      FutureCallback<ServiceDocument> futureCallback = new FutureCallback<ServiceDocument>() {
        @Override
        public void onSuccess(ServiceDocument result) {
          count.incrementAndGet();
        }

        @Override
        public void onFailure(Throwable t) {
          fail();
        }
      };

      TaskUtils.checkProgress(service,
          "factoryLink",
          (input) -> true,
          null,
          0,
          futureCallback);

      assertThat(count.get(), is(1));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void shouldRetry() {
      ServiceDocument document = new ServiceDocument();
      document.documentSelfLink = "selfLink";
      when(operation.getBody(any(Class.class))).thenReturn(document);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((Operation) invocation.getArguments()[0]).getCompletion().handle(operation, null);
          return null;
        }
      }).when(service).sendRequest(any(Operation.class));

      TaskUtils.checkProgress(service,
          "factoryLink",
          (input) -> false,
          null,
          0,
          null);

      verify(service, times(2)).getHost();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void invokesFailureWhenThrowableNotNull() {
      final AtomicInteger count = new AtomicInteger(0);
      ServiceDocument document = new ServiceDocument();
      document.documentSelfLink = "selfLink";
      when(operation.getBody(any(Class.class))).thenReturn(document);
      doAnswer(new Answer() {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
          ((Operation) invocation.getArguments()[0]).getCompletion().handle(operation, new RuntimeException());
          return null;
        }
      }).when(service).sendRequest(any(Operation.class));

      FutureCallback<ServiceDocument> futureCallback = new FutureCallback<ServiceDocument>() {
        @Override
        public void onSuccess(ServiceDocument result) {
          fail();
        }

        @Override
        public void onFailure(Throwable t) {
          count.incrementAndGet();
        }
      };

      TaskUtils.checkProgress(service,
          "factoryLink",
          (input) -> true,
          null,
          0,
          futureCallback);

      assertThat(count.get(), is(1));
    }
  }
}
