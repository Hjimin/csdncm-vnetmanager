package org.iris4sdn.csdncm.vnetmanager.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.*;
import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.iris4sdn.csdncm.vnetmanager.NodeManagerService;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNode;
import org.iris4sdn.csdncm.vnetmanager.VnetManagerService;
import org.iris4sdn.csdncm.vnetmanager.gateway.Gateway;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayEvent;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayListener;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayService;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineService;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.*;
import org.onosproject.net.behaviour.ExtensionTreatmentResolver;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatmentType;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.group.*;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
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
    private static final String APP_ID = "org.iris4sdn.csdncm.vnetmanager";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DriverService driverService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GroupService groupService;

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
    protected NodeManagerService nodeManagerService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualMachineService virtualMachineService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected GatewayService gatewayService;

    private final ExecutorService eventExecutor = Executors
            .newFixedThreadPool(1, groupedThreads("onos/vnetmanager", "event-handler"));
    private ApplicationId appId;
    private DeviceListener deviceListener = new InnerDeviceListener();
    private HostListener hostListener = new InnerHostListener();
    private GatewayListener gatewayListener = new InnerGatewayListener();

//    private VirtualMachineListener virtualMachineListener = new InnerVirtualMachineStoreListener();
    private static final BridgeHandler bridgeHandler = BridgeHandler.bridgeHandler();
    private static L2RuleInstaller installer;
    private static final String IFACEID = "ifaceid";
    private static final String OPENSTACK_NODES = "openstack-nodes";
    private static final String GATEWAY = "multi-gateway";
    private static final String OVSDB_IP_KEY = "ipaddress";
    private final Map<HostId, Host> hostStore = new HashMap<>();
    private final Map<Ip4Address, Gateway> vmGateMatchStore = new HashMap<>();

    private VnetPacketProcessor processor = new VnetPacketProcessor();

    @Activate
    public void activate() {
        appId = coreService.registerApplication("org.iris4sdn.csdncm.vnetmanager");
        packetService.addProcessor(processor, PacketProcessor.director(1));

        installer = L2RuleInstaller.ruleInstaller(appId);

        deviceService.addListener(deviceListener);
        hostService.addListener(hostListener);
        gatewayService.addListener(gatewayListener);

//        virtualMachineService.addListener(virtualMachineListener);

        log.info("Started~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
    }

    @Deactivate
    public void deactivate() {
        packetService.removeProcessor(processor);
        processor = null;

        deviceService.removeListener(deviceListener);
        hostService.removeListener(hostListener);
        gatewayService.removeListener(gatewayListener);

//        virtualMachineService.removeListener(virtualMachineListener);
        eventExecutor.shutdown();

        log.info("Stopped");
    }

    private OpenstackNode connectNodetoOvsdb(Device ovsdb) {
        // Find out Openstack node which is configured in advance.
        String nodeManageIp = ovsdb.annotations().value(OVSDB_IP_KEY);
        IpAddress nodeManageNetworkIp = IpAddress.valueOf(nodeManageIp);

        OpenstackNode node = Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> e.getManageNetworkIp().equals(nodeManageNetworkIp))
                .findFirst().orElse(null);

        if (node == null) {
            log.warn("No information of Openstack node for detected ovsdb {}", ovsdb.id());
            return null;
        }

        node.setOvsdbId(ovsdb.id());
        node.applyState(OVSDB_CONNECTED);

        log.info("{} is connected to ovsdb {}", node.id(), ovsdb.id());
        return node;
    }

    //public void onConntrollerConnected
    // Openstack node is connected to onos controller -> ovsdb is created
    public void nodeConnected(Device device) {
        DeviceId deviceId = device.id();
        log.info("New ovsdb {} found", deviceId);

        if (!mastershipService.isLocalMaster(deviceId)) {
            log.info("This ovsdb is not under our control {}", deviceId);
            return;
        }

        OpenstackNode node = connectNodetoOvsdb(device);
        if (node == null) {
            log.warn("No information of Openstack node for detected ovsdb {}", deviceId);
            return;
        }

        bridgeHandler.createBridge(node, Bridge.BridgeType.INTEGRATION);
        bridgeHandler.createBridge(node, Bridge.BridgeType.EXTERNAL);

        node.applyState(BRIDGE_CREATED);

        if (bridgeHandler.setBridgeOutbandControl(node.getOvsdbId(),
                Bridge.BridgeType.INTEGRATION) == false)
            log.warn("Could not set integration bridge to out-of-band control");

        if (bridgeHandler.setBridgeOutbandControl(node.getOvsdbId(),
                Bridge.BridgeType.EXTERNAL) == false)
            log.warn("Could not set external bridge to out-of-band control");
    }

    public void nodeDisconnected(Device ovsdb) {
        DeviceId ovsdbDeviceId = ovsdb.id();
        log.info("Ovsdb {} vanished", ovsdbDeviceId);

        OpenstackNode node = Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(OVSDB_CONNECTED)))
                .filter(e -> e.getOvsdbId().equals(ovsdbDeviceId))
                .findFirst().orElse(null);

        if (node == null) {
            log.warn("No information of Openstack node for vanished Ovsdb {}", ovsdbDeviceId);
            return;
        }

        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(TUNNEL_CREATED)))
                .filter(e -> !e.equals(node))
                .forEach(e -> bridgeHandler.destroyTunnel(node, e));

        node.initState();
    }

    public void ovsBridgeDetected(Device ovsBridge) {
        DeviceId ovsBridgeId = ovsBridge.id();
        log.info("New OVS {} found ", ovsBridgeId);

        if (!mastershipService.isLocalMaster(ovsBridgeId)) {
            log.info("This ovs bridge is not under our control {}", ovsBridgeId);
            return;
        }

        OpenstackNode node = nodeManagerService.getOpenstackNode(ovsBridgeId);
        if (node == null) {
            log.warn("No information of Openstack node for detected ovs {}", ovsBridgeId);
            return;
        }

        Bridge.BridgeType type = null;
        if (ovsBridgeId.equals(node.getBridgeId(Bridge.BridgeType.EXTERNAL))) {
            type = Bridge.BridgeType.EXTERNAL;

            // Install blocking rule for attached before installation of drop rule
            installBarrierRule(node, type, Objective.Operation.ADD);

            if (bridgeHandler.createExPort(node) == false) {
                log.error("External port setting failed at {}", node.id());
                return;
            }

            Port exPort = node.getExPort();
            MacAddress macAddress = MacAddress.valueOf(exPort.annotations().value(AnnotationKeys.PORT_MAC));

            installer.programDrop(ovsBridgeId, exPort.number(), Objective.Operation.ADD);
            installer.programArpRequest(ovsBridgeId, node.getPublicNetworkIp(), macAddress, Objective.Operation.ADD);
            installer.programArpResponse(ovsBridgeId, node.getPublicNetworkIp(), Objective.Operation.ADD);
            installer.programNormalIn(ovsBridgeId, exPort.number(), node.getPublicNetworkIp(), Objective.Operation.ADD);
            installer.programNormalOut(ovsBridgeId, exPort.number(), Objective.Operation.ADD);

            // Uninstall blocking rule for attached before installation of drop rule
            installBarrierRule(node, type, Objective.Operation.REMOVE);
            node.applyState(EXTERNAL_BRIDGE_DETECTED);

        } else if (ovsBridgeId.equals(node.getBridgeId(Bridge.BridgeType.INTEGRATION))) {
            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                    .filter(e -> e.getState().containsAll(EnumSet.of(BRIDGE_CREATED)))
                    .filter(e -> !e.equals(node))
                    .forEach(e -> bridgeHandler.createTunnel(node, e));
            node.applyState(INTEGRATION_BRIDGE_DETECTED);
        }

        if (node.getState().containsAll(EnumSet.of(INTEGRATION_BRIDGE_DETECTED, EXTERNAL_BRIDGE_DETECTED))) {
            bridgeHandler.createPatchPort(node, Bridge.BridgeType.INTEGRATION);
            bridgeHandler.createPatchPort(node, Bridge.BridgeType.EXTERNAL);
        }
    }

    public void ovsdbBridgeDeleted(Device ovsdbBridge) {
        DeviceId ovsdbBridgeId = ovsdbBridge.id();
        log.info("OVS {} vanished ", ovsdbBridgeId);

        OpenstackNode node = nodeManagerService.getOpenstackNode(ovsdbBridgeId);
        if (node == null) {
            log.warn("No information of Openstack node for detected ovs {}", ovsdbBridgeId);
            return;
        }
        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(TUNNEL_CREATED)))
                .filter(e -> !e.equals(node))
                .forEach(e -> bridgeHandler.destroyTunnel(node, e));

        // Nothing to be processed more since OVS is gone.
    }

    public void processGateway(Gateway gateway, Objective.Operation type) {
        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> e.getState().containsAll(EnumSet.of(BRIDGE_CREATED)))
                .forEach(node -> {
                    bridgeHandler.createGatewayTunnel(node, gateway);
                    installGroupTableRule(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            type);
                });
        if (gatewayService.getGatewayPortNumber() == null) {
            gatewayService.setGatewayPortNumber(gateway.getGatewayPortNumber());
        }
    }



    //detect node and install rule
    public void processHost(Host host, Objective.Operation type) {
        log.info("New host found {}", host.id());
        DeviceId deviceId = host.location().deviceId();

        if (!mastershipService.isLocalMaster(deviceId)) {
            log.info("This host is not under our control {}", host.toString());
            return;
        }
        OpenstackNode node = nodeManagerService.getOpenstackNode(deviceId);
        if (node == null) {
            log.error("Could not find Openstack node of the host {} in {} ",
                    host.toString(), deviceId);
            return;
        }

        VirtualPort virtualPort = configureVirtualPort(host, node, type);

        if (virtualPort == null) {
            log.error("Could not find virutal port of the host {}", host.toString());
            return;
        }

        // Install flow rules
        installUnicastOutRule(node, virtualPort, type);
        installUnicastInRule(node, virtualPort, type);
        installBroadcastRule(node, virtualPort, type);

        if(type.equals(Objective.Operation.REMOVE)) {
            node.removeVirtualPort(virtualPort);
        } else if(type.equals(Objective.Operation.ADD)) {
            hostStore.put(host.id(), host);
        }
    }

   //configure virtual port
    private VirtualPort configureVirtualPort(Host host, OpenstackNode node, Objective.Operation type) {
        String ifaceId = host.annotations().value(IFACEID);
        if (ifaceId == null) {
            log.error("The ifaceId of Host is null");
            return null;
        }

        VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
        if (type.equals(Objective.Operation.ADD)) {
            PortNumber portNumber = host.location().port();
            VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
            if (virtualPort == null) {
                log.error("Could not find virutal port of the host {}", host.toString());
                return null;
            }

            // Add virtual port information
            TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
            SegmentationId segmentationId = tenantNetwork.segmentationId();

            node.addVirtualPort(virtualPort, portNumber, segmentationId);
            return virtualPort;
        } else if(type.equals(Objective.Operation.REMOVE)) {
            VirtualPort virtualPort = node.getVirtualPort(virtualPortId);
            if (virtualPort == null) {
                log.error("Could not find virutal port of the host {}", host.toString());
                return null;
            }
            return virtualPort;
        }
        return null;
    }


    private void installBarrierRule(OpenstackNode node, Bridge.BridgeType bridgeType,
                                    Objective.Operation type) {
        log.info("Install barrier rule");
        installer.programDrop(node.getBridgeId(bridgeType), null, type);
    }


    private void installGroupTableRule(DeviceId deviceId,
                                  Objective.Operation type) {
        if (type.equals(Objective.Operation.REMOVE)) {
            return;
        }
        List<GroupBucket> buckets = Lists.newArrayList();
        Sets.newHashSet(gatewayService.getGateways()).stream().forEach(gateway -> {
            Ip4Address dst = Ip4Address.valueOf(gateway.getDataNetworkIp().toString());

            TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();

            DriverHandler handler = driverService.createHandler(deviceId);
            ExtensionTreatmentResolver resolver = handler.behaviour(ExtensionTreatmentResolver.class);
            ExtensionTreatment treatment = resolver
                    .getExtensionInstruction(ExtensionTreatmentType.ExtensionTreatmentTypes
                            .NICIRA_SET_TUNNEL_DST.type());
            try {
                treatment.setPropertyValue("tunnelDst", dst);
            } catch (Exception e) {
                log.error("Failed to get extension instruction to set tunnel dst {}", deviceId);
            }

            short weight = gateway.getWeight();

            builder.extension(treatment, deviceId);
            builder.setOutput(gateway.getGatewayPortNumber());
            GroupBucket bucket = DefaultGroupBucket
                    .createSelectGroupBucket(builder.build(), weight);
            buckets.add(bucket);
        });

        final GroupKey key = new DefaultGroupKey("org.iris4sdn.csdncm.vnetmanager".getBytes());
        GroupDescription groupDescription = new DefaultGroupDescription(deviceId,
                GroupDescription.Type.SELECT,
                new GroupBuckets(buckets),
                key, 1, appId);
        groupService.addGroup(groupDescription);
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
                            e, type);
                });

//        Set<PortNumber> tunnel_gateway_ports = new HashSet<>();
//        gatewayStore.values().stream().forEach(gwport -> {
//            tunnel_gateway_ports.add(gwport);
//        });
//


        //Jimin!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        Sets.newHashSet(gatewayService.getGateways()).stream().forEach(gatewayPort -> {
            installer.programGatewayIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    gatewayPort.getGatewayPortNumber(), type);
        });

        // For remote Openstack nodes (gateway included)
        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
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
        SegmentationId segmentationId = node.getSegmentationId(port.portId());
        PortNumber portNumber = node.getVirutalPortNumber(port.portId());

        Set<PortNumber> allPorts = new HashSet<>();
        Set<PortNumber> tunnelPorts = Sets.newHashSet(node.getTunnelPortNumbers());
        Set<PortNumber> virtualPorts =
                Sets.newHashSet(node.getVirutalPortNumbers(segmentationId));
//        Set<PortNumber> gatewayPorts = new HashSet<>();
//        Sets.newHashSet(nodeManagerService.getGatewayPorts()).stream().forEach(e -> gatewayPorts.add(e));

        // Add local virtual ports & tunnel ports for entire out ports
        allPorts.addAll(virtualPorts);
        allPorts.addAll(tunnelPorts);
//        allPorts.addAll(gatewayPorts);

        // Virtual ports broadcast to all ports
//        virtualPorts.stream().forEach(e -> {



        Gateway gateway = Sets.newHashSet(gatewayService.getGateways()).stream().findFirst().orElse(null);
        if(gateway != null) {
            installer.programBroadcastWithGroup(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    segmentationId, allPorts, type);

        } else {
             installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                     segmentationId, allPorts, type);

        }
//        });

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//        // Tunnel ports broadcast only to virtual ports
//        tunnelPorts.stream().forEach(e -> {
//            installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                    segmentationId,virtualPorts, type);
//        });

//        gatewayPorts.stream().forEach(e -> {
//            installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                    segmentationId, virtualPorts, type);
//        });
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        if (type == Objective.Operation.REMOVE) {
            // Broadcasting rules should be added again when removed
            virtualPorts.remove(portNumber);
            tunnelPorts.remove(portNumber);
            allPorts.remove(portNumber);

//            virtualPorts.stream().forEach(e -> {
//                installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                        segmentationId, e, allPorts, Objective.Operation.ADD);
//            });
//
//            if (!virtualPorts.isEmpty()) {
//                tunnelPorts.stream().forEach(e -> {
//                    installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            segmentationId, e, virtualPorts, Objective.Operation.ADD);
//                });
//            }



        }
    }
//
//    private void processVirtualMachine(VirtualMachine vm, Objective.Operation operation) {
//        log.info("VirtualMachine {} processed", vm);
//        // For remote Openstack VMs beyond gateway
//
//        if(operation == Objective.Operation.ADD) {
//            vmStore.put(vm.ipAddress().getIp4Address(), vm.macAddress());
//        } else if (operation == Objective.Operation.REMOVE) {
//            vmStore.remove(vm.ipAddress().getIp4Address());
//        }
//    }



    private Gateway chooseGateway(Ip4Address ip4Address){

        return null;
    }

    private void processArp(ARP arpPacket) {
        MacAddress targetHostMac = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
        MacAddress senderHostMac = MacAddress.valueOf(arpPacket.getSenderHardwareAddress());
        Ip4Address targetIp = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
        Ip4Address senderIp = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());


//        if(!targetHostMac.toString().equals(MacAddress.valueOf("ff:ff:ff:ff:ff:ff").toString())) {
//            return;
//        }

        Iterator<Host> hosts = hostService.getHostsByMac(targetHostMac).iterator();
        while (hosts.hasNext()) {
            Host host = hosts.next();
            String ifaceId = host.annotations().value(IFACEID);
            if (ifaceId == null) {
                log.error("The ifaceId of Host is null");
                return;
            }

            VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
            VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
            TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
            SegmentationId segmentationId = tenantNetwork.segmentationId();


            PortNumber gatewayPortNumber = gatewayService.getGatewayPortNumber();

            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> e.getState().contains(GATEWAY_CREATED))
                .forEach(e -> {
                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            segmentationId, gatewayPortNumber, senderHostMac,
                            Objective.Operation.ADD);
                });
        }
    }
//    private void processArp(ARP arpPacket, PortNumber inPort) {
//        MacAddress targetHostMac = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
//        MacAddress senderHostMac = MacAddress.valueOf(arpPacket.getSenderHardwareAddress());
//        Ip4Address targetIp = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
//        Ip4Address senderIp = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
//
//
//        if(!targetHostMac.toString().equals(MacAddress.valueOf("ff:ff:ff:ff:ff:ff").toString())) {
//            return;
//        }
//
//        MacAddress macAddress = vmStore.get(targetIp);
//        if(macAddress != null) {
//           return;
//        }
//
//        Iterator<Host> hosts = hostService.getHostsByMac(targetHostMac).iterator();
//        while (hosts.hasNext()) {
//            Host host = hosts.next();
//            String ifaceId = host.annotations().value(IFACEID);
//            if (ifaceId == null) {
//                log.error("The ifaceId of Host is null");
//                return;
//            }
//
//            VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
//            VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
//            TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
//            SegmentationId segmentationId = tenantNetwork.segmentationId();
//
//            MacAddress remoteVmMac = vmStore.get(senderIp);
//            Gateway  gateway = nodeManagerService.getGateway(inPort);
//            if(remoteVmMac == null) {
//                log.info("remote vm is null");
//                return;
//            }
//
//            vmGateMatchStore.put(senderIp, gateway);
//            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
//                .filter(e -> e.getState().contains(GATEWAY_CREATED))
//                .forEach(e -> {
//                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            segmentationId, gateway.getGatewayPortNumber(), remoteVmMac,
//                            Objective.Operation.ADD);
//                });
//        }
//    }


//    private void processArpRequest(ARP arpPacket, PortNumber inPort, PacketContext context, Ethernet ethernet) {
//        log.info("AAAAAAAAAAAAAAARRRRRRRRRRRRRRRRRRRRPPPPPPPPPPPPPPPPPPPPP");
//        Ip4Address targetVmIp = Ip4Address.valueOf(arpPacket.getTargetProtocolAddress());
//
//        Iterator<Host> targetHosts = hostService.getHostsByIp(targetVmIp).iterator();
//        if(targetHosts.hasNext()){
//            return;
//        }
//
//        VirtualMachine vm = virtualMachineService.getVirtualMachineByIp(targetVmIp);
//        if(vm != null) {
//            Gateway gateway = vmGateMatchStore.get(targetVmIp);
//            TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
//            builder.setOutput(gateway.getGatewayPortNumber());
//            context.block();
//            packetService.emit(new DefaultOutboundPacket(gateway.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                    builder.build(), ByteBuffer.wrap(ethernet.serialize())));
//
//        } else {
//            Gateway gateway = chooseGateway(targetVmIp);//nodeManagerService.getGateway(inPort);
//            vmGateMatchStore.put(targetVmIp, gateway);
//        }
//    }


//    private void processRarp(ARP arpPacket, PortNumber inPort) {
//        MacAddress targetHostMac = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
//        Ip4Address srcRemoteVmIp = Ip4Address.valueOf(arpPacket.getSenderProtocolAddress());
//
//        Iterator<Host> hosts = hostService.getHostsByMac(targetHostMac).iterator();
//        while (hosts.hasNext()) {
//            Host host = hosts.next();
//            String ifaceId = host.annotations().value(IFACEID);
//            if (ifaceId == null) {
//                log.error("The ifaceId of Host is null");
//                return;
//            }
//
//            VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
//            VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
//            TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
//            SegmentationId segmentationId = tenantNetwork.segmentationId();
//
//            MacAddress remoteVmMac = vmStore.get(srcRemoteVmIp);
//            Gateway downGateway = vmGateMatchStore.get(srcRemoteVmIp);
//            Gateway  newGateway = nodeManagerService.getGateway(inPort);
//            if(remoteVmMac == null
//                    || downGateway == null
//                    || newGateway == null) {
//                log.info("Vm or gateway is null");
//                return;
//            }
//
//            vmGateMatchStore.remove(srcRemoteVmIp);
//            vmGateMatchStore.put(srcRemoteVmIp, newGateway);
//            nodeManagerService.deleteGateway(downGateway);
//
//            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
//                .filter(e -> e.getState().contains(GATEWAY_CREATED))
//                .forEach(e -> {
//                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            segmentationId, downGateway.getGatewayPortNumber(), remoteVmMac,
//                            Objective.Operation.REMOVE);
//                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            segmentationId, newGateway.getGatewayPortNumber(), remoteVmMac,
//                            Objective.Operation.ADD);
//                    installer.programGatewayIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                                downGateway.getGatewayPortNumber(), Objective.Operation.REMOVE);
//                });
//        }
//    }


    private class InnerDeviceListener implements DeviceListener {

        @Override
        public void event(DeviceEvent event) {
            Device device = event.subject();
            if (Device.Type.CONTROLLER == device.type()) {
                if (DeviceEvent.Type.DEVICE_ADDED == event.type()) {
                    eventExecutor.submit(() -> nodeConnected(device));
                }
                if (DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED == event.type()) {
                    if (deviceService.isAvailable(device.id())) {
                        eventExecutor.submit(() -> nodeConnected(device));
                    } else {
                        eventExecutor.submit(() -> nodeDisconnected(device));
                    }
                }
            } else if (Device.Type.SWITCH == device.type()) {
                if (DeviceEvent.Type.DEVICE_ADDED == event.type()) {
                    eventExecutor.submit(() -> ovsBridgeDetected(device));
                }
                if (DeviceEvent.Type.DEVICE_AVAILABILITY_CHANGED == event.type()) {
                    if (deviceService.isAvailable(device.id())) {
                        eventExecutor.submit(() -> ovsBridgeDetected(device));
                    } else {
                        eventExecutor.submit(() -> ovsdbBridgeDeleted(device));
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
                processHost(host, Objective.Operation.ADD);
            } else if (HostEvent.Type.HOST_REMOVED == event.type()) {
                processHost(host, Objective.Operation.REMOVE);
            } else if (HostEvent.Type.HOST_UPDATED == event.type()) {
                processHost(host, Objective.Operation.REMOVE);
                processHost(host, Objective.Operation.ADD);
            }
        }
    }


    private class InnerGatewayListener implements GatewayListener {
        @Override
        public void event(GatewayEvent event) {
            checkNotNull(event, EVENT_NOT_NULL);
            Gateway gateway = event.subject();
            if (GatewayEvent.Type.GATEWAY_PUT == event.type()) {
                log.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~`");
                processGateway(gateway, Objective.Operation.ADD);
            } else if (GatewayEvent.Type.GATEWAY_REMOVE == event.type()) {
                processGateway(gateway, Objective.Operation.REMOVE);
            }
//            } else if (G == event.type()) {
//                processHost(host, Objective.Operation.REMOVE);
//                processHost(host, Objective.Operation.ADD);
//            }
        }
    }

//    private class InnerVirtualMachineStoreListener implements VirtualMachineListener{
//        @Override
//        public void event(VirtualMachineEvent event) {
//            checkNotNull(event, EVENT_NOT_NULL);
//            VirtualMachine vm = event.subject();
//            if (VirtualMachineEvent.Type.VIRTUAL_MACHINE_PUT == event.type()){
//                processVirtualMachine(vm, Objective.Operation.ADD);
//            }
//            if(VirtualMachineEvent.Type.VIRTUAL_MACHINE_REMOVE == event.type()){
//                processVirtualMachine(vm, Objective.Operation.REMOVE);
//            }
//        }
//    }

    private class VnetPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            PortNumber sourcePoint = context.inPacket().receivedFrom().port();

            Ethernet ethernet = pkt.parsed();
            if(ethernet == null) {
                return;
            }

            if(ethernet.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arpPacket = (ARP) ethernet.getPayload();
                processArp(arpPacket);
                MacAddress targetMac = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
                if(targetMac.toString().equals(MacAddress.valueOf("ff:ff:ff:ff:ff:ff"))){
//                    processArpRequest(arpPacket, sourcePoint, context, ethernet);
                } else {

//                    processArpResponse(arpPacket, sourcePoint);
                }
            } else if (ethernet.getEtherType() == Ethernet.TYPE_RARP) {
                ARP rarpPacket = (ARP) ethernet.getPayload();
//                processRarp(rarpPacket, sourcePoint);
            }
        }
    }

}