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

package com.vmware.photon.controller.nsxclient.apis;

import com.vmware.photon.controller.nsxclient.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseFactory;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * Tests {@link com.vmware.photon.controller.nsxclient.apis.NsxClientApi}.
 */
public class NsxClientApiBaseTest extends NsxClientApi {

  /**
   * A dummy class used for test purpose.
   */
  public static class DummyClass {
    @JsonProperty("field1")
    public String dummyField1;
    @JsonProperty("field2")
    public String dummyField2;

    public DummyClass() {
    }

    public DummyClass(String dummyField1, String dummyField2) {
      this.dummyField1 = dummyField1;
      this.dummyField2 = dummyField2;
    }
  }

  protected static final int CALLBACK_ARG_INDEX = 2;

  public NsxClientApiBaseTest() {
    super(mock(RestClient.class));
  }

  public NsxClientApiBaseTest(RestClient restClient) {
    super(restClient);
  }

  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * Tests for constructors.
   */
  public static class TestContructors {
    @Test
    public void testValidConstructor() {
      new NsxClientApi(mock(RestClient.class));
    }

    @Test
    public void testInvalidConstructor() {
      try {
        new NsxClientApi(null);
      } catch (Exception e) {
        assertThat(e.getMessage(), is("restClient cannot be null"));
      }
    }
  }

  /**
   * Tests for the serialization/deserialization between object and JSON string.
   */
  public static class SerializationTest {
    @Test
    public void testSerializeObjectAsJson() throws Exception {
      String expectedJsonContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest();
      DummyClass dummyObject = new DummyClass("value1", "value2");
      StringEntity stringEntity = nsxClientApi.serializeObjectAsJson(dummyObject);

      assertThat(IOUtils.toString(stringEntity.getContent()), is(expectedJsonContent));
      assertThat(stringEntity.getContentType().getValue(), is(ContentType.APPLICATION_JSON.toString()));
    }

    @Test
    public void testDeserializeObjectFromJson() throws Exception {
      String jsonContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";
      HttpEntity httpEntity = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest();
      DummyClass dummyObject = nsxClientApi.deserializeObjectFromJson(httpEntity, new TypeReference<DummyClass>() {});

      assertThat(dummyObject.dummyField1, is("value1"));
      assertThat(dummyObject.dummyField2, is("value2"));
    }
  }

  /**
   * Tests for methods of posting HTTP request to NSX.
   */
  public static class PostTest {
    private HttpResponseFactory factory;
    private RestClient restClient;

    @BeforeMethod
    public void setup() {
      factory = new DefaultHttpResponseFactory();
      restClient = spy(new RestClient("target", "username", "password"));
    }

    @Test
    public void testPostSuccessful() throws IOException {
      String jsonContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";
      HttpEntity responseHttpEntity = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);

      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
      httpResponse.setEntity(responseHttpEntity);

      HttpEntity requestHttpEntity = new StringEntity("", ContentType.APPLICATION_JSON);
      doReturn(httpResponse).when(restClient).send(RestClient.Method.POST, "target", requestHttpEntity);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      DummyClass dummyObject = nsxClientApi.post("target", requestHttpEntity, HttpStatus.SC_OK,
          new TypeReference<DummyClass>() {
          });

      assertThat(dummyObject.dummyField1, is("value1"));
      assertThat(dummyObject.dummyField2, is("value2"));
    }

    @Test
    public void testPostFailed() throws IOException {
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, null);

      HttpEntity requestHttpEntity = new StringEntity("", ContentType.APPLICATION_JSON);
      doReturn(httpResponse).when(restClient).send(RestClient.Method.POST, "target", requestHttpEntity);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      try {
        nsxClientApi.post("target", requestHttpEntity, HttpStatus.SC_OK, new TypeReference<DummyClass>() {
        });
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), is("HTTP request failed with: " + HttpStatus.SC_NOT_FOUND));
      }
    }
  }

  /**
   * Tests for methods of async post to NSX.
   */
  public static class AsyncPostTest {
    private CloseableHttpAsyncClient asyncClient;
    private RestClient restClient;
    private Future<HttpResponse> httpResponseFuture;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() throws IOException {
      httpResponseFuture = mock(FutureTask.class);
      asyncClient = mock(CloseableHttpAsyncClient.class);
      restClient = spy(new RestClient("target", "username", "password", asyncClient));
      latch = new CountDownLatch(1);

      doAnswer(invocation -> null).when(asyncClient).close();
    }

    @Test
    public void testAsyncPostSuccessful() throws Exception {
      String jsonContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";
      HttpEntity responseHttpEntity  = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);

      HttpResponseFactory factory = new DefaultHttpResponseFactory();
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
      httpResponse.setEntity(responseHttpEntity);

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).completed(httpResponse);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      HttpEntity requestHttpEntity = new StringEntity("", ContentType.APPLICATION_JSON);
      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.postAsync("target",
          requestHttpEntity,
          HttpStatus.SC_OK,
          new TypeReference<DummyClass>() {},
          new com.google.common.util.concurrent.FutureCallback<DummyClass>() {
            @Override
            public void onSuccess(DummyClass result) {
              assertThat(result.dummyField1, is("value1"));
              assertThat(result.dummyField2, is("value2"));

              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @Test
    public void testAsyncPostFailed() throws Exception {
      final String errorMsg = "Document does not exist";
      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          Exception ex = new Exception(errorMsg);
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).failed(ex);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      HttpEntity requestHttpEntity = new StringEntity("", ContentType.APPLICATION_JSON);
      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.postAsync("target",
          requestHttpEntity,
          HttpStatus.SC_OK,
          new TypeReference<DummyClass>() {},
          new com.google.common.util.concurrent.FutureCallback<DummyClass>() {
            @Override
            public void onSuccess(DummyClass result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          }
      );
      latch.await();
    }
  }

  /**
   * Tests for methods of getting HTTP request to NSX.
   */
  public static class GetTest {
    private HttpResponseFactory factory;
    private RestClient restClient;

    @BeforeMethod
    public void setup() {
      factory = new DefaultHttpResponseFactory();
      restClient = spy(new RestClient("target", "username", "password"));
    }

    @Test
    public void testGetSuccessful() throws IOException {
      String jsonContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";
      HttpEntity responseHttpEntity  = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);

      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
      httpResponse.setEntity(responseHttpEntity);

      doReturn(httpResponse).when(restClient).send(RestClient.Method.GET, "target", null);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      DummyClass dummyObject = nsxClientApi.get("target", HttpStatus.SC_OK, new TypeReference<DummyClass>() {
      });

      assertThat(dummyObject.dummyField1, is("value1"));
      assertThat(dummyObject.dummyField2, is("value2"));
    }

    @Test
    public void testGetFailed() throws IOException {
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, null);
      doReturn(httpResponse).when(restClient).send(RestClient.Method.GET, "target", null);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      try {
        nsxClientApi.get("target", HttpStatus.SC_OK, new TypeReference<DummyClass>() {
        });
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), is("HTTP request failed with: " + HttpStatus.SC_NOT_FOUND));
      }
    }
  }

  /**
   * Tests for methods of async get to NSX.
   */
  public static class AsyncGetTest {
    private CloseableHttpAsyncClient asyncClient;
    private RestClient restClient;
    private Future<HttpResponse> httpResponseFuture;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() throws IOException {
      httpResponseFuture = mock(FutureTask.class);
      asyncClient = mock(CloseableHttpAsyncClient.class);
      restClient = spy(new RestClient("target", "username", "password", asyncClient));
      latch = new CountDownLatch(1);

      doAnswer(invocation -> null).when(asyncClient).close();
    }

    @Test
    public void testAsyncGetSuccessful() throws Exception {
      String jsonContent = "{\"field1\":\"value1\",\"field2\":\"value2\"}";
      HttpEntity responseHttpEntity  = new StringEntity(jsonContent, ContentType.APPLICATION_JSON);

      HttpResponseFactory factory = new DefaultHttpResponseFactory();
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
      httpResponse.setEntity(responseHttpEntity);

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).completed(httpResponse);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.getAsync("target",
          HttpStatus.SC_OK,
          new TypeReference<DummyClass>() {},
          new com.google.common.util.concurrent.FutureCallback<DummyClass>() {
            @Override
            public void onSuccess(DummyClass result) {
              assertThat(result.dummyField1, is("value1"));
              assertThat(result.dummyField2, is("value2"));

              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @Test
    public void testAsyncGetFailed() throws Exception {
      final String errorMsg = "Document does not exist";
      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          Exception ex = new Exception(errorMsg);
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).failed(ex);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      HttpEntity requestHttpEntity = new StringEntity("", ContentType.APPLICATION_JSON);
      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.getAsync("target",
          HttpStatus.SC_OK,
          new TypeReference<DummyClass>() {},
          new com.google.common.util.concurrent.FutureCallback<DummyClass>() {
            @Override
            public void onSuccess(DummyClass result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          }
      );
      latch.await();
    }
  }

  /**
   * Tests for methods of delete request to NSX.
   */
  public static class DeleteTest {
    private HttpResponseFactory factory = new DefaultHttpResponseFactory();
    private RestClient restClient = spy(new RestClient("target", "username", "password"));

    @Test
    public void testDeleteSuccessful() throws IOException {
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);
      doReturn(httpResponse).when(restClient).send(RestClient.Method.DELETE, "target", null);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.delete("target", HttpStatus.SC_OK);
    }

    @Test
    public void testDeleteFailed() throws IOException {
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_NOT_FOUND, null);
      doReturn(httpResponse).when(restClient).send(RestClient.Method.DELETE, "target", null);

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      try {
        nsxClientApi.delete("target", HttpStatus.SC_OK);
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), is("HTTP request failed with: " + HttpStatus.SC_NOT_FOUND));
      }
    }
  }

  /**
   * Tests for methods of async delete to NSX.
   */
  public static class AsyncDeleteTest {
    private CloseableHttpAsyncClient asyncClient;
    private RestClient restClient;
    private Future<HttpResponse> httpResponseFuture;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() throws IOException {
      httpResponseFuture = mock(FutureTask.class);
      asyncClient = mock(CloseableHttpAsyncClient.class);
      restClient = spy(new RestClient("target", "username", "password", asyncClient));
      latch = new CountDownLatch(1);

      doAnswer(invocation -> null).when(asyncClient).close();
    }

    @Test
    public void testAsyncDeleteSuccessful() throws Exception {
      HttpResponseFactory factory = new DefaultHttpResponseFactory();
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, HttpStatus.SC_OK, null);

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).completed(httpResponse);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.deleteAsync("target",
          HttpStatus.SC_OK,
          new com.google.common.util.concurrent.FutureCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @Test
    public void testAsyncGetFailed() throws Exception {
      final String errorMsg = "Document does not exist";
      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          Exception ex = new Exception(errorMsg);
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).failed(ex);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.deleteAsync("target",
          HttpStatus.SC_OK,
          new com.google.common.util.concurrent.FutureCallback<Void>() {
            @Override
            public void onSuccess(Void v) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          }
      );
      latch.await();
    }
  }

  /**
   * Tests for methods of async check existence to NSX.
   */
  public static class AsyncCheckExistenceTest {
    private CloseableHttpAsyncClient asyncClient;
    private RestClient restClient;
    private Future<HttpResponse> httpResponseFuture;
    private CountDownLatch latch;

    @BeforeMethod
    public void setup() throws IOException {
      httpResponseFuture = mock(FutureTask.class);
      asyncClient = mock(CloseableHttpAsyncClient.class);
      restClient = spy(new RestClient("target", "username", "password", asyncClient));
      latch = new CountDownLatch(1);

      doAnswer(invocation -> null).when(asyncClient).close();
    }

    @Test(dataProvider = "existenceData")
    public void testAsyncCheckExistenceSuccessful(int httpStatus, Boolean expectedStatus) throws Exception {
      HttpResponseFactory factory = new DefaultHttpResponseFactory();
      HttpResponse httpResponse = factory.newHttpResponse(HttpVersion.HTTP_1_1, httpStatus, null);

      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX]).completed(httpResponse);
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.checkExistenceAsync("target",
          new com.google.common.util.concurrent.FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              assertThat(result, is(expectedStatus));
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              fail("Should not have failed");
            }
          }
      );
      latch.await();
    }

    @Test
    public void testAsyncCheckExistenceFailed() throws Exception {
      final String errorMsg = "Not Found";
      doAnswer(invocation -> {
        if (invocation.getArguments()[CALLBACK_ARG_INDEX] != null) {
          ((FutureCallback<HttpResponse>) invocation.getArguments()[CALLBACK_ARG_INDEX])
              .failed(new Exception(errorMsg));
        }
        return httpResponseFuture;
      }).when(asyncClient).execute(any(HttpUriRequest.class), any(BasicHttpContext.class), any(FutureCallback.class));

      NsxClientApiBaseTest nsxClientApi = new NsxClientApiBaseTest(restClient);
      nsxClientApi.checkExistenceAsync("target",
          new com.google.common.util.concurrent.FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
              fail("Should not have succeeded");
              latch.countDown();
            }

            @Override
            public void onFailure(Throwable t) {
              assertThat(t.getMessage(), is(errorMsg));
              latch.countDown();
            }
          }
      );
      latch.await();
    }

    @DataProvider(name = "existenceData")
    public Object[][] getExistenceData() {
      return new Object[][]{
        {HttpStatus.SC_OK, true},
        {HttpStatus.SC_CREATED, true},
        {HttpStatus.SC_NOT_FOUND, false}
      };
    }
  }
}
