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

package com.vmware.photon.controller.deployer.deployengine;

import com.vmware.photon.controller.deployer.helpers.TestHelper;
import com.vmware.photon.controller.deployer.service.exceptions.InvalidLoginException;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertTrue;

import javax.net.ssl.HttpsURLConnection;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link HttpFileServiceClient} class.
 */
public class HttpFileServiceClientTest {

  private static final Logger logger = LoggerFactory.getLogger(HttpFileServiceClientTest.class);

  private static final String STORAGE_DIRECTORY_PATH = "/tmp/file_service_client";

  /**
   * This test declaration enables IntelliJ to recognize this as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    @Test
    public void testSuccess() {
      HttpFileServiceClient httpFileServiceClient = new HttpFileServiceClient("HOST_ADDRESS", "USER_NAME", "PASSWORD");
      assertThat(httpFileServiceClient.getHostAddress(), is("HOST_ADDRESS"));
      assertThat(httpFileServiceClient.getPassword(), is("PASSWORD"));
      assertThat(httpFileServiceClient.getUserName(), is("USER_NAME"));
    }
  }

  /**
   * This class implements tests for the uploadFile method.
   */
  public class UploadFileTest {

    private File storageDirectory = new File(STORAGE_DIRECTORY_PATH);
    private File sourceDirectory = new File(storageDirectory, "source");
    private File destinationDirectory = new File(storageDirectory, "destination");

    private ExecutorService executorService;
    private HttpFileServiceClient httpFileServiceClient;
    private File sourceFile;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      sourceDirectory.mkdirs();
      sourceFile = TestHelper.createSourceFile(null, sourceDirectory);
      executorService = Executors.newFixedThreadPool(1);
      httpFileServiceClient = new HttpFileServiceClient("HOST_ADDRESS", "USER_NAME", "PASSWORD");
    }

    @BeforeMethod
    public void setUpTest() {
      destinationDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(destinationDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      executorService.shutdown();
    }

    @Test
    public void testSuccess() throws Throwable {
      File outputFile = new File(destinationDirectory, "output.bin");
      outputFile.createNewFile();
      FileOutputStream fileOutputStream = new FileOutputStream(outputFile);

      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(fileOutputStream).when(httpConnection).getOutputStream();
      doReturn(HttpsURLConnection.HTTP_CREATED).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFile(
          sourceFile.getAbsolutePath(), "/tmp/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          assertThat(result, is(HttpsURLConnection.HTTP_CREATED));
          countDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          logger.error("Task failed unexpectedly: " + t.toString());
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
      assertTrue(FileUtils.contentEquals(sourceFile, outputFile));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFailureMissingSourceFile() throws Throwable {
      File missingSourceFile = new File(sourceDirectory, "missing.bin");
      httpFileServiceClient.uploadFile(missingSourceFile.getAbsolutePath(), "/tmp/output.bin");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFailureInvalidDestinationPath() {
      httpFileServiceClient.uploadFile(sourceFile.getAbsolutePath(), "/usr/lib/output.bin");
    }

    @Test
    public void testCopyFailure() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doThrow(new IOException("Copy failure")).when(httpConnection).getOutputStream();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFile(
          sourceFile.getAbsolutePath(), "/tmp/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable t) {
          assertTrue(t instanceof IOException);
          assertThat(t.getMessage(), is("Copy failure"));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testHttpResponseFailure() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(HttpsURLConnection.HTTP_UNAUTHORIZED).when(httpConnection).getResponseCode();
      OutputStream outputStream = mock(OutputStream.class);
      doReturn(outputStream).when(httpConnection).getOutputStream();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFile(
          sourceFile.getAbsolutePath(), "/tmp/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable t) {
          assertThat(t.getMessage(), containsString(" failed with HTTP response " + Integer.toString(
              HttpsURLConnection.HTTP_UNAUTHORIZED)));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }
  }

  /**
   * This class implements tests for the uploadFileToDatastore method.
   */
  public class UploadFileToDatastoreTest {

    private File storageDirectory = new File(STORAGE_DIRECTORY_PATH);
    private File sourceDirectory = new File(storageDirectory, "source");
    private File destinationDirectory = new File(storageDirectory, "destination");

    private ExecutorService executorService;
    private HttpFileServiceClient httpFileServiceClient;
    private File sourceFile;

    @BeforeClass
    public void setUpClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      sourceDirectory.mkdirs();
      sourceFile = TestHelper.createSourceFile(null, sourceDirectory);
      executorService = Executors.newFixedThreadPool(1);
      httpFileServiceClient = new HttpFileServiceClient("HOST_ADDRESS", "USER_NAME", "PASSWORD");
    }

    @BeforeMethod
    public void setUpTest() {
      destinationDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws Throwable {
      FileUtils.deleteDirectory(destinationDirectory);
    }

    @AfterClass
    public void tearDownClass() throws Throwable {
      FileUtils.deleteDirectory(storageDirectory);
      executorService.shutdown();
    }

    @Test
    public void testSuccess() throws Throwable {
      File outputFile = new File(destinationDirectory, "output.bin");
      outputFile.createNewFile();
      FileOutputStream fileOutputStream = new FileOutputStream(outputFile);

      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(fileOutputStream).when(httpConnection).getOutputStream();
      doReturn(HttpsURLConnection.HTTP_CREATED).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFileToDatastore(
          sourceFile.getAbsolutePath(), "DATASTORE_NAME", "/path/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          assertThat(result, is(HttpsURLConnection.HTTP_CREATED));
          countDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          logger.error("Task failed unexpectedly: " + t.toString());
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
      assertTrue(FileUtils.contentEquals(sourceFile, outputFile));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFailureMissingSourceFile() throws Throwable {
      File missingSourceFile = new File(sourceDirectory, "missing.bin");
      httpFileServiceClient.uploadFileToDatastore(missingSourceFile.getAbsolutePath(), "DATASTORE_NAME",
          "/path/output.bin");
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testFailureInvalidDatastorePath() {
      httpFileServiceClient.uploadFileToDatastore(sourceFile.getAbsolutePath(), "DATASTORE_NAME", "path/output.bin");
    }

    @Test
    public void testCopyFailure() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doThrow(new IOException("Copy failure")).when(httpConnection).getOutputStream();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFileToDatastore(
          sourceFile.getAbsolutePath(), "DATASTORE_NAME", "/path/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable t) {
          assertTrue(t instanceof IOException);
          assertThat(t.getMessage(), is("Copy failure"));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testHttpResponseFailure() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(HttpsURLConnection.HTTP_UNAUTHORIZED).when(httpConnection).getResponseCode();
      OutputStream outputStream = mock(OutputStream.class);
      doReturn(outputStream).when(httpConnection).getOutputStream();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.uploadFileToDatastore(
          sourceFile.getAbsolutePath(), "DATASTORE_NAME", "/path/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable t) {
          assertThat(t.getMessage(), containsString(" failed with HTTP response " + Integer.toString(
              HttpsURLConnection.HTTP_UNAUTHORIZED)));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }
  }

  /**
   * This class implements tests for the deleteFileFromDatastore method.
   */
  public class DeleteFileFromDatastoreTest {

    private ExecutorService executorService;
    private HttpFileServiceClient httpFileServiceClient;

    @BeforeClass
    public void setUpClass() {
      executorService = Executors.newFixedThreadPool(1);
      httpFileServiceClient = new HttpFileServiceClient("HOST_ADDRESS", "USER_NAME", "PASSWORD");
    }

    @AfterClass
    public void tearDownClass() {
      executorService.shutdown();
    }

    @Test
    public void testSuccess() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(HttpsURLConnection.HTTP_NO_CONTENT).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.deleteFileFromDatastore(
          "DATASTORE_NAME", "/path/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          assertThat(result, is(HttpsURLConnection.HTTP_NO_CONTENT));
          countDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          logger.error("Task failed unexpectedly: " + t.toString());
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testInvalidDatastorePath() {
      httpFileServiceClient.deleteFileFromDatastore("DATASTORE_NAME", "path/output.bin");
    }

    @Test
    public void testHttpResponseFailure() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(HttpsURLConnection.HTTP_UNAUTHORIZED).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(httpFileServiceClient.deleteFileFromDatastore(
          "DATASTORE_NAME", "/path/output.bin"));
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable t) {
          assertThat(t.getMessage(), containsString(" failed with HTTP response " + Integer.toString(
              HttpsURLConnection.HTTP_UNAUTHORIZED)));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }
  }

  /**
   * This class implements tests for the getDirectoryListingOfDatastores method.
   */
  public class GetDirectoryListingOfDatastoresTest {

    private ExecutorService executorService;
    private HttpFileServiceClient httpFileServiceClient;

    @BeforeClass
    public void setUpClass() {
      executorService = Executors.newFixedThreadPool(1);
      httpFileServiceClient = new HttpFileServiceClient("HOST_ADDRESS", "USER_NAME", "PASSWORD");
    }

    @AfterClass
    public void tearDownClass() {
      executorService.shutdown();
    }

    @Test
    public void testSuccess() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(HttpsURLConnection.HTTP_OK).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(
          httpFileServiceClient.getDirectoryListingOfDatastores());
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          assertThat(result, is(HttpsURLConnection.HTTP_OK));
          countDownLatch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          logger.error("Task failed unexpectedly: " + t.toString());
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testHttpResponseFailure() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doReturn(HttpsURLConnection.HTTP_UNAUTHORIZED).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(
          httpFileServiceClient.getDirectoryListingOfDatastores());
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable t) {
          assertTrue(t instanceof InvalidLoginException);
          assertThat(t.getMessage(), is(nullValue()));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testHttpResponseException() throws Throwable {
      HttpsURLConnection httpConnection = mock(HttpsURLConnection.class);
      doThrow(new RuntimeException("Runtime exception")).when(httpConnection).getResponseCode();
      httpFileServiceClient.setHttpConnection(httpConnection);

      ListenableFutureTask<Integer> task = ListenableFutureTask.create(
          httpFileServiceClient.getDirectoryListingOfDatastores());
      executorService.submit(task);
      CountDownLatch countDownLatch = new CountDownLatch(1);
      Futures.addCallback(task, new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          logger.error("Task succeeded unexpectedly: " + Integer.toString(result));
        }

        @Override
        public void onFailure(Throwable throwable) {
          assertTrue(throwable instanceof RuntimeException);
          assertThat(throwable.getMessage(), containsString("Runtime exception"));
          countDownLatch.countDown();
        }
      });

      assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
    }
  }
}
