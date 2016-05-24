package org.iris4sdn.csdncm.vnetmanager;

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

    private DeviceId intBridgeId;
    private final MacAddress macAddress;
    private final IpAddress dataNetworkIp;
    private final Set<PortNumber> gatewayPortNumbers = new HashSet<>();
    private PortNumber gatewayPortNumber;

    public Gateway(MacAddress macAddress, IpAddress dataNetworkIp) {
        this.macAddress = checkNotNull(macAddress);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
    }



    public MacAddress macAddress() {
        return macAddress;
    }

    public IpAddress getDataNetworkIp() {
        return dataNetworkIp;
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
