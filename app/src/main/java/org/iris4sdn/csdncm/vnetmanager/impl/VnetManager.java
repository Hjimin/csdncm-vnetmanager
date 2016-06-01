package org.iris4sdn.csdncm.vnetmanager.impl;

import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.iris4sdn.csdncm.vnetmanager.*;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.*;
import org.onlab.packet.*;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.LogicalClockService;
import org.onosproject.store.service.StorageService;
import org.onosproject.vtnrsc.SegmentationId;
import org.onosproject.vtnrsc.TenantNetwork;
import org.onosproject.vtnrsc.VirtualPort;
import org.onosproject.vtnrsc.VirtualPortId;
import org.onosproject.vtnrsc.tenantnetwork.TenantNetworkService;
import org.onosproject.vtnrsc.virtualport.VirtualPortService;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.iris4sdn.csdncm.vnetmanager.OpenstackNode.State.*;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Virtual Network Manager.
 */
@Component(immediate = true)
@Service
public class VnetManager implements VnetManagerService {
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
    private final ExecutorService eventExecutor = Executors
            .newFixedThreadPool(1, groupedThreads("onos/vnetmanager", "event-handler"));
    private ApplicationId appId;
    private DeviceListener deviceListener = new InnerDeviceListener();
    private HostListener hostListener = new InnerHostListener();

    private VirtualMachineListener virtualMachineListener = new InnerVirtualMachineStoreListener();
    private static final BridgeHandler bridgeHandler = BridgeHandler.bridgeHandler();
    private static L2RuleInstaller installer;
    private static final String IFACEID = "ifaceid";
    private static final String OPENSTACK_NODES = "openstack-nodes";
    private static final String GATEWAY = "multi-gateway";
    private static final String CONTROLLER_IP_KEY = "ipaddress";
    private EventuallyConsistentMap<OpenstackNodeId, OpenstackNode> nodeStore;
    private EventuallyConsistentMap<Gateway, PortNumber> gatewayStore;
    private EventuallyConsistentMap<Ip4Address, MacAddress> vmStore;

    private VnetPacketProcessor processor = new VnetPacketProcessor();

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.iris4sdn.csdncm.vnetmanager");
        packetService.addProcessor(processor, PacketProcessor.director(1));

        installer = L2RuleInstaller.ruleInstaller(appId);
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

        vmStore = storageService
                .<Ip4Address, MacAddress>eventuallyConsistentMapBuilder()
                .withName(GATEWAY).withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);
        virtualMachineService.addListener(virtualMachineListener);

        log.info("Started~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }

    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;

        deviceService.removeListener(deviceListener);
        hostService.removeListener(hostListener);
        virtualMachineService.removeListener(virtualMachineListener);
        eventExecutor.shutdown();

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
                    log.info("gateway Port number!!!!!!!!!!!!!!!!!!!!!!!!11 {}", gateway.getGatewayPortNumber());
//                    log.info("bridge {}", e.getBridgeId(Bridge.BridgeType.INTEGRATION));
//                    log.info("mac {}", gateway.macAddress());
//                    installer.programGatewayArp(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            gateway.macAddress(), Objective.Operation.ADD);
                });
    }


    @Override
    public void deleteGateway(Gateway gateway) {
        //TODO
    }

    private Gateway getGateway(PortNumber inPort){
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
    public void addOpenstackNode(OpenstackNode node) {
        checkNotNull(node, OPENSTACK_NODE_NOT_NULL);
        if(nodeStore.containsKey(node.id())) {
            log.info("Remove Openstack node {} pre-configured", node.id());
            nodeStore.remove(node.id());
        }

        log.info("Openstack node {} in {} configured", node.id(), node.getManageNetworkIp());
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


    private OpenstackNode connectController(Device device) {
        // Find out Openstack node which is configured in advance.
        String localIpAddress = device.annotations()
                .value(CONTROLLER_IP_KEY);
        IpAddress localIp = IpAddress.valueOf(localIpAddress);

        OpenstackNode node = nodeStore.values().stream()
                .filter(e -> e.getManageNetworkIp().equals(localIp))
                .findFirst().orElse(null);

        if (node == null) {
            log.warn("No information of Openstack node for detected ovsdb {}", device.id());
            return null;
        }
        node.setControllerId(device.id());
        node.applyState(OVSDB_CONNECTED);

        log.info("{} is connected to ovsdb {}", node.id(), device.id());
        return node;
    }

    public void onControllerDetected(Device device) {
        DeviceId deviceId = device.id();
        log.info("New ovsdb {} found", deviceId);

        if (!mastershipService.isLocalMaster(deviceId)) {
            log.info("This ovsdb is not under our control {}", deviceId);
            return;
        }

        OpenstackNode node = connectController(device);
        if (node == null) {
            log.warn("No information of Openstack node for detected ovsdb {}", deviceId);
            return;
        }

        bridgeHandler.createBridge(node, Bridge.BridgeType.INTEGRATION);
        bridgeHandler.createBridge(node, Bridge.BridgeType.EXTERNAL);

        node.applyState(BRIDGE_CREATED);

        if (bridgeHandler.setBridgeOutbandControl(node.getControllerId(),
                Bridge.BridgeType.INTEGRATION) == false)
            log.warn("Could not set integration bridge to out-of-band control");

        if (bridgeHandler.setBridgeOutbandControl(node.getControllerId(),
                Bridge.BridgeType.EXTERNAL) == false)
            log.warn("Could not set external bridge to out-of-band control");

    }

    public void onControllerVanished(Device device) {
        DeviceId deviceId = device.id();
        log.info("Ovsdb {} vanished", deviceId);

        OpenstackNode node = nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(OVSDB_CONNECTED)))
                .filter(e -> e.getControllerId().equals(deviceId))
                .findFirst().orElse(null);

        if (node == null) {
            log.warn("No information of Openstack node for vanished Ovsdb {}", deviceId);
            return;
        }

        nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(TUNNEL_CREATED)))
                .filter(e -> !e.equals(node))
                .forEach(e -> bridgeHandler.destroyTunnel(node, e));

        node.initState();
    }

    public void onOvsDetected(Device device) {
        DeviceId deviceId = device.id();
        log.info("New OVS {} found ", deviceId);

        if (!mastershipService.isLocalMaster(deviceId)) {
            log.info("This ovs bridge is not under our control {}", deviceId);
            return;
        }

        OpenstackNode node = getOpenstackNode(deviceId);
        if (node == null) {
            log.warn("No information of Openstack node for detected ovs {}", deviceId);
            return;
        }

        Bridge.BridgeType type = null;
        if (deviceId.equals(node.getBridgeId(Bridge.BridgeType.EXTERNAL))) {
            type = Bridge.BridgeType.EXTERNAL;

            // Install blocking rule for attached before installation of drop rule
            installBarrierRule(node, type, Objective.Operation.ADD);

            if (bridgeHandler.createExPort(node) == false) {
                log.error("External port setting failed at {}", node.id());
                return;
            }

            installer.programDrop(deviceId,
                    node.getExPort().number(), Objective.Operation.ADD);

            Port exPort = node.getExPort();
            MacAddress macAddress = MacAddress.valueOf(exPort.annotations()
                    .value(AnnotationKeys.PORT_MAC));

            installer.programArpRequest(deviceId,
                    node.getPublicNetworkIp(), macAddress, Objective.Operation.ADD);
            log.info("node publicNetworkIp");

            installer.programArpResponse(deviceId,
                    node.getPublicNetworkIp(), Objective.Operation.ADD);
            installer.programNormalIn(deviceId,
                    node.getExPort().number(), node.getPublicNetworkIp(),
                    Objective.Operation.ADD);

            installer.programNormalOut(deviceId, node.getExPort().number(),
                    Objective.Operation.ADD);

            // Uninstall blocking rule for attached before installation of drop rule
            installBarrierRule(node, type, Objective.Operation.REMOVE);

            node.applyState(EXTERNAL_BRIDGE_DETECTED);
        } else if (deviceId.equals(node.getBridgeId(Bridge.BridgeType.INTEGRATION))) {
            type = Bridge.BridgeType.INTEGRATION;

            nodeStore.values().stream()
                    .filter(e -> e.getState().containsAll(EnumSet.of(BRIDGE_CREATED)))
                    .filter(e -> !e.equals(node))
                    .forEach(e -> bridgeHandler.createTunnel(node, e));

//            OpenstackNode gateway = nodeStore.values().stream()
//                    .filter(e -> e.getNodeType().equals(OpenstackNode.Type.GATEWAY))
//                    .findFirst().orElse(null);
//
//            if (gateway == null) {
//                log.info("Gateway is not configured");
//            } else {
//                bridgeHandler.createGatewayTunnel(node, gateway);
//            }

//            gatewayStore.keySet().stream()
//                .forEach(e -> {
//                    log.info("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!1");
//                    installer.programGatewayArp(deviceId,
//                            e.macAddress(), Objective.Operation.ADD);
//                });

            node.applyState(INTEGRATION_BRIDGE_DETECTED);
        }

        if (node.getState().containsAll(
                EnumSet.of(INTEGRATION_BRIDGE_DETECTED, EXTERNAL_BRIDGE_DETECTED))) {
            bridgeHandler.createPatchPort(node, Bridge.BridgeType.INTEGRATION);
            bridgeHandler.createPatchPort(node, Bridge.BridgeType.EXTERNAL);
        }
    }

    public void onOvsVanished(Device device) {
        DeviceId deviceId = device.id();
        log.info("OVS {} vanished ", deviceId);

        OpenstackNode node = getOpenstackNode(deviceId);
        if (node == null) {
            log.warn("No information of Openstack node for detected ovs {}", deviceId);
            return;
        }

        nodeStore.values().stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(TUNNEL_CREATED)))
                .filter(e -> !e.equals(node))
                .forEach(e -> bridgeHandler.destroyTunnel(node, e));

        // Nothing to be processed more since OVS is gone.
    }

    public void onHostDetected(Host host) {
        log.info("New host found {}", host.id());
        DeviceId deviceId = host.location().deviceId();
        if (!mastershipService.isLocalMaster(deviceId)) {
            log.info("This host is not under our control {}", host.toString());
            return;
        }

        String ifaceId = host.annotations().value(IFACEID);
        if (ifaceId == null) {
            log.error("The ifaceId of Host is null");
            return;
        }

        OpenstackNode node = getOpenstackNode(deviceId);
        if (node == null) {
            log.error("Could not find Openstack node of the host {} in {} ",
                    host.toString(), deviceId);
            return;
        }

        VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
        PortNumber portNumber = host.location().port();
        VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
        if (virtualPort == null) {
            log.error("Could not find virutal port of the host {}", host.toString());
            return;
        }

        // Add virtual port information
        TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
        SegmentationId segmentationId = tenantNetwork.segmentationId();

        node.addVirtualPort(virtualPort, portNumber, segmentationId);

        // Install flow rules
        installUnicastOutRule(node, virtualPort, Objective.Operation.ADD);
        installUnicastInRule(node, virtualPort, Objective.Operation.ADD);
        installBroadcastRule(node, virtualPort, Objective.Operation.ADD);
    }

    public void onHostVanished(Host host) {
        log.info("Host vanished {}", host.id());
        DeviceId deviceId = host.location().deviceId();
        if (!mastershipService.isLocalMaster(deviceId)) {
            log.info("This host is not under our control {}", host.toString());
            return;
        }

        String ifaceId = host.annotations().value(IFACEID);
        if (ifaceId == null) {
            log.error("The ifaceId of Host is null");
            return;
        }

        OpenstackNode node = getOpenstackNode(deviceId);
        if (node == null) {
            log.error("Could not find Openstack node of the host {}",
                    host.toString() + " Device : "  + deviceId);
            return;
        }

        VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
        VirtualPort virtualPort = node.getVirtualPort(virtualPortId);
        if (virtualPort == null) {
            log.error("Could not find virutal port of the host {}", host.toString());
            return;
        }

        // Uninstall flow rules
        installUnicastOutRule(node, virtualPort, Objective.Operation.REMOVE);
        installUnicastInRule(node, virtualPort, Objective.Operation.REMOVE);
        installBroadcastRule(node, virtualPort, Objective.Operation.REMOVE);

        // Remove virtual port information
        node.removeVirtualPort(virtualPort);
    }

    private void installBarrierRule(OpenstackNode node, Bridge.BridgeType bridgeType,
                                    Objective.Operation type) {
        log.info("Install barrier rule");
        installer.programDrop(node.getBridgeId(bridgeType), null, type);
    }

    private void installUnicastInRule(OpenstackNode node, VirtualPort port,
                                      Objective.Operation type) {
        log.info("Install unicast inward flow");
        // From local VMs
        SegmentationId segmentationId = node.getSegmentationId(port.portId());
        PortNumber portNumber = node.getVirutalPortNumber(port.portId());
        MacAddress mac = port.macAddress();

        installer.programLocalIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION), segmentationId,
                portNumber, mac, type);

        // From remote VMs
        // Rule from other openstack node instance
        node.getTunnelPortNumbers()
                .forEach(e -> {
                    installer.programTunnelIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            segmentationId, e, type);
                });

//        Set<PortNumber> tunnel_gateway_ports = new HashSet<>();
//        gatewayStore.values().stream().forEach(gwport -> {
//            tunnel_gateway_ports.add(gwport);
//        });
//
        gatewayStore.values().stream().forEach(gatewayPort -> {
            installer.programGatewayIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    segmentationId, gatewayPort, type);
        });

        // For remote Openstack nodes (gateway included)
        nodeStore.values().stream()
                .filter(e -> !e.equals(node))
                .filter(e -> e.getState().containsAll(EnumSet.of(TUNNEL_CREATED)))
                .forEach(e -> {
//                    tunnel_gateway_ports.add(e.getTunnelPortNumber(node.id()));
                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            segmentationId, e.getTunnelPortNumber(node.id()), mac, type);
//                    segmentationId, tunnel_gateway_ports, mac, type);
//                    gatewayStore.values().stream().forEach(gatewayport -> {
//                        log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
//                        installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                                segmentationId, gatewayport, mac, type);
//                    });
                });
//        gatewayStore.keySet().stream().forEach(gateway -> {
//            log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
//            installer.programLocalIn(gateway.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                    segmentationId, gatewayStore.get(gateway), mac, type);
//        });

        // For remote Openstack VMs beyond gateway (remote vm)
        Sets.newHashSet(virtualMachineService.getVirtualMachines()).stream()
                .filter(e -> e.segmentationId().equals(segmentationId))
                .forEach(e -> {
                    installer.programLocalIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            segmentationId, node.getGatewayTunnelPortNumber(),
                            e.macAddress(), type);
                });
    }

    private void installUnicastOutRule(OpenstackNode node, VirtualPort port,
                                       Objective.Operation type) {
        log.info("Install unicast outward flow");
        SegmentationId segmentationId = node.getSegmentationId(port.portId());
        PortNumber portNumber = node.getVirutalPortNumber(port.portId());
        MacAddress mac = port.macAddress();

        installer.programLocalOut(node.getBridgeId(Bridge.BridgeType.INTEGRATION), segmentationId,
                portNumber, mac, type);
    }

    private void installBroadcastRule(OpenstackNode node, VirtualPort port,
                                      Objective.Operation type) {
        log.info("Install broadcast flow");
        SegmentationId segmentationId = node.getSegmentationId(port.portId());
        PortNumber portNumber = node.getVirutalPortNumber(port.portId());

        Set<PortNumber> allPorts = new HashSet<>();
        Set<PortNumber> tunnelPorts = Sets.newHashSet(node.getTunnelPortNumbers());
        Set<PortNumber> virtualPorts =
                Sets.newHashSet(node.getVirutalPortNumbers(segmentationId));
        Set<PortNumber> gatewayPorts = new HashSet<>();
        gatewayStore.values().stream().forEach(e -> gatewayPorts.add(e));
        // Add local virtual ports & tunnel ports for entire out ports
        allPorts.addAll(virtualPorts);
        allPorts.addAll(tunnelPorts);
        allPorts.addAll(gatewayPorts);

        // Virtual ports broadcast to all ports
        virtualPorts.stream().forEach(e -> {
            installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    segmentationId, e, allPorts, type);
        });

        // Tunnel ports broadcast only to virtual ports
        tunnelPorts.stream().forEach(e -> {
            installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    segmentationId, e, virtualPorts, type);
        });

        gatewayPorts.stream().forEach(e -> {
            installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    segmentationId, e, virtualPorts, type);
        });;

        if (type == Objective.Operation.REMOVE) {
            // Broadcasting rules should be added again when removed
            virtualPorts.remove(portNumber);
            tunnelPorts.remove(portNumber);
            allPorts.remove(portNumber);

            virtualPorts.stream().forEach(e -> {
                installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                        segmentationId, e, allPorts, Objective.Operation.ADD);
            });

            if (!virtualPorts.isEmpty()) {
                tunnelPorts.stream().forEach(e -> {
                    installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            segmentationId, e, virtualPorts, Objective.Operation.ADD);
                });
            }
        }
    }

    private class InnerDeviceListener implements DeviceListener {

        @Override
        public void event(DeviceEvent event) {
            Device device = event.subject();
            if (Device.Type.CONTROLLER == device.type()) {
                if (DeviceEvent.Type.DEVICE_ADDED == event.type()) {
                    eventExecutor.submit(() -> onControllerDetected(device));
                }
                if (DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED == event.type()) {
                    if (deviceService.isAvailable(device.id())) {
                        eventExecutor.submit(() -> onControllerDetected(device));
                    } else {
                        eventExecutor.submit(() -> onControllerVanished(device));
                    }
                }
            } else if (Device.Type.SWITCH == device.type()) {
                if (DeviceEvent.Type.DEVICE_ADDED == event.type()) {
                    eventExecutor.submit(() -> onOvsDetected(device));
                }
                if (DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED == event.type()) {
                    if (deviceService.isAvailable(device.id())) {
                        eventExecutor.submit(() -> onOvsDetected(device));
                    } else {
                        eventExecutor.submit(() -> onOvsVanished(device));
                    }
                }
            } else {
                log.info("Do nothing for this device type");
            }
        }
    }

    private class InnerHostListener implements HostListener {

        @Override
        public void event(HostEvent event) {
            Host host = event.subject();
            if (HostEvent.Type.HOST_ADDED == event.type()) {
                onHostDetected(host);
            } else if (HostEvent.Type.HOST_REMOVED == event.type()) {
                onHostVanished(host);
            } else if (HostEvent.Type.HOST_UPDATED == event.type()) {
                onHostVanished(host);
                onHostDetected(host);
            }
        }
    }

    private void processVirtualMachine(VirtualMachine vm, Objective.Operation operation) {
        log.info("VirtualMachine {} processed", vm);
        // For remote Openstack VMs beyond gateway

        if(operation == Objective.Operation.ADD) {
            vmStore.put(vm.ipAddress().getIp4Address(), vm.macAddress());
        } else if (operation == Objective.Operation.REMOVE) {
            vmStore.remove(vm.ipAddress().getIp4Address());
        }


//        nodeStore.values().stream()
//                .filter(e -> e.getState().contains(TUNNEL_CREATED))
//                .forEach(e -> {
//                    gatewayStore.values().stream().forEach(gatewayPort -> {
//                        installer.programGatewayIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                        vm.segmentationId(), gatewayPort, operation);
//                    });
//                });



//        gatewayStore.keySet().stream().forEach(gateway -> {
//            log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
//            installer.programLocalIn(gateway.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                    vm.segmentationId(), gatewayStore.get(gateway), vm.macAddress(), operation);
//        });

//        Gateway gateway = null;
//        Iterator<Gateway> gateways = gatewayStore.keySet().iterator();
//        while (gateways.hasNext()) {
//            gateway = gateways.next();
//        }
//        if(gateway == null)
//            return;

//        final PortNumber finalPortNumber = gatewayStore.get(gateway);
//        final Gateway finalGateway = gateway;





//        nodeStore.values().stream()
//                .filter(e -> e.getState().contains(TUNNEL_CREATED))
//                .forEach(e -> {
//                    installer.programArpClassifier(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            vm.ipAddress().getIp4Address(), vm.segmentationId(), operation);
//                    Iterator<Gateway> gateways = gatewayStore.keySet().iterator();
//                    while (gateways.hasNext()) {
//                        Gateway gateway = gateways.next();
//                        installer.programGatewayArp(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                                vm.segmentationId(), gateway.macAddress(),
//                                operation);
//                    }
//                    gateways = null;
//                });

    }

    private void processArp(ARP arpPacket, PortNumber inPort){
        MacAddress srcMacAddress = MacAddress.valueOf(arpPacket.getSenderHardwareAddress());
        MacAddress dstMacAddress = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
        Ip4Address scrIpAddress = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());

        Iterator<Host> hosts = hostService.getHostsByMac(dstMacAddress).iterator();
        while (hosts.hasNext()) {
            Host host = hosts.next();
            String ifaceId = host.annotations().value(IFACEID);
            if (ifaceId == null) {
//                log.error("The ifaceId of Host is null");
                return;
            }

            VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
            VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
            TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
            SegmentationId segmentationId = tenantNetwork.segmentationId();

            log.info("src {}" , srcMacAddress);
            log.info("src {}" , scrIpAddress);
            log.info("dst {}", dstMacAddress);
            MacAddress vmMac = vmStore.get(scrIpAddress);
//            Gateway gateway = getGateway(srcMacAddress);
            Gateway  gateway = getGateway(inPort);
            log.info("vmMAc {}", vmMac);
//            log.info("gateway {}", gateway);
            log.info("inPort {}", inPort);
            if(vmMac == null)
                return;

            log.info("!!!!!!!!!!!!!!!!!!!!!! gateway {}",gateway.toString());
            log.info("~~~~~~~~~~~~~~~ vmStore {}", vmMac.toString());
            nodeStore.values().stream()
                .filter(e -> e.getState().contains(GATEWAY_CREATED))
                .forEach(e -> {
                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            segmentationId, gateway.getGatewayPortNumber(), vmMac,
                            Objective.Operation.ADD);
                });
        }
    }

    private class InnerVirtualMachineStoreListener implements VirtualMachineListener{
        @Override
        public void event(VirtualMachineEvent event) {
            checkNotNull(event, EVENT_NOT_NULL);
            VirtualMachine vm = event.subject();
            if (VirtualMachineEvent.Type.VIRTUAL_MACHINE_PUT == event.type()){
                processVirtualMachine(vm, Objective.Operation.ADD);
            }
            if(VirtualMachineEvent.Type.VIRTUAL_MACHINE_REMOVE == event.type()){
                processVirtualMachine(vm, Objective.Operation.REMOVE);
            }
        }
    }

    private class VnetPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            PortNumber sourcePoint = context.inPacket().receivedFrom().port();

            Ethernet ethernet = pkt.parsed();
            if(ethernet == null) {
                return;
            }

            if(ethernet.getEtherType() == Ethernet.TYPE_ARP){
                ARP arpPacket = (ARP) ethernet.getPayload();
                processArp(arpPacket, sourcePoint);
            }
        }
    }

}