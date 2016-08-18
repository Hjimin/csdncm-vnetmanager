package org.iris4sdn.csdncm.vnetmanager.impl;

/**
 * Created by gurum on 16. 6. 17.
 */

import org.apache.felix.scr.annotations.*;
import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.iris4sdn.csdncm.vnetmanager.NodeManagerService;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNode;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNodeId;
import org.iris4sdn.csdncm.vnetmanager.gateway.Gateway;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayEvent;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayListener;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayService;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineId;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.util.KryoNamespace;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.*;
import org.onosproject.vtnrsc.VirtualPortId;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.iris4sdn.csdncm.vnetmanager.OpenstackNode.State.BRIDGE_CREATED;
import static org.iris4sdn.csdncm.vnetmanager.OpenstackNode.State.CONFIGURED;
import static org.slf4j.LoggerFactory.getLogger;


@Component(immediate = true)
@Service
public class NodeManager extends AbstractListenerManager<GatewayEvent, GatewayListener>
        implements NodeManagerService, GatewayService {
    private final Logger log = getLogger(getClass());

    private static final String OPENSTACK_NODE_NOT_NULL = "Openstack node cannot be null";
    private static final String EVENT_NOT_NULL = "VirtualMachine event cannot be null";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LogicalClockService clockService;

    private static final String OPENSTACK_NODES = "openstack-nodes";
    private static final String GATEWAY = "multi-gateway";
    private EventuallyConsistentMap<OpenstackNodeId, OpenstackNode> nodeStore;
    private EventuallyConsistentMap<String, Gateway> gatewayStore;

    private EventuallyConsistentMapListener<String, Gateway> gatewayListener =
            new InnerGatewayListener();
//    private GatewayListener gatewayListener = new InnerGatewayListener();

    @Activate
    public void activate() {
        eventDispatcher.addSink(GatewayEvent.class,listenerRegistry);
        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(OpenstackNodeId.class)
                .register(Gateway.class)
                .register(VirtualMachineId.class);

        nodeStore = storageService
                .<OpenstackNodeId, OpenstackNode>eventuallyConsistentMapBuilder()
                .withName(OPENSTACK_NODES).withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        gatewayStore = storageService
                .<String, Gateway>eventuallyConsistentMapBuilder()
                .withName(GATEWAY).withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        gatewayStore.addListener(gatewayListener);

        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }

    @Override
    public void addGatewayList(List<Gateway> gatewayList) {
        for (Gateway gateway : gatewayList) {
            if(!gateway.isActive()) {
                if(gatewayStore.containsKey(gateway.id())) {
                    gatewayStore.remove(gateway.id());
                }
                continue;
            }
            if(gatewayStore.containsKey(gateway.id())) {
                if(gateway.isUpdated()){
                    log.info("Remove pre-configured openstack gateway {} ", gateway.id());
                    gatewayStore.remove(gateway.id());
                    gatewayStore.put(gateway.id(), gateway);
                }
                continue;
            }
            gatewayStore.put(gateway.id(), gateway);
        }
    }

    @Override
    public void createGateway(String id, String name, MacAddress macAddress, IpAddress dataNetworkIp,
                              short weight, String state, boolean updated) {
        if(state.equals("deactive")) {
            if(gatewayStore.containsKey(id)) {
                gatewayStore.remove(id);
            }
            return;
        }

        if(gatewayStore.containsKey(id)) {
            Gateway gateway = gatewayStore.get(id);
            if(updated) {
                if(!gateway.getDataNetworkIp().toString().equals(dataNetworkIp.toString())) {
                    gateway.changeIp(dataNetworkIp);
                }

                if(gateway.getWeight() != weight) {
                    gateway.changeWeight(weight);
                }

                if(!gateway.macAddress().toString().equals(macAddress.toString())) {
                    gateway.changeMac(macAddress);
                }

                if(!gateway.getActivateState().equals(state)){
                    gateway.changeActivateState(state);
                }

                if(!gateway.gatewayName().equals(name)) {
                    gateway.changeName(name);
                }
                notifyListeners(new GatewayEvent(GatewayEvent.Type.GATEWAY_UPDATE, gateway));
            }
            return;
        }

        Gateway gateway = new Gateway(id, name, macAddress, dataNetworkIp, weight, state, updated);
        gateway.applyState(Gateway.State.CONFIGURED);
        gateway.applyState(Gateway.State.ACTIVATE);
        gatewayStore.put(gateway.id(), gateway);
        log.info("New gateway \"{}\" is added", gateway.id());
    }

//    private boolean checkGatewayUpdate(Gateway gateway) {
//        Gateway old_gateway = gatewayStore.get(gateway.id());
//
//        if(!old_gateway.getDataNetworkIp().toString().equals(gateway.getDataNetworkIp().toString())) {
//            log.info("old_gateway {}", old_gateway.getDataNetworkIp().toString());
//            log.info("gateway {}", gateway.getDataNetworkIp().toString());
//            gateway.changeIp();
////            gateway.update(true);
////            notifyListeners(new GatewayEvent(GatewayEvent.Type.GATEWAY_UPDATE, gatewayList));
//            return true;
//        }
//
//        if(old_gateway.getWeight() != gateway.getWeight()) {
//            log.info("old_gateway {}", old_gateway.getWeight());
//            log.info("gateway {}", gateway.getWeight());
////            gateway.update(true);
////            notifyListeners(new GatewayEvent(GatewayEvent.Type.GATEWAY_UPDATE, gatewayList));
//            return true;
//        }
//
//        if(!old_gateway.macAddress().toString().equals(gateway.macAddress().toString())) {
//            log.info("old_gateway {}", old_gateway.macAddress().toString());
//            log.info("gateway {}", gateway.macAddress().toString());
////            gateway.update(true);
////            notifyListeners(new GatewayEvent(GatewayEvent.Type.GATEWAY_UPDATE, gatewayList));
//            return true;
//        }
//
////        gateway.update(false);
//        return false;
//    }


    @Override
    public Gateway getGateway(PortNumber inPort){
        return gatewayStore.values().stream()
                .filter(gateway -> {
                    if (gateway.getGatewayPortNumber().toString().endsWith(inPort.toString()+")")) {
//                        log.info("gateway {} ", gateway.getGatewayPortNumber());
                        return true;
                    } if (gateway.getGatewayPortNumber().toString().equals(inPort.toString())) {
                        return true;
                    } else {
                        return false;
                    }
                })
                .findFirst().orElse(null);
    }

    @Override
    public Iterable<Gateway> getGateways() {
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


    private class InnerGatewayListener
            implements
            EventuallyConsistentMapListener<String, Gateway> {

        @Override
        public void event(EventuallyConsistentMapEvent<String, Gateway> event) {
            checkNotNull(event, EVENT_NOT_NULL);
            Gateway gateway = event.value();
            if (EventuallyConsistentMapEvent.Type.PUT == event.type()) {
                notifyListeners(new GatewayEvent(
                        GatewayEvent.Type.GATEWAY_PUT, gateway));
            }
            if (EventuallyConsistentMapEvent.Type.REMOVE == event.type()) {
                notifyListeners(new GatewayEvent(
                        GatewayEvent.Type.GATEWAY_REMOVE, gateway));
            }
        }
    }

    private void notifyListeners(GatewayEvent event) {
        checkNotNull(event, EVENT_NOT_NULL);
        post(event);
    }

}
