package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.event.ListenerService;
import org.onosproject.net.PortNumber;

import java.util.List;

public interface GatewayService extends ListenerService<GatewayEvent,GatewayListener> {

    Iterable<Gateway> getGateways();
    void addGatewayList(List<Gateway> gatewayList);
    Gateway getGateway(PortNumber inPort);
    void createGateway(String id, String name, MacAddress macAddress, IpAddress dataNetworkIp,
                              short weight, String state, boolean updated);
}
