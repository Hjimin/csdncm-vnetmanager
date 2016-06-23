package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNodeId;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
public class Gateway {

    private String name;
    private DeviceId intBridgeId;
    private final MacAddress macAddress;
    private final short weight;
    private final IpAddress dataNetworkIp;
    private final Set<PortNumber> gatewayPortNumbers = new HashSet<>();
    private PortNumber gatewayPortNumber;
    private final OpenstackNodeId nodeId;

    public Gateway(String name, MacAddress macAddress, IpAddress dataNetworkIp, short weight) {
        this.nodeId = OpenstackNodeId.valueOf(name);
        this.name = checkNotNull(name);
        this.macAddress = checkNotNull(macAddress);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
        this.weight = checkNotNull(weight);
    }

    public OpenstackNodeId id() {
        return nodeId;
    }

    public String gatewayName() {
        return name;
    }

    public MacAddress macAddress() {
        return macAddress;
    }

    public IpAddress getDataNetworkIp() {
        return dataNetworkIp;
    }

    public Short getWeight(){
        return weight;
    }

    public void setBridgeId(DeviceId bridgeId, Bridge.BridgeType type) {
        checkNotNull(bridgeId);
        checkNotNull(type);
        if (type.equals(Bridge.BridgeType.INTEGRATION))
            this.intBridgeId = bridgeId;
    }

    public DeviceId getBridgeId(Bridge.BridgeType type) {
        if (type.equals(Bridge.BridgeType.INTEGRATION))
            return intBridgeId;

        return null;
    }

    public void setGatewayTunnelPortNumber(PortNumber portNumber) {
        checkNotNull(portNumber);
        gatewayPortNumbers.add(portNumber);
    }

    public void setGatewayPortNumber(PortNumber gatewayPortNumber) {
       this.gatewayPortNumber = gatewayPortNumber;
    }
    public PortNumber getGatewayPortNumber() {
        return gatewayPortNumber;
    }
    public Set<PortNumber> getGatewayPortNumbers() {
        return Collections.unmodifiableSet(gatewayPortNumbers
                .stream().collect(Collectors.toSet()));
    }
}
