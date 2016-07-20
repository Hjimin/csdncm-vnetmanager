package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import static com.google.common.base.Preconditions.checkNotNull;
public class Gateway {

    private String name;
    private DeviceId intBridgeId;
    private MacAddress macAddress;
    private short weight;
    private IpAddress dataNetworkIp;
    private PortNumber gatewayPortNumber;
    private final String nodeId;
    private String state;
    private boolean updated;

    public Gateway(String id, String name, MacAddress macAddress, IpAddress dataNetworkIp,
                   short weight, String state, boolean updated) {
        this.nodeId = checkNotNull(id);
        this.name = checkNotNull(name);
        this.macAddress = checkNotNull(macAddress);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
        this.weight = checkNotNull(weight);
        this.state = state;
        this.updated = updated;
    }

    public String id() {
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

    public String getState() {
        return state;
    }


    public boolean isActive() {
        if(state.equals("active")) {
            return true;
        } else if (state.equals("deactive")) {
            return false;
        }
        return false;
    }

    public boolean isUpdated() {
         if(updated) {
            return true;
        }
        return false;
    }

    public void changeName(String name){
       this.name = name;
    }

    public void changeMac(MacAddress macAddress) {
        this.macAddress = macAddress;
    }

    public void changeIp(IpAddress ipAddress) {
        this.dataNetworkIp = ipAddress;
    }

    public void changeWeight(short weight) {
        this.weight = weight;
    }

    public void changeState(String state) {
        this.state = state;
    }

    public void changeUpdated(boolean updated) {
        this.updated = updated;
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
