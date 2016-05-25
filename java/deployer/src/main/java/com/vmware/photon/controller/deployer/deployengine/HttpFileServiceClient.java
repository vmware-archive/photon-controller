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

import com.vmware.photon.controller.deployer.service.exceptions.InvalidLoginException;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.protocol.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.Callable;

/**
 * This class implements a client for the vSphere HTTP file service interface.
 */
public class HttpFileServiceClient {

  private static final Logger logger = LoggerFactory.getLogger(HttpFileServiceClient.class);

  private String hostAddress;
  private HttpsURLConnection httpConnection;
  private String password;
  private String userName;

  public HttpFileServiceClient(String hostAddress,
                               String userName,
                               String password) {
    this.hostAddress = hostAddress;
    this.password = password;
    this.userName = userName;
  }

  public Callable<Integer> uploadFile(String sourceFilePath, String destinationPath) {
    return uploadFile(sourceFilePath, destinationPath, true);
  }

  public Callable<Integer> uploadFile(String sourceFilePath, String destinationPath, boolean shouldOverride) {
    checkNotNull(sourceFilePath);
    checkNotNull(destinationPath);

    File sourceFile = new File(sourceFilePath);
    if (!sourceFile.exists() || !sourceFile.isFile()) {
      throw new IllegalArgumentException("Source file must exist");
    }

    if (!destinationPath.startsWith("/tmp")) {
      throw new IllegalArgumentException("Destination path must be under /tmp when no datastore is specified");
    }

    return () -> {
      URL destinationURL = new URL("https", this.hostAddress, destinationPath);

      if (!shouldOverride) {
        HttpsURLConnection urlConnection = createHttpConnection(destinationURL, "HEAD");
        if (urlConnection.getResponseCode() == HttpsURLConnection.HTTP_OK) {
          logger.info("File {} already exists", destinationURL.toString());
          return HttpsURLConnection.HTTP_OK;
        }
      }
      logger.info("Uploading file {} to destination URL {}", sourceFile.getAbsolutePath(), destinationURL.toString());
      return performFileUpload(sourceFile, destinationURL);
    };
  }

  public Callable<Integer> uploadFileToDatastore(String sourceFilePath, String dsName, String dsPath) {
    return uploadFileToDatastore(sourceFilePath, "ha-datacenter", dsName, dsPath);
  }

  public Callable<Integer> uploadFileToDatastore(String sourceFilePath, String dcPath, String dsName, String dsPath) {
    checkNotNull(sourceFilePath);
    checkNotNull(dcPath);
    checkNotNull(dsName);
    checkNotNull(dsPath);

    File sourceFile = new File(sourceFilePath);
    if (!sourceFile.exists() || !sourceFile.isFile()) {
      throw new IllegalArgumentException("Source file must exist");
    }

    if (!dsPath.startsWith("/")) {
      throw new IllegalArgumentException("Destination path must be absolute");
    }

    return () -> {
      String destinationPath = "/folder" + dsPath + "?dcPath=" + dcPath + "&dsName=" + dsName;
      URL destinationURL = new URL("https", this.hostAddress, destinationPath);
      logger.info("Uploading file {} to destination URL {}", sourceFile.getAbsolutePath(), destinationURL.toString());
      return performFileUpload(sourceFile, destinationURL);
    };
  }

  public Callable<Integer> getDirectoryListingOfDatastores() {
    return getDirectoryListingOfDatastores("ha-datacenter");
  }

  private Callable<Integer> getDirectoryListingOfDatastores(String dcPath) {
    checkNotNull(dcPath);

    return () -> {
      String destinationPath = "/folder" + "?dcPath=" + dcPath;
      URL destinationURL = new URL("https", this.hostAddress, destinationPath);
      logger.info("Getting directory listing of all datastores available at {}", destinationURL.toString());

      if (null == this.httpConnection) {
        this.httpConnection = createHttpConnection(destinationURL, "GET");
      }

      int responseCode = httpConnection.getResponseCode();
      if (responseCode == HttpsURLConnection.HTTP_UNAUTHORIZED) {
        logger.info("Getting directory listing of all datastores available at {} failed with HTTP response code {}",
            destinationURL.toString(), responseCode);
        throw new InvalidLoginException();
      }

      logger.info("Getting directory listing of all datastores available at {} returned HTTP response code {}",
          destinationURL.toString(), responseCode);
      return responseCode;
    };
  }

  public Callable<Integer> deleteFileFromDatastore(String dsName, String dsPath) {
    return deleteFileFromDatastore("ha-datacenter", dsName, dsPath);
  }

  public Callable<Integer> deleteFileFromDatastore(String dcPath, String dsName, String dsPath) {
    checkNotNull(dcPath);
    checkNotNull(dsName);
    checkNotNull(dsPath);

    if (!dsPath.startsWith("/")) {
      throw new IllegalArgumentException("Destination path must be absolute");
    }

    return () -> {
      String destinationPath = "/folder" + dsPath + "?dcPath=" + dcPath + "&dsName=" + dsName;
      URL destinationURL = new URL("https", this.hostAddress, destinationPath);
      logger.info("Deleting file at URL {}", destinationURL.toString());

      if (null == this.httpConnection) {
        this.httpConnection = createHttpConnection(destinationURL, "DELETE");
      }

      int responseCode = httpConnection.getResponseCode();
      logger.info("Deleting file at URL {} returned HTTP response code {}", destinationURL.toString(), responseCode);
      if (responseCode != HttpsURLConnection.HTTP_NO_CONTENT) {
        throw new RuntimeException(String.format("Deleting file at URL %s failed with HTTP response %d",
            destinationURL.toString(), httpConnection.getResponseCode()));
      }

      return responseCode;
    };
  }

  @VisibleForTesting
  protected void setHttpConnection(HttpsURLConnection httpConnection) {
    this.httpConnection = httpConnection;
  }

  @VisibleForTesting
  protected String getHostAddress() {
    return this.hostAddress;
  }

  @VisibleForTesting
  protected String getPassword() {
    return this.password;
  }

  @VisibleForTesting
  protected String getUserName() {
    return this.userName;
  }

  private HttpsURLConnection createHttpConnection(URL destinationURL, String requestMethod) throws Exception {

    final TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
          }

          @Override
          public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return null;
          }
        }
    };

    final HostnameVerifier trustAllHostnames = (String hostname, SSLSession sslSession) -> true;

    SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustAllCerts, new SecureRandom());

    String authType = "Basic " + new String(Base64.encodeBase64((this.userName + ":" + this.password).getBytes()));

    HttpsURLConnection httpConnection = (HttpsURLConnection) destinationURL.openConnection();
    httpConnection.setSSLSocketFactory(sslContext.getSocketFactory());
    httpConnection.setHostnameVerifier(trustAllHostnames);
    httpConnection.setRequestMethod(requestMethod);
    httpConnection.setRequestProperty("Authorization", authType);
    return httpConnection;
  }

  private int performFileUpload(File sourceFile, URL destinationURL) throws Exception {

    if (null == this.httpConnection) {
      this.httpConnection = createHttpConnection(destinationURL, "PUT");
      this.httpConnection.setDoOutput(true);
      this.httpConnection.setRequestProperty(HTTP.CONTENT_LEN, Long.toString(sourceFile.length()));
    }

    InputStream inputStream = null;
    OutputStream outputStream = null;
    try {
      inputStream = new FileInputStream(sourceFile);
      outputStream = this.httpConnection.getOutputStream();
      IOUtils.copy(inputStream, outputStream);
    } finally {
      if (inputStream != null) {
        inputStream.close();
      }
      if (outputStream != null) {
        outputStream.close();
      }
    }

    int responseCode = httpConnection.getResponseCode();
    logger.info("Uploading file {} to URL {} returned HTTP response code {}", sourceFile.getAbsolutePath(),
        destinationURL.toString(), responseCode);
    // HTTP_OK is returned when the file is already there
    if (responseCode != HttpsURLConnection.HTTP_CREATED && responseCode != HttpsURLConnection.HTTP_OK) {
      throw new RuntimeException(String.format("Uploading file %s to URL %s failed with HTTP response %d",
          sourceFile.getAbsolutePath(), destinationURL.toString(), httpConnection.getResponseCode()));
    }
    return responseCode;
  }
}
