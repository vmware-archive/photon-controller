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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiModelProperty;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Helper test class implementing a resource with multiple paths.
 */
@Path("/test")
@Api(value = "/test")
public class TestMultiplePaths {
  @Path("/pathone")
  @GET
  @ApiOperation(value = "Get path one.", response = SomeClass3.class)
  public SomeClass2 getPathOne(@ApiParam(required = true, value = "The description", allowableValues = "Val1,Val2",
      defaultValue = "Val1") @PathParam("foo") String foo) {
    return new SomeClass3();
  }

  @Path("/pathtwo")
  @POST
  @ApiOperation(value = "Get path two.")
  public List<SomeClass> getPathTwo(@ApiParam(required = true) @QueryParam("foo") HashMap<String, String> foo) {
    return new ArrayList<>();
  }

  @Path("/paththree")
  @POST
  @ApiOperation(value = "Get path three.",
      response = SomeClass4.class, responseContainer = "ResourceList")
  public Response getPathThree(@ApiParam(required = true) @QueryParam("foo") List<String> foo) {
    return Response
        .status(Response.Status.OK)
        .entity(new ArrayList<SomeClass4>())
        .type(MediaType.APPLICATION_JSON)
        .build();
  }

  /**
   * Sample class.
   */
  public class SomeClass {
    @JsonProperty
    @ApiModelProperty(value = "This is someProperty.", allowableValues = "allowable1,allowable2", required = true)
    private String someProperty;
  }

  /**
   * Sample class.
   */
  public class SomeClass2 {
    @JsonProperty
    @ApiModelProperty(value = "This is someProperty.", allowableValues = "allowable1,allowable2", required = true)
    private String someProperty;
  }

  /**
   * Sample class.
   */
  public class SomeClass3 extends SomeClass2 {
    @JsonProperty
    @ApiModelProperty(value = "This is name", required = true)
    private int name;
  }

  /**
   * Sample class.
   */
  public class SomeClass4 {
    @JsonProperty
    @ApiModelProperty(value = "This is collection of primitive", required = true)
    private List<Integer> ids;

    @JsonProperty
    @ApiModelProperty(value = "This is nested collection of model", required = true)
    private List<SomeClass3> class3s;

    @JsonProperty
    @ApiModelProperty(value = "This is nested model", required = false)
    private SomeClass someClass;
  }
}
