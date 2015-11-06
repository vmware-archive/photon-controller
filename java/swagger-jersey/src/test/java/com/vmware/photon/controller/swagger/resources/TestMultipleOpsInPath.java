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

package com.vmware.photon.controller.swagger.resources;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;

import java.util.HashMap;

/**
 * Helper test class implementing a resource with multiple operations tied to the same path.
 */
@Path("/test")
@Api(value = "/test")
public class TestMultipleOpsInPath {

  @Path("/path")
  @GET
  @ApiOperation(value = "Get path one.")
  public int getPathOne(
      @Context Request request,
      @ApiParam(required = true, value = "The description", allowableValues = "Val1,Val2",
          defaultValue = "Val1") @PathParam("foo") String foo) {
    return 1;
  }

  @Path("/path")
  @POST
  @ApiOperation(value = "Post path two.")
  public int postPathOne(@ApiParam(required = true) @QueryParam("foo") HashMap<String, String> foo) {
    return 2;
  }
}
