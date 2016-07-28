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

package com.vmware.photon.controller.apife;

import com.vmware.photon.controller.api.model.ApiError;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Version;
import com.vmware.photon.controller.api.model.base.Base;
import com.vmware.photon.controller.apife.exceptions.external.ErrorCode;
import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import com.vmware.photon.controller.common.logging.LoggingUtils;

import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.server.ContainerRequest;
import static com.google.common.base.Preconditions.checkNotNull;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import java.net.URI;

/**
 * This class is responsible for generating ALL API responses.
 */
@Singleton
public class Responses {

  /**
   * Header field that will contain the request id.
   */
  public static final String REQUEST_ID_HEADER = "REQUEST-ID";

  /**
   * Header filed that will contain the name.
   */
  public static final String VERSION_HEADER = "X-API-VERSION";

  /**
   * Return a Response object that reflects a specified HTTP status and serialized body.
   *
   * @param responseStatus The status to send out
   * @param entity         The entity object to serialize and return in response
   * @return - a response object suitable for return to the caller
   */
  public static Response generateCustomResponse(Response.Status responseStatus, Base entity) {
    return generateCustomResponse(responseStatus, entity, null, null);
  }

  public static Response generateCustomResponse(Response.Status responseStatus, Base entity,
                                                ContainerRequest request, String selfLinkTemplate) {
    setEntitySelfLink(entity, request, selfLinkTemplate);

    return buildResponse(responseStatus, entity);
  }

  public static Response generateCustomResponseFromServlet(Response.Status responseStatus, Base entity,
                                                           HttpServletRequest request, String selfLinkTemplate) {
    setEntitySelfLink(entity, request, selfLinkTemplate);

    return buildResponse(responseStatus, entity);
  }

  public static Response generateCustomResponse(Response.Status responseStatus, Object entity) {
    return buildResponse(responseStatus, entity);
  }

  public static <T extends Base> Response generateResourceListResponse(Response.Status responseStatus,
                                                                       ResourceList<T> entities,
                                                                       ContainerRequest request,
                                                                       String selfLinkTemplate) {
    checkNotNull(entities);
    for (T entity : entities.getItems()) {
      setEntitySelfLink(entity, request, selfLinkTemplate);
    }

    return generateResourceListResponse(responseStatus, entities);
  }

  public static <T> Response generateResourceListResponse(Response.Status responseStatus,
                                                          ResourceList<T> entities) {
    checkNotNull(entities);

    Response.ResponseBuilder builder = Response
        .status(responseStatus)
        .entity(entities)
        .type(MediaType.APPLICATION_JSON);

    addResponseHeaders(builder);
    return builder.build();
  }

  /**
   * return a Response object that reflects the fact that an externally
   * visible exception occurred while processing the request.
   *
   * @param e - supplies the exception that generated the problem
   * @return - a response object suitable for return to the caller
   */
  public static Response externalException(ExternalException e) {
    ApiError error = new ApiError(e.getErrorCode(), e.getMessage(), e.getData());
    Response.ResponseBuilder builder = Response
        .status(e.getHttpStatus())
        .entity(error)
        .type(MediaType.APPLICATION_JSON);

    addResponseHeaders(builder);
    return builder.build();
  }

  /**
   * return an error response generated by a validation failure, invalid json, etc.
   *
   * @param e - the exception
   * @return - a response object suitable for return to the caller
   */
  public static Response invalidEntity(ConstraintViolationException e) {
    // hand create an external exception out of this and continue with normal
    // error response flow
    StringBuilder errorMessage = new StringBuilder();
    boolean firstPass = true;
    for (ConstraintViolation error : e.getConstraintViolations()) {
      if (!firstPass) {
        errorMessage.append(", ");
      }
      errorMessage.append(String.format("%s %s (was %s)", error.getPropertyPath(), error.getMessage(),
          error.getInvalidValue()));
      firstPass = false;
    }
    ExternalException externalException =
        new ExternalException(ErrorCode.INVALID_ENTITY, errorMessage.toString(), null);
    return externalException(externalException);
  }

  /**
   * return generic server error response with no other information transfer.
   *
   * @return - a response object suitable for return to the caller
   */
  public static Response serverError() {
    Response.ResponseBuilder builder = Response.serverError().entity("");
    addResponseHeaders(builder);
    return builder.build();
  }

  /**
   * return an error response generated by the jersey framework. this is typically
   * routing/404 issues, etc.
   *
   * @param e - the exception
   * @return - a response object suitable for return to the caller
   */
  public static Response jerseyError(WebApplicationException e) {
    if (e instanceof NotFoundException) {
      NotFoundException notFoundException = (NotFoundException) e;
      String errorMessage = null;
      if (notFoundException.getResponse().getLocation() != null) {
        errorMessage = notFoundException.getResponse().getLocation().toString();
      }
      // map 404 to NOT_FOUND_404 error
      ExternalException externalException = new ExternalException(ErrorCode.NOT_FOUND, errorMessage, null);
      return externalException(externalException);
    } else {
      // return generic jersey error
      Response.ResponseBuilder builder = Response.status(e.getResponse().getStatus()).entity("");
      addResponseHeaders(builder);
      return builder.build();
    }
  }

  private static void setEntitySelfLink(Base entity, ContainerRequest request, String selfLinkTemplate) {
    checkNotNull(entity);
    if (request != null && StringUtils.isNotBlank(selfLinkTemplate)) {
      String selfLink =
          UriBuilder
              .fromUri(request.getBaseUri())
              .path(selfLinkTemplate)
              .build(checkNotNull(entity.getId()))
              .toString();
      entity.setSelfLink(selfLink);
    } else if (request != null || StringUtils.isNotBlank(selfLinkTemplate)) {
      throw new IllegalArgumentException("Both request and selfLinkTemplate need to be provided!");
    }
  }

  private static void setEntitySelfLink(Base entity, HttpServletRequest request, String selfLinkTemplate) {
    checkNotNull(entity);
    if (request != null && StringUtils.isNotBlank(selfLinkTemplate)) {
      String selfLink =
          UriBuilder
              .fromUri(getBaseUri(request))
              .path(selfLinkTemplate)
              .build(checkNotNull(entity.getId()))
              .toString();
      entity.setSelfLink(selfLink);
    } else if (request != null || StringUtils.isNotBlank(selfLinkTemplate)) {
      throw new IllegalArgumentException("Both request and selfLinkTemplate need to be provided!");
    }
  }

  private static void addResponseHeaders(Response.ResponseBuilder builder) {
    // add version header
    builder.header(VERSION_HEADER, Version.VERSION);

    // add request id
    String requestId = LoggingUtils.getRequestId();
    if (StringUtils.isNotBlank(requestId)) {
      builder.header(REQUEST_ID_HEADER, requestId);
    }
  }

  private static Response buildResponse(Response.Status responseStatus, Object entity) {
    Response.ResponseBuilder builder = Response
        .status(responseStatus)
        .entity(entity)
        .type(MediaType.APPLICATION_JSON);

    addResponseHeaders(builder);
    return builder.build();
  }

  private static URI getBaseUri(HttpServletRequest request) throws IllegalArgumentException {
    String requestBaseUri = request.getScheme() + "://" +
        request.getServerName() + ":" + request.getServerPort();
    return UriBuilder.fromUri(requestBaseUri).build();
  }
}
