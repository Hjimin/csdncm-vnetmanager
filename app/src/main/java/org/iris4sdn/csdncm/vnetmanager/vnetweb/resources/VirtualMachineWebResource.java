package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.*;
import org.iris4sdn.csdncm.vnetmanager.vnetweb.web.VirtualMachineCodec;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.vtnrsc.SegmentationId;
import org.onosproject.vtnrsc.TenantId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.OK;
import static org.onlab.util.Tools.nullIsNotFound;

@Path("virtual-machine")
public class VirtualMachineWebResource extends AbstractWebResource {
    private final Logger log = LoggerFactory
            .getLogger(VirtualMachineWebResource.class);

    private static final String PORT_NOT_FOUND = "Port is not found";
    private static final String VNI_NOT_NULL = "Segmentation ID cannot be null";
    private static final String JSON_NOT_NULL = "JsonNode can not be null";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listVirtualMachines() {
        Iterable<VirtualMachine> vms = get(VirtualMachineService.class)
                .getVirtualMachines();
        ObjectNode result = new ObjectMapper().createObjectNode();
        result.set("virtualmachines",
                new VirtualMachineCodec().encode(vms, this));
        return ok(result.toString()).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createVirtualMachine(InputStream input) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode cfg = mapper.readTree(input);
            Iterable<VirtualMachine> vms = decodeVirtualMachines(cfg);
            Boolean isSuccess = nullIsNotFound(get(VirtualMachineService.class)
                    .addVirtualMachine(vms), PORT_NOT_FOUND);
            if (!isSuccess) {
                return Response.status(INTERNAL_SERVER_ERROR)
                        .entity(VNI_NOT_NULL).build();
            }
            return Response.status(OK).entity(isSuccess.toString()).build();
        } catch (Exception e) {
            log.error("Creating VirtualMachine failed because of exception {}",
                    e.toString());
            return Response.status(INTERNAL_SERVER_ERROR).entity(e.toString())
                    .build();
        }
    }

    @Path("{vmID}")
    @DELETE
    public Response deletePorts(@PathParam("vmID") String id) {
        Set<VirtualMachineId> vmIds = new HashSet<>();
        try {
            if (id != null) {
                vmIds.add(VirtualMachineId.virtualMachineId(id));
            }
            Boolean isSuccess = nullIsNotFound(get(VirtualMachineService.class)
                    .deleteVirtualMachine(vmIds), PORT_NOT_FOUND);
            if (!isSuccess) {
                return Response.status(INTERNAL_SERVER_ERROR)
                        .entity(VNI_NOT_NULL).build();
            }
            return Response.status(OK).entity(isSuccess.toString()).build();
        } catch (Exception e) {
            log.error("Deletes VirtualMachine failed because of exception {}",
                    e.toString());
            return Response.status(INTERNAL_SERVER_ERROR).entity(e.toString())
                    .build();
        }
    }

    private Iterable<VirtualMachine> decodeVirtualMachines(JsonNode subnode)
            throws Exception {
        checkNotNull(subnode, JSON_NOT_NULL);
        Map<VirtualMachineId, VirtualMachine> vms = new HashMap<>();

        if (!subnode.hasNonNull("lvn_provider:segmentation_id")) {
            throw new IllegalArgumentException("Segmentation Id should not be null");
        } else if (subnode.get("lvn_provider:segmentation_id").asText().isEmpty()) {
            throw new IllegalArgumentException("Segmentation Id should not be empty");
        }

        SegmentationId segmentationId = SegmentationId.segmentationId(
                subnode.get("lvn_provider:segmentation_id").asText());

        if (!subnode.hasNonNull("subnets")) {
            throw new IllegalArgumentException("Subnets should not be null");
        }
        JsonNode subnets = subnode.get("subnets");

        for (JsonNode subnet : subnets) {
            if (!subnet.hasNonNull("ports")) {
                throw new IllegalArgumentException("Ports should not be null");
            }
            JsonNode ports = subnet.get("ports");

            for (JsonNode port : ports) {

                if (!port.hasNonNull("tenant_id")) {
                    throw new IllegalArgumentException("Tenant Id should not be null");
                } else if (port.get("tenant_id").asText().isEmpty()) {
                    throw new IllegalArgumentException("Tenant should not be empty");
                }
                TenantId tenantId = TenantId.tenantId(
                        port.get("tenant_id").asText());

                if (!port.hasNonNull("device_ip")) {
                    throw new IllegalArgumentException("device_ip should not be null");
                } else if (port.get("device_ip").asText().isEmpty()) {
                    throw new IllegalArgumentException("device_ip should not be empty");
                }
                IpAddress ipAddress = IpAddress.valueOf(port.get("device_ip").asText());

		String name = null;

                if (!port.hasNonNull("device_name")) {
                    throw new IllegalArgumentException("device_name should not be null");
                } else if (port.get("device_name").asText().isEmpty()) {
//                    throw new IllegalArgumentException("device_name should not be empty");
                } else {
                	name = port.get("device_name").asText();
		}

                if (!port.hasNonNull("device_id")) {
                    throw new IllegalArgumentException("device_id should not be null");
                } else if (port.get("device_id").asText().isEmpty()) {
                    throw new IllegalArgumentException("device_id should not be empty");
                }
                DeviceId deviceId = DeviceId.deviceId(port.get("device_id").asText());
                VirtualMachineId id = VirtualMachineId.virtualMachineId(port.get("device_id").asText());

                if (!port.hasNonNull("device_mac")) {
                    throw new IllegalArgumentException("device_mac should not be null");
                } else if (port.get("device_mac").asText().isEmpty()) {
                    throw new IllegalArgumentException("device_mac should not be empty");
                }
                MacAddress mac = MacAddress.valueOf(port.get("device_mac").asText());

                VirtualMachine vm = new DefaultVirtualMachine(id, segmentationId,tenantId,
                        ipAddress, name, mac, deviceId);

                vms.put(id, vm);
            }
        }

        return Collections.unmodifiableCollection(vms.values());
    }

}
