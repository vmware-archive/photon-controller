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

package com.vmware.photon.controller.apife.resources.image;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Image;
import com.vmware.photon.controller.api.model.ImageReplicationType;
import com.vmware.photon.controller.api.model.ResourceList;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.ImageFeClient;
import com.vmware.photon.controller.apife.config.PaginationConfig;
import com.vmware.photon.controller.apife.exceptions.external.ImageUploadException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.resources.routes.ImageResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.utils.PaginationUtils;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponseFromServlet;
import static com.vmware.photon.controller.api.common.Responses.generateResourceListResponse;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;

/**
 * This resource is for image related API.
 */
@Path(ImageResourceRoutes.API)
@Api(value = ImageResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ImagesResource {
  private static final Logger logger = LoggerFactory.getLogger(ImagesResource.class);

  private final ImageFeClient imageFeClient;
  private final PaginationConfig paginationConfig;

  @Inject
  public ImagesResource(ImageFeClient imageFeClient, PaginationConfig paginationConfig) {
    this.imageFeClient = imageFeClient;
    this.paginationConfig = paginationConfig;
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @ApiOperation(value = "Upload an image", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, image creation process can be fetched via the task")
  })
  public Response upload(@Context HttpServletRequest request)
      throws InternalException, ExternalException {
    return generateCustomResponseFromServlet(
        Response.Status.CREATED,
        parseImageDataFromRequest(request),
        request,
        TaskResourceRoutes.TASK_PATH);
  }

  @GET
  @ApiOperation(value = "Get all images' information",
      response = Image.class, responseContainer = ResourceList.CLASS_NAME)
  @ApiResponses(value = {
      @ApiResponse(code = 200, message = "List of Images")
  })
  public Response list(@Context Request request,
                       @QueryParam("name") Optional<String> name,
                       @QueryParam("pageSize") Optional<Integer> pageSize,
                       @QueryParam("pageLink") Optional<String> pageLink) throws ExternalException {

    ResourceList<Image> resourceList;
    if (pageLink.isPresent()) {
      resourceList = imageFeClient.getImagesPage(pageLink.get());
    } else {
      Optional<Integer> adjustedPageSize = PaginationUtils.determinePageSize(paginationConfig, pageSize);
      resourceList = imageFeClient.list(name, adjustedPageSize);
    }
    return generateResourceListResponse(
        Response.Status.OK,
        PaginationUtils.formalizePageLinks(resourceList, ImageResourceRoutes.API),
        (ContainerRequest) request,
        ImageResourceRoutes.IMAGE_PATH);
  }

  private Task parseImageDataFromRequest(HttpServletRequest request) throws InternalException, ExternalException {
    Task task = null;

    ServletFileUpload fileUpload = new ServletFileUpload();
    FileItemIterator iterator = null;
    InputStream itemStream = null;

    try {
      ImageReplicationType replicationType = null;

      iterator = fileUpload.getItemIterator(request);
      while (iterator.hasNext()) {
        FileItemStream item = iterator.next();
        itemStream = item.openStream();

        if (item.isFormField()) {
          String fieldName = item.getFieldName();
          switch (fieldName.toUpperCase()) {
            case "IMAGEREPLICATION":
              replicationType = ImageReplicationType.valueOf(Streams.asString(itemStream).toUpperCase());
              break;
            default:
              logger.warn(String.format("The parameter '%s' is unknown in image upload.", fieldName));
          }
        } else {
          if (replicationType == null) {
            throw new ImageUploadException(
                "ImageReplicationType is required and should be encoded before image data in the upload request.");
          }

          task = imageFeClient.create(itemStream, item.getName(), replicationType);
        }

        itemStream.close();
        itemStream = null;
      }
    } catch (IllegalArgumentException ex) {
      throw new ImageUploadException("Image upload receives invalid parameter", ex);
    } catch (IOException ex) {
      throw new ImageUploadException("Image upload IOException", ex);
    } catch (FileUploadException ex) {
      throw new ImageUploadException("Image upload FileUploadException", ex);
    } finally {
      flushRequest(iterator, itemStream);
    }

    if (task == null) {
      throw new ImageUploadException("There is no image stream data in the image upload request.");
    }

    return task;
  }

  private void flushRequest(FileItemIterator iterator, InputStream itemStream) {
    try {
      // close any streams left open due to error.
      if (itemStream != null) {
        itemStream.close();
      }

      // iterate through the remaining fields an flush the fields that contain the file data.
      while (null != iterator && iterator.hasNext()) {
        FileItemStream item = iterator.next();
        if (!item.isFormField()) {
          item.openStream().close();
        }
      }
    } catch (IOException | FileUploadException ex) {
      logger.warn("Unexpected exception flushing upload request.", ex);
    }
  }
}
