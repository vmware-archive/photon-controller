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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class implements tests for the {@link ScriptRunner} class.
 */
public class ScriptRunnerTest {

  private static final Logger logger = LoggerFactory.getLogger(ScriptRunnerTest.class);

  private static final File storageDirectory = new File("/tmp/scriptRunner");

  private static final int DEFAULT_TIMEOUT_IN_SECONDS = 10;

  private ScriptRunner scriptRunner;

  private void createScript(File scriptFile, String content) throws IOException {
    scriptFile.createNewFile();
    scriptFile.setExecutable(true, true);

    FileOutputStream outputStream = new FileOutputStream(scriptFile);
    outputStream.write(content.getBytes());
    outputStream.flush();
    outputStream.close();
  }

  /**
   * Dummy test case to make IntelliJ recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the builder constructor.
   */
  public class BuilderConstructorTest {

    private final List<String> baseCommand = Arrays.asList("echo", "hello", "world");

    private final List<String> expectedCommand = Arrays.asList("echo", "hello", "world");

    @AfterClass
    public void tearDownClass() {
      scriptRunner = null;
    }

    @Test
    public void testSuccessfulConstruction() {
      scriptRunner = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS).build();
      assertThat(scriptRunner.getCommand(), is(expectedCommand));
      assertThat(scriptRunner.getTimeoutInSeconds(), is(DEFAULT_TIMEOUT_IN_SECONDS));
      assertThat(scriptRunner.getDirectory(), nullValue());
      assertThat(scriptRunner.getEnvironment(), nullValue());
      assertThat(scriptRunner.getRedirectError(), nullValue());
      assertThat(scriptRunner.getRedirectErrorStream(), nullValue());
      assertThat(scriptRunner.getRedirectInput(), nullValue());
      assertThat(scriptRunner.getRedirectOutput(), nullValue());
    }

    @Test
    public void testFailureNullCommandList() {
      try {
        ScriptRunner.Builder builder = new ScriptRunner.Builder(null, DEFAULT_TIMEOUT_IN_SECONDS);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Parameter command cannot be null or zero-length"));
      }
    }

    @Test
    public void testFailureEmptyCommandList() {
      List<String> emptyCommand = new ArrayList<>();
      try {
        ScriptRunner.Builder builder = new ScriptRunner.Builder(emptyCommand, DEFAULT_TIMEOUT_IN_SECONDS);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Parameter command cannot be null or zero-length"));
      }
    }

    @Test
    public void testFailureZeroTimeoutValue() {
      try {
        ScriptRunner.Builder builder = new ScriptRunner.Builder(baseCommand, 0);
      } catch (IllegalArgumentException e) {
        assertThat(e.getMessage(), is("Parameter timeoutInSeconds cannot be zero"));
      }
    }
  }

  /**
   * This class implements tests for getter and setter methods.
   */
  public class BuilderTest {

    private final List<String> command = Arrays.asList("echo", "hello", "world");

    private final List<String> expectedCommand = Arrays.asList("echo", "hello", "world");

    private ScriptRunner.Builder builder;

    @BeforeClass
    public void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() {
      storageDirectory.mkdirs();
      builder = new ScriptRunner.Builder(command, DEFAULT_TIMEOUT_IN_SECONDS);
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws IOException {
      scriptRunner = null;
    }

    @Test
    public void testDefaultCommand() {
      scriptRunner = builder.build();
      assertThat(scriptRunner.getCommand(), is(expectedCommand));
    }

    @Test
    public void testDefaultTimeout() {
      scriptRunner = builder.build();
      assertThat(scriptRunner.getTimeoutInSeconds(), is(DEFAULT_TIMEOUT_IN_SECONDS));
    }

    @Test
    public void testEnvironment() {
      Map<String, String> environment = new HashMap<>();
      environment.put("MY_ENVIRONMENT_VARIABLE", "MY_VALUE");
      builder.environment(environment);
      environment.put("MY_NEW_ENVIRONMENT_VARIABLE", "MY_OTHER_VALUE");
      scriptRunner = builder.build();

      Map<String, String> expectedEnvironment = new HashMap<>();
      expectedEnvironment.put("MY_ENVIRONMENT_VARIABLE", "MY_VALUE");
      assertThat(scriptRunner.getEnvironment(), is(expectedEnvironment));
    }

    @Test
    public void testDirectory() throws IOException {
      File directory = new File(storageDirectory, "scriptDir");
      builder.directory(directory.getCanonicalPath());
      scriptRunner = builder.build();
      assertThat(scriptRunner.getDirectory(), is(directory.getCanonicalPath()));
    }

    @Test
    public void testRedirectError() {
      File devNull = new File("/dev/null");
      ProcessBuilder.Redirect redirect = ProcessBuilder.Redirect.to(devNull);
      builder.redirectError(redirect);
      scriptRunner = builder.build();

      ProcessBuilder.Redirect expectedRedirect = ProcessBuilder.Redirect.to(devNull);
      assertThat(scriptRunner.getRedirectError(), is(expectedRedirect));
    }

    @Test(dataProvider = "booleanProvider")
    public void testRedirectErrorStream(boolean value) {
      builder.redirectErrorStream(value);
      scriptRunner = builder.build();
      assertThat(scriptRunner.getRedirectErrorStream(), is(value));
    }

    @DataProvider(name = "booleanProvider")
    public Object[][] getBooleanData() {
      return new Object[][]{{false}, {true}};
    }

    @Test
    public void testRedirectInput() {
      File devNull = new File("/dev/null");
      ProcessBuilder.Redirect redirect = ProcessBuilder.Redirect.to(devNull);
      builder.redirectInput(redirect);
      scriptRunner = builder.build();

      ProcessBuilder.Redirect expectedRedirect = ProcessBuilder.Redirect.to(devNull);
      assertThat(scriptRunner.getRedirectInput(), is(expectedRedirect));
    }

    @Test
    public void testRedirectOutput() {
      File devNull = new File("/dev/null");
      ProcessBuilder.Redirect redirect = ProcessBuilder.Redirect.to(devNull);
      builder.redirectOutput(redirect);
      scriptRunner = builder.build();

      ProcessBuilder.Redirect expectedRedirect = ProcessBuilder.Redirect.to(devNull);
      assertThat(scriptRunner.getRedirectOutput(), is(expectedRedirect));
    }
  }

  /**
   * This class implements tests for the process environment.
   */
  public class EnvironmentTest {

    private final File scriptFile = new File(storageDirectory, "scriptFile.sh");

    private final List<String> baseCommand = Arrays.asList(scriptFile.getAbsolutePath());

    private final File stdoutLog = new File(storageDirectory, "stdout.log");

    @BeforeClass
    public void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() {
      storageDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws IOException {
      scriptRunner = null;
    }

    @Test
    public void testEnvironment() throws IOException {
      String scriptContents = ">&1 echo $MY_ENVIRONMENT_VARIABLE\n";
      createScript(scriptFile, scriptContents);

      Map<String, String> environment = new HashMap<>(1);
      environment.put("MY_ENVIRONMENT_VARIABLE", "potatoes");

      scriptRunner = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS)
          .environment(environment)
          .redirectOutput(ProcessBuilder.Redirect.to(stdoutLog))
          .build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdoutLog);
      assertThat(stdOutLogContents.trim(), is("potatoes"));
    }
  }

  /**
   * This class implements tests for the process working directory.
   */
  public class DirectoryTest {

    private final File scriptFile = new File(storageDirectory, "scriptFile.sh");

    private final List<String> baseCommand = Arrays.asList(scriptFile.getAbsolutePath());

    private final File stdOutLog = new File(storageDirectory, "stdOut.log");

    private final ScriptRunner.Builder builder =
        new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS)
            .redirectOutput(ProcessBuilder.Redirect.to(stdOutLog));

    @BeforeClass
    public void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() {
      storageDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws IOException {
      scriptRunner = null;
    }

    @Test
    public void testDirectoryNoChange() throws IOException {
      String scriptContents = ">&1 pwd\n";
      createScript(scriptFile, scriptContents);
      scriptRunner = builder.build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is(System.getProperty("user.dir")));
    }

    @Test
    public void testDirectoryWithChange() throws IOException {
      String scriptContents = ">&1 pwd\n";
      createScript(scriptFile, scriptContents);
      builder.directory(storageDirectory.getCanonicalPath());
      scriptRunner = builder.build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is(storageDirectory.getCanonicalPath()));
    }

    @Test
    public void testMissingDirectoryFailure() throws IOException {
      File missingDirectory = new File(storageDirectory, "not_here");
      builder.directory(missingDirectory.getCanonicalPath());
      scriptRunner = builder.build();

      try {
        scriptRunner.call();
        fail("Script runner should throw with missing working directory");
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), containsString("No such file or directory"));
      }
    }

    @Test
    public void testFileAsDirectoryFailure() throws IOException {
      File fileAsDirectory = new File(storageDirectory, "myfile");
      fileAsDirectory.createNewFile();
      builder.directory(fileAsDirectory.getAbsolutePath());
      scriptRunner = builder.build();

      try {
        scriptRunner.call();
        fail("Script runner should throw with file as working directory");
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), containsString("Not a directory"));
      }
    }
  }

  /**
   * This class implements test for redirection of standard I/O streams.
   */
  public class StandardStreamTest {

    private final File scriptFile = new File(storageDirectory, "scriptFile.sh");

    private final List<String> baseCommand = Arrays.asList(scriptFile.getAbsolutePath());

    private final File stdInContents = new File(storageDirectory, "stdIn.txt");

    private final File stdOutLog = new File(storageDirectory, "stdOut.log");

    private final File stdErrLog = new File(storageDirectory, "stdErr.log");

    private ScriptRunner.Builder builder;

    @BeforeClass
    public void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() {
      storageDirectory.mkdirs();
      builder = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS);
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws IOException {
      scriptRunner = null;
    }

    @Test
    public void testStandardInputRedirect() throws IOException {
      String scriptContents = "while read LINE; do\n" + "  echo ${LINE}\n" + "done\n";
      createScript(scriptFile, scriptContents);

      stdInContents.createNewFile();
      FileUtils.writeStringToFile(stdInContents, "Do not go gentle into that good night\n");

      scriptRunner = builder
          .redirectInput(ProcessBuilder.Redirect.from(stdInContents))
          .redirectOutput(ProcessBuilder.Redirect.to(stdOutLog))
          .build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is("Do not go gentle into that good night"));
    }

    @Test
    public void testStandardOutputRedirect() throws IOException {
      String scriptContents = ">&1 echo standard output\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = builder
          .redirectOutput(ProcessBuilder.Redirect.to(stdOutLog))
          .build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is("standard output"));
    }

    @Test
    public void testStandardErrorRedirect() throws IOException {
      String scriptContents = ">&2 echo standard error\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = builder
          .redirectError(ProcessBuilder.Redirect.to(stdErrLog))
          .build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdErrLog);
      assertThat(stdOutLogContents.trim(), is("standard error"));
    }

    @Test
    public void testRedirectErrorStream() throws IOException {
      String scriptContents = ">&2 echo standard error\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = builder
          .redirectOutput(ProcessBuilder.Redirect.to(stdOutLog))
          .redirectError(ProcessBuilder.Redirect.to(stdErrLog))
          .redirectErrorStream(true)
          .build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is("standard error"));

      String stdErrLogContents = FileUtils.readFileToString(stdErrLog);
      assertThat(stdErrLogContents.trim(), is(""));
    }
  }

  /**
   * This class implements tests for the call method when invoked directly.
   */
  public class CallTest {

    private final File scriptFile = new File(storageDirectory, "scriptFile.sh");

    private final List<String> baseCommand = Arrays.asList(scriptFile.getAbsolutePath());

    private final File stdOutLog = new File(storageDirectory, "stdOut.log");

    private final File stdErrLog = new File(storageDirectory, "stdErr.log");

    @BeforeClass
    public void setUpClass() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() {
      storageDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws IOException {
      scriptRunner = null;
    }

    @Test
    public void testSuccessfulCall() throws IOException {
      String scriptContents = ">&1 echo standard output\n" + ">&2 echo standard error\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS)
          .redirectOutput(ProcessBuilder.Redirect.to(stdOutLog))
          .redirectError(ProcessBuilder.Redirect.to(stdErrLog))
          .build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(0));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is("standard output"));

      String stdErrLogContents = FileUtils.readFileToString(stdErrLog);
      assertThat(stdErrLogContents.trim(), is("standard error"));
    }

    @Test
    public void testFailingCallScriptFailure() throws IOException {
      String scriptContents = "exit 1\n";
      createScript(scriptFile, scriptContents);
      scriptRunner = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS).build();

      int exitCode = scriptRunner.call();
      assertThat(exitCode, is(1));
    }

    @Test
    public void testFailingCallScriptTimeout() throws IOException {
      String scriptContents = "sleep 20\n";
      createScript(scriptFile, scriptContents);
      scriptRunner = new ScriptRunner.Builder(baseCommand, 1).build();

      try {
        scriptRunner.call();
      } catch (RuntimeException e) {
        assertThat(e.getMessage(), containsString("InterruptedException"));
      }
    }
  }

  /**
   * This class implements tests for the call method when invoked as a callable task.
   */
  public class CallableTest {

    private final File scriptFile = new File(storageDirectory, "scriptFile.sh");

    private final List<String> baseCommand = Arrays.asList(scriptFile.getAbsolutePath());

    private final File stdOutLog = new File(storageDirectory, "stdOut.log");

    private final File stdErrLog = new File(storageDirectory, "stdErr.log");

    private ExecutorService service;

    @BeforeClass
    public void setUpClass() throws IOException {
      service = Executors.newFixedThreadPool(1);
      FileUtils.deleteDirectory(storageDirectory);
    }

    @BeforeMethod
    public void setUpTest() {
      storageDirectory.mkdirs();
    }

    @AfterMethod
    public void tearDownTest() throws IOException {
      FileUtils.deleteDirectory(storageDirectory);
    }

    @AfterClass
    public void tearDownClass() throws IOException {
      service.shutdown();
      scriptRunner = null;
    }

    @Test
    public void testSuccessfulCall() throws InterruptedException, IOException {
      String scriptContents = ">&1 echo standard output\n" + ">&2 echo standard error\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS)
          .redirectOutput(ProcessBuilder.Redirect.to(stdOutLog))
          .redirectError(ProcessBuilder.Redirect.to(stdErrLog))
          .build();

      ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
      service.submit(futureTask);

      final CountDownLatch latch = new CountDownLatch(1);

      FutureCallback<Integer> futureCallback = new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {

          //
          // N.B. Assert failures on other threads appear not to cause the test case to fail.
          //      If this assert fails, then the observed behavior is that the latch.await call
          //      below will fail.
          //

          assertThat(result, is(0));
          latch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Callback failed: " + t.getMessage());
        }
      };

      Futures.addCallback(futureTask, futureCallback);
      assertTrue(latch.await(10, TimeUnit.SECONDS));

      String stdOutLogContents = FileUtils.readFileToString(stdOutLog);
      assertThat(stdOutLogContents.trim(), is("standard output"));

      String stdErrLogContents = FileUtils.readFileToString(stdErrLog);
      assertThat(stdErrLogContents.trim(), is("standard error"));
    }

    @Test
    public void testFailingCallScriptFailure() throws InterruptedException, IOException {
      String scriptContents = "exit 1\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = new ScriptRunner.Builder(baseCommand, DEFAULT_TIMEOUT_IN_SECONDS).build();
      ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
      service.submit(futureTask);

      final CountDownLatch latch = new CountDownLatch(1);

      FutureCallback<Integer> futureCallback = new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {

          //
          // N.B. Assert failures on other threads appear not to cause the test case to fail.
          //      If this assert fails, then the observed behavior is that the latch.await call
          //      below will fail.
          //

          assertThat(result, is(1));
          latch.countDown();
        }

        @Override
        public void onFailure(Throwable t) {
          fail("Callback failed: " + t.getMessage());
        }
      };

      Futures.addCallback(futureTask, futureCallback);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    public void testFailingCallScriptTimeout() throws InterruptedException, IOException {
      String scriptContents = "sleep 20\n";
      createScript(scriptFile, scriptContents);

      scriptRunner = new ScriptRunner.Builder(baseCommand, 1).build();
      ListenableFutureTask<Integer> futureTask = ListenableFutureTask.create(scriptRunner);
      service.submit(futureTask);

      final CountDownLatch latch = new CountDownLatch(1);

      FutureCallback<Integer> futureCallback = new FutureCallback<Integer>() {
        @Override
        public void onSuccess(Integer result) {
          fail("Callback succeeded unexpectedly with result: " + result.toString());
        }

        @Override
        public void onFailure(Throwable t) {

          //
          // N.B. Assert failures on other threads appear not to cause the test case to fail.
          //      If this assert fails, then the observed behavior is that the latch.await call
          //      below will fail.
          //

          assertThat(t.getMessage(), containsString("InterruptedException"));
          latch.countDown();
        }
      };

      Futures.addCallback(futureTask, futureCallback);
      assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
  }
}
