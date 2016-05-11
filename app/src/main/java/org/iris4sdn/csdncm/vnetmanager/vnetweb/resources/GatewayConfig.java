package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/gateway")
public class GatewayConfig extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(OpenstackConfig.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listGateway() {
        log.info("hahahahahhahhahahahahahahhahahahhahahah");
        return Response.status(Response.Status.OK).entity("haha").build();
    }
}
