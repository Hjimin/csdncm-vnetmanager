package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayService;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.Iterator;

@Path("/gwcluster")
public class GatewayConfig extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(GatewayConfig.class);
    GatewayService gatewayService = getService(GatewayService.class);

    private final String GW_ID = "gw_id";
    private final String GW_NAME = "gw_name";
    private final String GW_MAC = "gw_mac";
    private final String GW_IP = "gw_ip";
    private final String WEIGHT = "weight";
    private final String ACTIVE = "active";
    private final String UPDATED = "updated";

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGateway(InputStream input) {
        try{
            ObjectMapper mapper = new ObjectMapper();
            JsonNode cfg = mapper.readTree(input);
            ArrayNode gatewaynode = (ArrayNode) cfg.get("gw_clusters");
            Iterator<JsonNode> iterator = gatewaynode.elements();

            String value = "haha";
            if(decodeConfigFile(iterator)){
                log.info("Gateway configuration Added");
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
        while(gateways.hasNext()) {
            JsonNode gateway = gateways.next();
            if (!gateway.hasNonNull(GW_ID)) {
                throw new IllegalArgumentException(GW_ID + " should not be null");
            } else if (gateway.get(GW_ID).asInt() < 0) {
                throw new IllegalArgumentException(GW_ID +" should not be under zero");
            }
            String gw_id = gateway.get(GW_ID).asText();

            if (!gateway.hasNonNull(GW_NAME)) {
                throw new IllegalArgumentException(GW_NAME + " should not be null");
            } else if (gateway.get(GW_NAME).asInt() < 0) {
                throw new IllegalArgumentException(GW_NAME +" should not be under zero");
            }
            String gw_name = gateway.get(GW_NAME).asText();

            if (!gateway.hasNonNull(GW_MAC)) {
                throw new IllegalArgumentException(GW_MAC + " should not be null");
            } else if (gateway.get(GW_MAC).asText().isEmpty()) {
                throw new IllegalArgumentException(GW_MAC + " should not be empty");
            }
            MacAddress gw_mac = MacAddress.valueOf(gateway.get(GW_MAC).asText());

            if (!gateway.hasNonNull(GW_IP)) {
                throw new IllegalArgumentException(GW_IP + " should not be null");
            } else if (gateway.get(GW_IP).asText().isEmpty()) {
                throw new IllegalArgumentException(GW_IP+" should not be empty");
            }
            IpAddress gw_ip = IpAddress.valueOf(gateway.get(GW_IP).asText());

            if (!gateway.hasNonNull(WEIGHT)) {
                throw new IllegalArgumentException(WEIGHT + " should not be null");
            } else if (gateway.get(WEIGHT).asInt() < 0) {
                throw new IllegalArgumentException(WEIGHT + " should not be under zero");
            }
            short weight = (short) gateway.get(WEIGHT).asInt();

            if (!gateway.hasNonNull(ACTIVE)) {
                throw new IllegalArgumentException(ACTIVE + " should not be null");
            } else if (gateway.get(ACTIVE).asInt() < 0) {
                throw new IllegalArgumentException(ACTIVE +" should not be under zero");
            }
            String active = gateway.get(ACTIVE).asText();


            if (!gateway.hasNonNull(UPDATED)) {
                throw new IllegalArgumentException(UPDATED + " should not be null");
            } else if (gateway.get(UPDATED).asInt() < 0) {
                throw new IllegalArgumentException(UPDATED +" should not be under zero");
            }
            boolean updated = gateway.get(UPDATED).asBoolean();

            gatewayService.createGateway(gw_id, gw_name, gw_mac, gw_ip, weight, active, updated);
        }
        return true;
    }
}
