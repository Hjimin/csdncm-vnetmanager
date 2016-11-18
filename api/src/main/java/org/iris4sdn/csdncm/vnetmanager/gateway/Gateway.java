package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;
public class Gateway {

    public enum State {
        CONFIGURED,
        PORT_CREATED,
        BUCKET_IN,
        ACTIVATE
    }
    private String name;
    private DeviceId intBridgeId;
    private MacAddress macAddress;
    private short weight;
    private IpAddress dataNetworkIp;
    private final Map<DeviceId, PortNumber> gatewayPortNumbers  = new HashMap<>();
    private final String nodeId;
    private String activate;
    private boolean updated;
    private final Set<State> currentState = new HashSet<>();

    public Gateway(String id, String name, MacAddress macAddress, IpAddress dataNetworkIp,
                   short weight, String state, boolean updated) {
        this.nodeId = checkNotNull(id);
        this.name = checkNotNull(name);
        this.macAddress = checkNotNull(macAddress);
        this.dataNetworkIp = checkNotNull(dataNetworkIp);
        this.weight = checkNotNull(weight);
        this.activate = state;
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

    public String getActivateState() {
        return activate;
    }


    public boolean isActive() {
        if(activate.equals("active")) {
            return true;
        } else if (activate.equals("deactive")) {
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

    public void changeActivateState(String state) {
        this.activate = state;
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

    public void setGatewayPortNumber(DeviceId id, PortNumber gatewayPortNumber) {
        checkNotNull(id);
        checkNotNull(gatewayPortNumber);
        gatewayPortNumbers.putIfAbsent(id, gatewayPortNumber);
    }

    public PortNumber getGatewayPortNumber(DeviceId id) {
        return gatewayPortNumbers.get(id);
    }
}
