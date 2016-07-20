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
package org.iris4sdn.csdncm.vnetmanager;

import com.google.common.base.MoreObjects;
import com.google.common.collect.Sets;
import org.onlab.packet.IpAddress;
import org.onosproject.incubator.net.tunnel.Tunnel;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.vtnrsc.FixedIp;
import org.onosproject.vtnrsc.SegmentationId;
import org.onosproject.vtnrsc.VirtualPort;
import org.onosproject.vtnrsc.VirtualPortId;

import java.util.*;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represent Openstack physical node.
 */
public class OpenstackNode {
    public enum Type {
        COMPUTE,
        NETWORK,
        CONTROL,
        GATEWAY
    }

    // State is EnumSet for getting more than two states.
    public enum State {
        CONFIGURED,
        OVSDB_CONNECTED,
        BRIDGE_CREATED,
        EXTERNAL_BRIDGE_DETECTED,
        INTEGRATION_BRIDGE_DETECTED,
        GATEWAY_CREATED,
        GATEWAY_GROUP_CREATED,
        TUNNEL_CREATED,
    }

    //controllerId -> ovsdb id
    private DeviceId ovsdbId;
    private DeviceId exBridgeId;
    private DeviceId intBridgeId;
    private PortNumber gatewayTunnelPortNumber;
    private Port exPatchPort;
    private Port intPatchPort;
    private Port exPort;

    private final OpenstackNodeId nodeId;
    private final Set<State> currentState = new HashSet<>();
    private final Set<VirtualPort> virtualPorts = new HashSet<>();
    private final Map<VirtualPortId, PortNumber> virtualPortNumbers = new HashMap<>();
    private final Map<VirtualPortId, SegmentationId> segmentationIds = new HashMap<>();
    private final Map<OpenstackNodeId, PortNumber> tunnelPortNumbers  = new HashMap<>();
    private final Map<String, PortNumber> gatewayTunnelPortNumbers  = new HashMap<>();
    private final Map<SegmentationId, Set<VirtualPort>> tenantVirtualPorts = new HashMap<>();

    // Configuration information
    private String hostName;
    private IpAddress publicNetworkIp;
    private IpAddress manageNetworkIp;
    private IpAddress dataNetworkIp;
    private Tunnel.Type tunnelType;
    private Type nodeType;

    public static final Comparator<OpenstackNode> OPENSTACK_NODE_COMPARATOR =
            (node1, node2) -> node1.name().compareTo(node2.name());

    public OpenstackNode(String hostName, IpAddress publicNetworkIp,
                         IpAddress manageNetworkIp, IpAddress dataNetworkIp,
                         Tunnel.Type tunnelType, Type nodeType) {
        this.nodeId = OpenstackNodeId.valueOf(hostName);
        this.hostName = checkNotNull(hostName);
        this.publicNetworkIp = checkNotNull(publicNetworkIp);
        this.manageNetworkIp = checkNotNull(manageNetworkIp);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
        this.tunnelType = checkNotNull(tunnelType);
        this.nodeType = checkNotNull(nodeType);
    }

    public void updateOpenstackNode(String hostName, IpAddress publicNetworkIp,
                         IpAddress manageNetworkIp, IpAddress dataNetworkIp,
                         Tunnel.Type tunnelType, Type nodeType) {
        this.hostName = checkNotNull(hostName);
        this.publicNetworkIp = checkNotNull(publicNetworkIp);
        this.manageNetworkIp = checkNotNull(manageNetworkIp);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
        this.tunnelType = checkNotNull(tunnelType);
        this.nodeType = checkNotNull(nodeType);

//        controllerId = null;
////        exBridgeId = null;
////        intBridgeId = null;
////        currentState.clear();
//        virtualPorts.clear();
//        virtualPortNumbers.clear();
//        segmentationIds.clear();
//        tunnelPortNumbers.clear();
//        tenantVirtualPorts.clear();
    }

    public OpenstackNodeId id() { return nodeId; }

    public String name() {
        return hostName;
    }

    public IpAddress getPublicNetworkIp() {
        return publicNetworkIp;
    }

    public IpAddress getManageNetworkIp() {
        return manageNetworkIp;
    }

    public IpAddress getDataNetworkIp() {
        return dataNetworkIp;
    }

    public Tunnel.Type getTunnelType() { return tunnelType; }

    public Type getNodeType() { return nodeType; }

    public void setOvsdbId(DeviceId ovsdbId) {
        this.ovsdbId = ovsdbId;
    }

    public DeviceId getOvsdbId() {
        return ovsdbId;
    }

    public void setBridgeId(DeviceId bridgeId, Bridge.BridgeType type) {
        checkNotNull(bridgeId);
        checkNotNull(type);
        if (type.equals(Bridge.BridgeType.EXTERNAL))
            this.exBridgeId = bridgeId;
        if (type.equals(Bridge.BridgeType.INTEGRATION))
            this.intBridgeId = bridgeId;
    }

    public DeviceId getBridgeId(Bridge.BridgeType type) {
        if (type.equals(Bridge.BridgeType.EXTERNAL))
            return exBridgeId;
        if (type.equals(Bridge.BridgeType.INTEGRATION))
            return intBridgeId;

        return null;
    }

    public Set<State> getState() {
        return Collections.unmodifiableSet(currentState);
    }

    public Set<State> applyState(State state) {
        checkNotNull(state);
        currentState.addAll(EnumSet.of(state));
        return Collections.unmodifiableSet(currentState);
    }

    public Set<State> deleteState(State state) {
        checkNotNull(state);
        currentState.removeAll(EnumSet.of(state));
        return Collections.unmodifiableSet(currentState);
    }

    /**
     * Transit to initial configured state.
     */
    public void initState() {
        ovsdbId = null;
        exBridgeId = null;
        intBridgeId = null;
        currentState.clear();
        virtualPorts.clear();
        virtualPortNumbers.clear();
        segmentationIds.clear();
        tunnelPortNumbers.clear();
        gatewayTunnelPortNumbers.clear();
        tenantVirtualPorts.clear();

        applyState(State.CONFIGURED);
    }

    public void addVirtualPort(VirtualPort port, PortNumber portNumber,
                               SegmentationId segmentationId) {
        checkNotNull(port);
        checkNotNull(portNumber);
        checkNotNull(segmentationId);

        virtualPorts.add(port);
        virtualPortNumbers.putIfAbsent(port.portId(), portNumber);
        segmentationIds.putIfAbsent(port.portId(), segmentationId);

        if (tenantVirtualPorts.containsKey(segmentationId)) {
            tenantVirtualPorts.get(segmentationId).add(port);
        } else {
            tenantVirtualPorts.put(segmentationId, Sets.newHashSet(port));
        }
    }

    public void removeVirtualPort(VirtualPort port) {
        checkNotNull(port);

        virtualPorts.remove(port);
        virtualPortNumbers.remove(port.portId());

        SegmentationId segmentationId = segmentationIds.remove(port.portId());
        tenantVirtualPorts.get(segmentationId).remove(port);
    }

    public Set<VirtualPort> getVirtualPorts() {
        return Collections.unmodifiableSet(virtualPorts) ;
    }

    public VirtualPort getVirtualPort(VirtualPortId portId) {
        checkNotNull(portId);
        return virtualPorts.stream().filter(e -> e.portId().equals(portId))
                .findFirst().orElse(null);
    }

    public VirtualPort getVirtualPort(FixedIp fixedIp) {
        checkNotNull(fixedIp);
        List<VirtualPort> vPorts = new ArrayList<>();
        virtualPorts.stream().forEach(p -> {
            Iterator<FixedIp> fixedIps = p.fixedIps().iterator();
            while (fixedIps.hasNext()) {
                if (fixedIps.next().equals(fixedIp)) {
                    vPorts.add(p);
                    break;
                }
            }
        });
        if (vPorts.size() == 0) {
            return null;
        }
        return vPorts.get(0);
    }

    public VirtualPort getVirtualPort(PortNumber portNumber) {
        checkNotNull(portNumber);
        return virtualPorts.stream().filter(e ->
                virtualPortNumbers.get(e.portId()).equals(portNumber))
                .findFirst().orElse(null);
    }

    public Set<VirtualPort> getVirtualPorts(SegmentationId segmentationId) {
        checkNotNull(segmentationId);
        return Collections.unmodifiableSet(tenantVirtualPorts.get(segmentationId));
    }

    public PortNumber getVirutalPortNumber(VirtualPortId portId) {
        checkNotNull(portId);
        return virtualPortNumbers.get(portId);
    }

    public SegmentationId getSegmentationId(VirtualPortId portId) {
        checkNotNull(portId);
        return segmentationIds.get(portId);
    }

    public Set<PortNumber> getVirutalPortNumbers() {
        return Collections.unmodifiableSet(virtualPortNumbers
                .values().stream().collect(Collectors.toSet()));
    }

    public Set<PortNumber> getVirutalPortNumbers(SegmentationId segmentationId) {
        Set<VirtualPort> virtualPorts = tenantVirtualPorts.get(segmentationId);
        if (virtualPorts == null) {
            return null;
        }

        Set<PortNumber> portNumbers = new HashSet<>();
        virtualPorts.stream().forEach(e ->
                portNumbers.add(virtualPortNumbers.get(e.portId())));
        return Collections.unmodifiableSet(portNumbers);
    }

    public void addTunnelPortNumber(OpenstackNodeId id, PortNumber portNumber) {
        checkNotNull(id);
        checkNotNull(portNumber);
        tunnelPortNumbers.putIfAbsent(id, portNumber);
    }

    public void addGatewayTunnelPortNumber(String id, PortNumber portNumber) {
        checkNotNull(id);
        checkNotNull(portNumber);
        gatewayTunnelPortNumbers.putIfAbsent(id, portNumber);
    }

    public void removeTunnelPortNumber(OpenstackNodeId id) {
        checkNotNull(id);
        tunnelPortNumbers.remove(id);
    }

    public void removeGatewayTunnelPortNumber(String id) {
        checkNotNull(id);
        gatewayTunnelPortNumbers.remove(id);
    }

    public PortNumber getTunnelPortNumber(OpenstackNodeId id) {
        checkNotNull(id);
        return tunnelPortNumbers.get(id);
    }

    public PortNumber getGatewayTunnelPortNumber(OpenstackNodeId id) {
        checkNotNull(id);
        return gatewayTunnelPortNumbers.get(id);
    }

    public Set<PortNumber> getTunnelPortNumbers() {
        return Collections.unmodifiableSet(tunnelPortNumbers.values()
                .stream().collect(Collectors.toSet()));
    }

    public Set<PortNumber> getGatewayTunnelPortNumbers() {
        return Collections.unmodifiableSet(gatewayTunnelPortNumbers.values()
                .stream().collect(Collectors.toSet()));
    }

    public void setGatewayTunnelPortNumber(PortNumber portNumber) {
        checkNotNull(portNumber);
        gatewayTunnelPortNumber = portNumber;
    }

    public PortNumber getGatewayTunnelPortNumber() {
        checkNotNull(gatewayTunnelPortNumber);
        return gatewayTunnelPortNumber;
    }

    public void setPatchPort(Port patchPort, Bridge.BridgeType type) {
        checkNotNull(patchPort);
        checkNotNull(type);

        if (type.equals(Bridge.BridgeType.EXTERNAL))
            exPatchPort = patchPort;
        if (type.equals(Bridge.BridgeType.INTEGRATION))
            intPatchPort = patchPort;
    }

    public Port getPatchPort(Bridge.BridgeType type) {
        checkNotNull(type);

        if (type.equals(Bridge.BridgeType.EXTERNAL))
            return exPatchPort;
        if (type.equals(Bridge.BridgeType.INTEGRATION))
            return intPatchPort;

        return null;
    }

    public void setExPort(Port exPort) {
        checkNotNull(exPort);
        this.exPort = exPort;
    }

    public Port getExPort() {
        return exPort;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof OpenstackNode) {
            OpenstackNode that = (OpenstackNode) obj;
            if (Objects.equals(hostName, that.hostName) &&
                    Objects.equals(manageNetworkIp, that.manageNetworkIp) &&
                    Objects.equals(dataNetworkIp, that.dataNetworkIp) &&
                    Objects.equals(tunnelType, that.tunnelType) &&
                    Objects.equals(nodeType, that.nodeType)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hostName, manageNetworkIp, dataNetworkIp, tunnelType, nodeType);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("hostName", hostName)
                .add("manageNetworkIp", manageNetworkIp)
                .add("dataNetworkIp", dataNetworkIp)
                .add("nodeType", nodeType)
                .toString();
    }
}
