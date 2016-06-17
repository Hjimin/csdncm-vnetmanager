package org.iris4sdn.csdncm.vnetmanager.impl;

/**
 * Created by gurum on 16. 6. 17.
 */
import org.apache.felix.scr.annotations.*;
import org.iris4sdn.csdncm.vnetmanager.*;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineId;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineService;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.LogicalClockService;
import org.onosproject.store.service.StorageService;
import org.onosproject.vtnrsc.VirtualPortId;
import org.onosproject.vtnrsc.tenantnetwork.TenantNetworkService;
import org.onosproject.vtnrsc.virtualport.VirtualPortService;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.EnumSet;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.iris4sdn.csdncm.vnetmanager.OpenstackNode.State.*;
import static org.slf4j.LoggerFactory.getLogger;


@Component(immediate = true)
@Service
public class NodeManager implements NodeManagerService {
    private final Logger log = getLogger(getClass());

    private static final String OPENSTACK_NODE_NOT_NULL = "Openstack node cannot be null";
    private static final String EVENT_NOT_NULL = "VirtualMachine event cannot be null";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TenantNetworkService tenantNetworkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualPortService virtualPortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LogicalClockService clockService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualMachineService virtualMachineService;

    private static final BridgeHandler bridgeHandler = BridgeHandler.bridgeHandler();
    private static L2RuleInstaller installer;
    private static final String IFACEID = "ifaceid";
    private static final String OPENSTACK_NODES = "openstack-nodes";
    private static final String GATEWAY = "multi-gateway";
    private static final String CONTROLLER_IP_KEY = "ipaddress";
    private EventuallyConsistentMap<OpenstackNodeId, OpenstackNode> nodeStore;
    private EventuallyConsistentMap<Gateway, PortNumber> gatewayStore;


    @Activate
    public void activate() {
        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(OpenstackNodeId.class)
                .register(VirtualMachineId.class);

        nodeStore = storageService
                .<OpenstackNodeId, OpenstackNode>eventuallyConsistentMapBuilder()
                .withName(OPENSTACK_NODES).withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        gatewayStore = storageService
                .<Gateway, PortNumber>eventuallyConsistentMapBuilder()
                .withName(GATEWAY).withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

//        vmStore = storageService
//                .<Ip4Address, MacAddress>eventuallyConsistentMapBuilder()
//                .withName(GATEWAY).withSerializer(serializer)
//                .withTimestampProvider((k, v) -> clockService.getTimestamp())
//                .build();

        log.info("Started~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }

    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void addGateway(Gateway gateway) {
        //create gateway
        nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(BRIDGE_CREATED)))
                .forEach(e -> {
                    bridgeHandler.createGatewayTunnel(e, gateway);
                    gatewayStore.put(gateway, gateway.getGatewayPortNumber());
                });
    }


    @Override
    public void deleteGateway(Gateway gateway) {
        nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(GATEWAY_CREATED)))
                .forEach(e -> {
                    //TODO : destroyGatewayTunnel
                    gatewayStore.remove(gateway);
                });
    }

    @Override
    public Gateway getGateway(PortNumber inPort){
        return gatewayStore.keySet().stream()
                .filter(e -> {
                    if(gatewayStore.get(e).equals(inPort)) {
                        log.info("!!! {}", gatewayStore.get(e));
                        log.info("inport {}", inPort);
                        return true;
                    } else {
                        log.info("~~~~~~~~~~~ {}", gatewayStore.get(e));
                        log.info("inport {}", inPort);
                        return false;
                    }
                })
                .findFirst().orElse(null);
    }

    @Override
    public Iterable<Gateway> getGateways() {
        return Collections.unmodifiableCollection(gatewayStore.keySet());
    }
    @Override
    public Iterable<PortNumber> getGatewayPorts() {
        return Collections.unmodifiableCollection(gatewayStore.values());
    }

    @Override
    public void addOpenstackNode(OpenstackNode node) {
        checkNotNull(node, OPENSTACK_NODE_NOT_NULL);
        if(nodeStore.containsKey(node.id())) {
            log.info("Remove pre-configured openstack node {} ", node.id());
            nodeStore.remove(node.id());
        }
        log.info("Add configured openstack node {} using {}", node.id(), node.getManageNetworkIp());
        nodeStore.put(node.id(), node);
        node.applyState(CONFIGURED);
    }

    @Override
    public void deleteOpenstackNode(OpenstackNode node) {
        checkNotNull(node, OPENSTACK_NODE_NOT_NULL);
        nodeStore.remove(node.id());
    }

    @Override
    public Iterable<OpenstackNode> getOpenstackNodes() {
        return Collections.unmodifiableCollection(nodeStore.values());
    }

    @Override
    public OpenstackNode getOpenstackNode(DeviceId deviceId) {
        return nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(BRIDGE_CREATED)))
                .filter(e -> e.getBridgeId(Bridge.BridgeType.INTEGRATION).equals(deviceId)
                        || e.getBridgeId(Bridge.BridgeType.EXTERNAL).equals(deviceId))
                .findFirst().orElse(null);
    }

    @Override
    public OpenstackNode getOpenstackNode(String hostName) {
        checkNotNull(hostName);

        OpenstackNode node = nodeStore.values().stream()
                .filter(e -> e.id().equals(OpenstackNodeId.valueOf(hostName)))
                .findFirst().orElse(null);

        return node;
    }

    @Override
    public OpenstackNode getOpenstackNode(VirtualPortId virtualPortId) {
        return nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(BRIDGE_CREATED)))
                .filter(e -> e.getVirutalPortNumber(virtualPortId) != null)
                .findFirst().orElse(null);
    }

}
