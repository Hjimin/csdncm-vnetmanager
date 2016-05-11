/*
 * Copyright 2014-2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNode;
import org.iris4sdn.csdncm.vnetmanager.VnetManagerService;
import org.onlab.packet.IpAddress;
import org.onosproject.incubator.net.tunnel.Tunnel;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

import static com.google.common.base.Preconditions.checkNotNull;


@Path("/gggg")
public class OpenstackConfig extends AbstractWebResource {

    private final Logger log = LoggerFactory.getLogger(OpenstackConfig.class);

    public static final String OPENSTACK_NODES = "openstackNodes";
    public static final String HOSTNAME = "hostName";
    public static final String PUBLIC_NETWORK_IP = "publicNetworkIp";
    public static final String MANAGE_NETWORK_IP = "manageNetworkIp";
    public static final String DATA_NETWORK_IP = "dataNetworkIp";
    public static final String TUNNEL_TYPE = "tunnelType";
    public static final String NODE_TYPE = "nodeType";


//    private final VnetManagerService vnetManagerService = getService(VnetManagerService.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listVirtualMachines() {
        log.info("hahahahahhahhahahahahahahhahahahhahahah");
        return Response.status(Response.Status.OK).entity("haha").build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createVirtualMachine(InputStream input) {
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
        if (!subnode.hasNonNull("hostName")) {
            throw new IllegalArgumentException("hostName should not be null");
        } else if (subnode.get("hostName").asText().isEmpty()) {
            throw new IllegalArgumentException("hostName should not be empty");
        }
        String hostName = subnode.get("hostName").asText();

        if (!subnode.hasNonNull("publicNetworkIp")) {
            throw new IllegalArgumentException("publicNetworkIp should not be null");
        } else if (subnode.get("publicNetworkIp").asText().isEmpty()) {
            throw new IllegalArgumentException("publicNetworkIp should not be empty");
        }
        IpAddress publicNetworkIp = IpAddress.valueOf(subnode.get("publicNetworkIp").asText());

        if (!subnode.hasNonNull("manageNetworkIp")) {
            throw new IllegalArgumentException("manageNetworkIp should not be null");
        } else if (subnode.get("manageNetworkIp").asText().isEmpty()) {
            throw new IllegalArgumentException("manageNetworkIp should not be empty");
        }
        IpAddress manageNetworkIp = IpAddress.valueOf(subnode.get("manageNetworkIp").asText());

        if (!subnode.hasNonNull("dataNetworkIp")) {
            throw new IllegalArgumentException("dataNetworkIp should not be null");
        } else if (subnode.get("dataNetworkIp").asText().isEmpty()) {
            throw new IllegalArgumentException("dataNetworkIp should not be empty");
        }
        IpAddress dataNetworkIp = IpAddress.valueOf(subnode.get("dataNetworkIp").asText());

        if (!subnode.hasNonNull("tunnelType")) {
            throw new IllegalArgumentException("tunnelType should not be null");
        } else if (subnode.get("tunnelType").asText().isEmpty()) {
            throw new IllegalArgumentException("tunnelType should not be empty");
        }
        Tunnel.Type tunnelType = Tunnel.Type.valueOf(subnode.get("tunnelType").asText().toUpperCase());

        if (!subnode.hasNonNull("nodeType")) {
            throw new IllegalArgumentException("nodeType should not be null");
        } else if (subnode.get("nodeType").asText().isEmpty()) {
            throw new IllegalArgumentException("nodeType should not be empty");
        }
        OpenstackNode.Type nodeType = OpenstackNode.Type.valueOf(subnode.get("nodeType").asText().toUpperCase());

        OpenstackNode openstackNode = new OpenstackNode(hostName, publicNetworkIp,
                manageNetworkIp, dataNetworkIp, tunnelType, nodeType);
        log.info("openstack node {} ", openstackNode.name());
        VnetManagerService vnetManagerService = getService(VnetManagerService.class);
        vnetManagerService.addOpenstackNode(openstackNode);
        return true;
    }

//    public Set<OpenstackNodeConfig> openstackNodes() {
//        return nodes;
//    }


    public static class OpenstackNodeConfig {
        private final String hostName;
        private final IpAddress publicNetworkIp;
        private final IpAddress manageNetworkIP;
        private final IpAddress dataNetworkIP;
        private final Tunnel.Type tunnelType;
        private final OpenstackNode.Type nodeType;

        public OpenstackNodeConfig(String hostName, IpAddress publicNetworkIp,
                                   IpAddress manageNetworkIP, IpAddress dataNetworkIP,
                             Tunnel.Type tunnelType, OpenstackNode.Type nodeType) {
            this.hostName = checkNotNull(hostName);
            this.publicNetworkIp = checkNotNull(publicNetworkIp);
            this.manageNetworkIP = checkNotNull(manageNetworkIP);
            this.dataNetworkIP = checkNotNull(dataNetworkIP);
            this.tunnelType = checkNotNull(tunnelType);
            this.nodeType = checkNotNull(nodeType);
        }

        public String hostName() {
            return hostName;
        }

        public IpAddress getPublicNetworkIp() {
            return publicNetworkIp;
        }

        public IpAddress getManageNetworkIP() {
            return manageNetworkIP;
        }

        public IpAddress getDataNetworkIP() {
            return dataNetworkIP;
        }

        public Tunnel.Type getTunnelType() { return tunnelType; }

        public OpenstackNode.Type getNodeType() { return nodeType; }
    }
}
