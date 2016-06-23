package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.Lists;
import org.iris4sdn.csdncm.vnetmanager.gateway.Gateway;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayService;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

@Path("/gateway")
public class GatewayConfig extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(GatewayConfig.class);
    GatewayService gatewayService = getService(GatewayService.class);

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
            ArrayNode gatewaynode = (ArrayNode) cfg.get("gateways");
            Iterator<JsonNode> iterator = gatewaynode.elements();

            String value = "haha";
            if(decodeConfigFile(iterator)){
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

    private boolean decodeConfigFile(Iterator<JsonNode> gateways) throws Exception {

//        for (JsonNode gateway : gateways) {

        List<Gateway> gatewayList = Lists.newArrayList();

        while(gateways.hasNext()) {
            JsonNode gateway = gateways.next();

            if (!gateway.hasNonNull("macAddress")) {
                throw new IllegalArgumentException("macAddress should not be null");
            } else if (gateway.get("macAddress").asText().isEmpty()) {
                throw new IllegalArgumentException("macAddress should not be empty");
            }
            MacAddress macAddress = MacAddress.valueOf(gateway.get("macAddress").asText());

            if (!gateway.hasNonNull("gatewayName")) {
                throw new IllegalArgumentException("gatewayName should not be null");
            } else if (gateway.get("gatewayName").asText().isEmpty()) {
                throw new IllegalArgumentException("gatewayName should not be empty");
            }
            String gatewayName = gateway.get("gatewayName").asText();

            if (!gateway.hasNonNull("dataNetworkIp")) {
                throw new IllegalArgumentException("dataNetworkIp should not be null");
            } else if (gateway.get("dataNetworkIp").asText().isEmpty()) {
                throw new IllegalArgumentException("dataNetworkIp should not be empty");
            }
            IpAddress dataNetworkIp = IpAddress.valueOf(gateway.get("dataNetworkIp").asText());

            if (!gateway.hasNonNull("weight")) {
                throw new IllegalArgumentException("weight should not be null");
            } else if (gateway.get("weight").asInt() < 0) {
                throw new IllegalArgumentException("weight should not be under zero");
            }
            short weight = (short) gateway.get("weight").asInt();

            Gateway default_gateway = new Gateway(gatewayName, macAddress, dataNetworkIp, weight);
            gatewayList.add(default_gateway);
        }

        gatewayService.addGateway(gatewayList);
        return true;
    }
}
