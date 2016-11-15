package com.vmware.photon.controller.api.frontend.resources.admin;

import com.google.inject.Inject;
import com.vmware.photon.controller.api.frontend.clients.TenantFeClient;
import com.vmware.photon.controller.api.frontend.exceptions.external.TenantNotFoundException;
import com.vmware.photon.controller.api.frontend.resources.routes.AdminRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.HostResourceRoutes;
import com.vmware.photon.controller.api.frontend.resources.routes.TenantResourceRoutes;
import com.vmware.photon.controller.api.model.Admin;
import com.vmware.photon.controller.api.model.Host;
import com.vmware.photon.controller.common.xenon.XenonRestClient;
import com.vmware.photon.controller.common.xenon.exceptions.*;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.services.common.NodeGroupService;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import net.minidev.json.JSONObject;
import org.glassfish.jersey.server.ContainerRequest;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import java.util.concurrent.TimeoutException;

import static com.vmware.photon.controller.api.frontend.Responses.generateCustomResponse;

/**
 * /admin/data-quorum
 */



@Path(AdminRoutes.DATA_QUORUM_PATH)
@Api(value = AdminRoutes.API)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DataQuorumResource {

    @Inject
    public DataQuorumResource(){
    }
//    @Inject
//    private XenonRestClient xenonClient;

    @GET
    @ApiOperation(value = "Print hello world", response = Admin.class)
    @ApiResponses(value = {@ApiResponse(code = 200 , message = "Hello World Representation" )})
    public Response get(@Context Request request, @PathParam("id") String id) {

//        Operation operation = xenonClient.get("/core/node-group/default");
//        NodeGroupService.NodeGroupState body = operation.getBody(NodeGroupService.NodeGroupState.class);
//        body.nodes.get("").membershipQuorum;

        String output = "hello world" + id;
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("Hi", output);
        String result = "The result is :" +jsonObject;
//        return Response.status(Response.Status.OK).entity(result).build();
        return generateCustomResponse(Response.Status.OK, result);
    }

//public void post(Object data) {
//
//}
}
