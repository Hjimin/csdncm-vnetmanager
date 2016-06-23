package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.onosproject.event.ListenerService;
import org.onosproject.net.PortNumber;

import java.util.List;

public interface GatewayService
        extends ListenerService<GatewayEvent,GatewayListener> {

//    boolean addGateway(Iterable<Gateway> gateways);
//
//    boolean deleteGateway(Iterable<OpenstackNodeId> gatewayIds);



    PortNumber getGatewayPortNumber();
    Iterable<Gateway> getGateways();
    void addGateway(List<Gateway> gatewayList);

    void setGatewayPortNumber(PortNumber portNumber);
    void deleteGateway(Gateway gateway);

}
