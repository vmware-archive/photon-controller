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

package com.vmware.photon.controller.api.frontend.resources.info;

import com.vmware.photon.controller.api.frontend.clients.DeploymentFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.frontend.resources.routes.InfoResourceRoutes;
import com.vmware.photon.controller.api.model.Deployment;
import com.vmware.photon.controller.api.model.Info;
import com.vmware.photon.controller.api.model.NetworkType;
import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * This resource provides support for Info related operations.
 */
@Path(InfoResourceRoutes.API)
@Api(value = InfoResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InfoResource {
  private static String gitCommitHash = null;
  private static final String MANIFEST_FILE = "/META-INF/MANIFEST.MF";
  private static final String GIT_COMMIT_ATTRIBUTE = "Git-Commit";
  private static final String UNKNOWN = "Unknown (dev environment)";

  private final DeploymentFeClient deploymentFeClient;

  @Inject
  public InfoResource(DeploymentFeClient deploymentFeClient) {
    this.deploymentFeClient = deploymentFeClient;
  }

  @GET
  @ApiOperation(
      value = "Information about the Photon Controller deployment",
      notes = "This API provides read-only information about the Photon Controller installation"
            + "including the version and whether or not software-defined network is enabled.",
      response = Info.class)
  @ApiResponses(
      value = {@ApiResponse(code = 200, message = "Returns the general information")})
  public Response get(@Context Request request) throws ExternalException {
    Info info = new Info();

    List<Deployment> deployments = deploymentFeClient.listAllDeployments().getItems();
    if (deployments.isEmpty() || deployments.size() > 1) {
      info.setNetworkType(NetworkType.NOT_AVAILABLE);
    } else {
      info.setNetworkType(deployments.get(0).getNetworkConfiguration().getSdnEnabled() ? NetworkType.SOFTWARE_DEFINED
          : NetworkType.PHYSICAL);
    }

    // We get our version information from our JAR's manifest
    // This doesn't exist in unit tests, since we're running from class files,
    // so we have defaults.
    info.setBaseVersion(this.getClass().getPackage().getSpecificationVersion());
    info.setFullVersion(this.getClass().getPackage().getImplementationVersion());
    info.setGitCommitHash(getGitCommitHash());
    if (info.getBaseVersion() == null) {
      info.setBaseVersion(UNKNOWN);
    }
    if (info.getFullVersion() == null) {
      info.setFullVersion(UNKNOWN);
    }
    if (info.getGitCommitHash() == null) {
      info.setGitCommitHash(UNKNOWN);
    }

    return generateCustomResponse(Response.Status.OK, info);
  }

  /**
   * Extract the git commit hash from the manifest file if possible, otherwise return null.
   *
   * See the build.gradle files to see how the git commit hash is stored
   */
  private static synchronized String getGitCommitHash() {

    if (gitCommitHash != null) {
      return gitCommitHash;
    }

    try {
      String classPath = findThisClassPath();
      gitCommitHash = extractGitHashFromManifest(classPath);
    } catch (Exception ex) {
      return null;
    }
    return gitCommitHash;
  }

  /**
   * Find the class path for this class. We use it to find the manifest.
   *
   * The classPath should look something like: jar:file:////usr/lib/photon.jar! I'm not sure why the exclamation is
   * there, but it is.
   */
  private static String findThisClassPath() throws IllegalStateException {

      Class<InfoResource> thisClass = InfoResource.class;
      String className = thisClass.getSimpleName() + ".class";
      String classPath = thisClass.getResource(className).toString();
      if (!classPath.startsWith("jar")) {
        // Can't find jar file, so can't find manifest, so can't find git commit hash
        throw new IllegalStateException();
      }
    return classPath;
  }

  /**
   * Given the class path for this class, find the manifest file and extract the git commit hash from it.
   */
  private static String extractGitHashFromManifest(String classPath) throws IOException {

    String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + MANIFEST_FILE;
    try (InputStream manifestStream = new URL(manifestPath).openStream()) {
      Manifest manifest = new Manifest(manifestStream);
      Attributes attr = manifest.getMainAttributes();
      return attr.getValue(GIT_COMMIT_ATTRIBUTE);
    }
  }
}
