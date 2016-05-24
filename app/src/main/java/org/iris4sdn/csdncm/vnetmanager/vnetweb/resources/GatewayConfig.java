package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.iris4sdn.csdncm.vnetmanager.Gateway;
import org.iris4sdn.csdncm.vnetmanager.VnetManagerService;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

@Path("/gateway")
public class GatewayConfig extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(GatewayConfig.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listGateway() {
        log.info("hahahahahhahhahahahahahahhahahahhahahah");
        return Response.status(Response.Status.OK).entity("haha").build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGateway(InputStream input) {
        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode cfg = mapper.readTree(input);
            String value = "haha";
            if(decodeConfigFile(cfg)){
                log.info("Configuration Added");
                value = "success";
            } else {
                value = "fail";
            }
            return Response.status(Response.Status.OK).entity(value).build();
        } catch(Exception e){
            log.error("Exception {} ", e.toString());
            return Response.status(Response.Status.OK).entity("exception").build();
        }
    }

    private boolean decodeConfigFile(JsonNode subnode) throws Exception {

        if (!subnode.hasNonNull("macAddress")) {
            throw new IllegalArgumentException("macAddress should not be null");
        } else if (subnode.get("macAddress").asText().isEmpty()) {
            throw new IllegalArgumentException("macAddress should not be empty");
        }
        MacAddress macAddress = MacAddress.valueOf(subnode.get("macAddress").asText());

        if (!subnode.hasNonNull("dataNetworkIp")) {
            throw new IllegalArgumentException("dataNetworkIp should not be null");
        } else if (subnode.get("dataNetworkIp").asText().isEmpty()) {
            throw new IllegalArgumentException("dataNetworkIp should not be empty");
        }
        IpAddress dataNetworkIp = IpAddress.valueOf(subnode.get("dataNetworkIp").asText());

        Gateway gateway = new Gateway(macAddress, dataNetworkIp);
        log.info("here111111111111111111111111111!! {}", gateway);
        VnetManagerService vnetManagerService = getService(VnetManagerService.class);
        log.info("here222222222222222222222222222!! {}", vnetManagerService);
        vnetManagerService.addGateway(gateway);
        log.info("here333333333333333333333333333!!");
        return true;
    }
}
