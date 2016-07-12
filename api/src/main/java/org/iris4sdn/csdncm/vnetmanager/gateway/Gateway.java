package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNodeId;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import static com.google.common.base.Preconditions.checkNotNull;
public class Gateway {

    private final String name;
    private DeviceId intBridgeId;
    private final MacAddress macAddress;
    private final short weight;
    private final IpAddress dataNetworkIp;
    private PortNumber gatewayPortNumber;
    private final OpenstackNodeId nodeId;
    private final String active;
    private boolean update;

    public Gateway(String name, MacAddress macAddress, IpAddress dataNetworkIp, short weight, String active) {
        this.nodeId = OpenstackNodeId.valueOf(name);
        this.name = checkNotNull(name);
        this.macAddress = checkNotNull(macAddress);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
        this.weight = checkNotNull(weight);
        this.active = active;
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


    public boolean isActive() {
        if(active.equals("active")) {
            return true;
        } else if (active.equals("deactive")) {
            return false;
        }
        return false;
    }

    public void update(boolean update) {
        this.update = update;
    }
    public boolean isUpdated() {
        return this.update;
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

    public void setGatewayPortNumber(PortNumber gatewayPortNumber) {
       this.gatewayPortNumber = gatewayPortNumber;
    }
    public PortNumber getGatewayPortNumber() {
        return gatewayPortNumber;
    }
}
