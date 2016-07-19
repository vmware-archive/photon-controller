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

package com.vmware.photon.controller.apife.resources.vm;

import com.vmware.photon.controller.api.common.exceptions.external.ExternalException;
import com.vmware.photon.controller.api.model.Task;
import com.vmware.photon.controller.apife.clients.VmFeClient;
import com.vmware.photon.controller.apife.exceptions.external.IsoUploadException;
import com.vmware.photon.controller.apife.exceptions.internal.InternalException;
import com.vmware.photon.controller.apife.resources.routes.TaskResourceRoutes;
import com.vmware.photon.controller.apife.resources.routes.VmResourceRoutes;
import static com.vmware.photon.controller.api.common.Responses.generateCustomResponseFromServlet;

import com.google.inject.Inject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * This resource is for ISO attach related API.
 */
@Path(VmResourceRoutes.VM_ATTACH_ISO_PATH)
@Api(value = VmResourceRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.MULTIPART_FORM_DATA)
public class VmIsoAttachResource {
  private static final Logger logger = LoggerFactory.getLogger(VmIsoAttachResource.class);

  private final VmFeClient vmFeClient;

  @Inject
  public VmIsoAttachResource(VmFeClient vmFeClient) {
    this.vmFeClient = vmFeClient;
  }

  @POST
  @ApiOperation(value = "Attaches a ISO to the VM", response = Task.class)
  @ApiResponses(value = {
      @ApiResponse(code = 201, message = "Task created, iso attaching process can be fetched via the task")
  })
  public Response operate(@Context HttpServletRequest request,
                          @PathParam("id") String id)
      throws InternalException, ExternalException {
    return generateCustomResponseFromServlet(
        Response.Status.CREATED,
        parseIsoDataFromRequest(request, id),
        request,
        TaskResourceRoutes.TASK_PATH);
  }

  private Task parseIsoDataFromRequest(HttpServletRequest request, String id)
      throws InternalException, ExternalException {
    Task task = null;

    ServletFileUpload fileUpload = new ServletFileUpload();
    List<InputStream> dataStreams = new LinkedList<>();

    try {
      FileItemIterator iterator = fileUpload.getItemIterator(request);
      while (iterator.hasNext()) {
        FileItemStream item = iterator.next();
        if (item.isFormField()) {
          logger.warn(String.format("The parameter '%s' is unknown in attach ISO.", item.getFieldName()));
        } else {
          InputStream fileStream = item.openStream();
          dataStreams.add(fileStream);

          task = vmFeClient.attachIso(id, fileStream, item.getName());
        }
      }
    } catch (IOException ex) {
      throw new IsoUploadException("Iso upload IOException", ex);
    } catch (FileUploadException ex) {
      throw new IsoUploadException("Iso upload FileUploadException", ex);
    } finally {
      for (InputStream stream : dataStreams) {
        try {
          stream.close();
        } catch (IOException | NullPointerException ex) {
          logger.warn("Unexpected exception closing data stream.", ex);
        }
      }
    }

    if (task == null) {
      throw new IsoUploadException("There is no iso stream data in the iso upload request.");
    }

    return task;
  }
}
