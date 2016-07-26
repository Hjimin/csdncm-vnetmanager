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
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.*;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.group.*;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
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
    protected GroupService groupService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TenantNetworkService tenantNetworkService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VirtualPortService virtualPortService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NodeManagerService nodeManagerService;

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
    private static final String OVSDB_IP_KEY = "ipaddress";
    private VnetPacketProcessor processor = new VnetPacketProcessor();

    private final Map<Host, VirtualPort> hostVirtualPortMap = new HashMap<>();
    private final Map<Gateway, GroupBucket> bucketMap = new HashMap<>();
    private final GroupKey groupKey = new DefaultGroupKey("org.iris4sdn.csdncm.vnetmanager".getBytes());

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
            if(createGroupTable(node.getBridgeId(Bridge.BridgeType.INTEGRATION))) {
                node.applyState(GATEWAY_GROUP_CREATED);
            }
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

        Sets.newHashSet(gatewayService.getGateways()).stream()
                .forEach(gateway -> {
                    processGateway(gateway, Objective.Operation.REMOVE);
                });

        // Nothing to be processed more since OVS is gone.
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
            hostVirtualPortMap.remove(host);
        } else if(type.equals(Objective.Operation.ADD)) {
            hostVirtualPortMap.put(host, virtualPort);
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


    public void processGateway(Gateway gateway, Objective.Operation type) {
        if(type.equals(Objective.Operation.ADD)) {
            log.info("Gateway {} added", gateway.toString());
            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream().forEach(node -> {
                bridgeHandler.createGatewayTunnel(node, gateway);
                //for arp
                installer.programGatewayIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                        gateway.getGatewayPortNumber(), type);
                Sets.newHashSet(hostVirtualPortMap.values()).stream()
                        .forEach(virtualPort -> installBroadcastRule(node, virtualPort, type));
                node.getGatewayTunnelPortNumbers().forEach(gatewayPort ->
                        installer.programTunnelIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION), gatewayPort, type));
            });
            addBucketToGroupTable(gateway);
        } else if(type.equals(Objective.Operation.REMOVE)) {
            log.info("Gateway {} deleted", gateway.toString());
            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream().forEach(node -> {
                node.getGatewayTunnelPortNumbers().forEach(gatewayPort ->
                        installer.programTunnelIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION), gatewayPort, type));
                installer.programGatewayIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                        gateway.getGatewayPortNumber(), type);
                //erase broadcasting rule going out to deactived gateway
                Sets.newHashSet(hostVirtualPortMap.values()).stream()
                        .forEach(virtualPort -> installBroadcastRule(node, virtualPort, type));
                Sets.newHashSet(hostVirtualPortMap.values()).stream()
                        .forEach(virtualPort -> installBroadcastRule(node, virtualPort, Objective.Operation.ADD));
                bridgeHandler.destroyGatewayTunnel(gateway, node);
            });
            deleteBucketFromGroupTable(gateway);
        } else if(type.equals(Objective.Operation.ADD_TO_EXISTING)) {
            log.info("Gateway {} updated", gateway.toString());
            Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                    .forEach(node -> {
                        bridgeHandler.updateGatewayTunnel(gateway, node);
            });
        }
    }

    private boolean createGroupTable(DeviceId deviceId) {
        log.info("Create Group table");
        List<GroupBucket> empty_list = Lists.newArrayList();

        if (groupService.getGroup(deviceId, groupKey) == null) {
            GroupDescription groupDescription = new DefaultGroupDescription(deviceId,
                    GroupDescription.Type.SELECT, new GroupBuckets(empty_list), groupKey, 1, appId);
            groupService.addGroup(groupDescription);
            return true;
        }
        return false;
    }

    private void addBucketToGroupTable(Gateway gateway) {
        log.info("Add bucket to GroupTable");
        List<GroupBucket> gateway_bucket = Lists.newArrayList();
        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        short weight = gateway.getWeight();
        builder.setOutput(gateway.getGatewayPortNumber());
        GroupBucket bucket = DefaultGroupBucket.createSelectGroupBucket(builder.build(), weight);

        gateway_bucket.add(bucket);
        bucketMap.put(gateway, bucket);
        GroupBuckets groupbuckets = new GroupBuckets(gateway_bucket);


        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(node ->
                    (groupService.getGroup(node.getBridgeId(Bridge.BridgeType.INTEGRATION), groupKey) != null))
                .forEach(node -> {
                    if(groupService.getGroup(node.getBridgeId(Bridge.BridgeType.INTEGRATION), groupKey) == null) {
                    }
                    groupService.addBucketsToGroup(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            groupKey, groupbuckets, groupKey, appId);
                });
    }

    private void deleteBucketFromGroupTable(Gateway gateway) {
        log.info("Delete bucket from GroupTable");
        //this list will hold buckets that needs to be removed
        List<GroupBucket> removing_bucketList = Lists.newArrayList();

        GroupBucket bucket = bucketMap.get(gateway);
        if(bucket == null) {
            return;
        }
        removing_bucketList.add(bucket);
        GroupBuckets groupbuckets = new GroupBuckets(removing_bucketList);

        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(node ->
                        (groupService.getGroup(node.getBridgeId(Bridge.BridgeType.INTEGRATION), groupKey) != null))
                .forEach(node -> {
                    groupService.removeBucketsFromGroup(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                            groupKey, groupbuckets, groupKey, appId);
        });

        bucketMap.remove(gateway);
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
                .forEach(e -> installer.programTunnelIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION), e, type));

//        Set<PortNumber> tunnel_gateway_ports = new HashSet<>();
//        gatewayStore.values().stream().forEach(gwport -> {
//            tunnel_gateway_ports.add(gwport);
//        });
//


//        //Jimin!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//        Sets.newHashSet(gatewayService.getGateways()).stream().forEach(gatewayPort -> {
//            installer.programGatewayIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                    gatewayPort.getGatewayPortNumber(), type);
//        });

        // For remote Openstack nodes (gateway included)
        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
                .filter(e -> !e.equals(node))
                .filter(e -> e.getState().containsAll(EnumSet.of(TUNNEL_CREATED)))
                .forEach(e -> {
//                    tunnel_gateway_ports.add(e.getTunnelPortNumber(node.id()));
                       installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
                                segmentationId, e.getTunnelPortNumber(node.id()), mac, type);


//                    installer.programLocalIn(e.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            segmentationId, e.getTunnelPortNumber(node.id()), mac, type);
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
        //TODO
//        Sets.newHashSet(virtualMachineService.getVirtualMachines()).stream()
//                .filter(e -> e.segmentationId().equals(segmentationId))
//                .forEach(e -> {
//                    installer.programLocalIn(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
//                            segmentationId, node.getGatewayTunnelPortNumber(),
//                            e.macAddress(), type);
//                });
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

        if(segmentationId == null) {
            log.info("segmentationId should not be null");
            return;
        }

        if(portNumber == null) {
            log.info("portNumber should not be null");
            return;
        }

        Set<PortNumber> allPorts = new HashSet<>();
        Set<PortNumber> tunnelPorts = Sets.newHashSet(node.getTunnelPortNumbers());
        Set<PortNumber> virtualPorts = Sets.newHashSet(node.getVirutalPortNumbers(segmentationId));
        Set<PortNumber> gatewayPorts = new HashSet<>();
        Sets.newHashSet(gatewayService.getGateways()).forEach(gateway -> gatewayPorts.add(gateway.getGatewayPortNumber()));

        // Add local virtual ports & tunnel ports for entire out ports
        allPorts.addAll(virtualPorts);
        allPorts.addAll(tunnelPorts);
        allPorts.addAll(gatewayPorts);

        installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                     segmentationId, allPorts, type);

        if (type == Objective.Operation.REMOVE) {
            // Broadcasting rules should be added again when removed
            virtualPorts.remove(portNumber);
            tunnelPorts.remove(portNumber);
            gatewayPorts.remove(portNumber);
            allPorts.remove(portNumber);

            installer.programBroadcast(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                    segmentationId, allPorts, Objective.Operation.ADD);
        }
    }

    private void processArp(ARP arpPacket, PortNumber inPort) {
        MacAddress targetHostMac = MacAddress.valueOf(arpPacket.getTargetHardwareAddress());
        MacAddress senderVmMac = MacAddress.valueOf(arpPacket.getSenderHardwareAddress());

        Iterator<Host> hosts = hostService.getHostsByMac(targetHostMac).iterator();
        if(!hosts.hasNext()) {
            return;
        }

        Host host = hosts.next();
        String ifaceId = host.annotations().value(IFACEID);
        if (ifaceId == null) {
            return;
        }

        //get segmentaionId
        VirtualPortId virtualPortId = VirtualPortId.portId(ifaceId);
        VirtualPort virtualPort = virtualPortService.getPort(virtualPortId);
        TenantNetwork tenantNetwork = tenantNetworkService.getNetwork(virtualPort.networkId());
        SegmentationId segmentationId = tenantNetwork.segmentationId();

        Gateway gateway = gatewayService.getGateway(inPort);
        if(gateway == null) {
            log.error("gateway can not be null");
            return;
        }


        Sets.newHashSet(nodeManagerService.getOpenstackNodes()).stream()
            .filter(e -> e.getState().contains(GATEWAY_CREATED))
            .forEach(node -> {
             installer.programLocalInWithGateway(node.getBridgeId(Bridge.BridgeType.INTEGRATION),
                        segmentationId, senderVmMac, Objective.Operation.ADD);
            });
    }

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
                eventExecutor.submit(() -> processGateway(gateway, Objective.Operation.ADD));
            } else if (GatewayEvent.Type.GATEWAY_REMOVE == event.type()) {
                eventExecutor.submit(() -> processGateway(gateway, Objective.Operation.REMOVE));
            } else if (GatewayEvent.Type.GATEWAY_UPDATE == event.type()) {
                eventExecutor.submit(() -> processGateway(gateway, Objective.Operation.ADD_TO_EXISTING));
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

            if(ethernet.getEtherType() == Ethernet.TYPE_ARP) {
                ARP arpPacket = (ARP) ethernet.getPayload();
                if(arpPacket.getOpCode() == ARP.OP_REPLY) {
                    processArp(arpPacket, sourcePoint);
                }
            }
       }
    }
}