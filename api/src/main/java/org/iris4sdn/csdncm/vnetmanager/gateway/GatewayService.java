package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.onosproject.event.ListenerService;
import org.onosproject.net.PortNumber;

import java.util.List;

public interface GatewayService extends ListenerService<GatewayEvent,GatewayListener> {

    Iterable<Gateway> getGateways();
    void addGateway(List<Gateway> gatewayList);


    Gateway getGateway(PortNumber inPort);
}
