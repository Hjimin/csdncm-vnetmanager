package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.iris4sdn.csdncm.vnetmanager.OpenstackNode;
import org.onosproject.event.ListenerService;
import org.onosproject.net.PortNumber;

import java.util.List;

public interface GatewayService
        extends ListenerService<GatewayEvent,GatewayListener> {

//    boolean addGateway(Iterable<Gateway> gateways);
//
//    boolean deleteGateway(Iterable<OpenstackNodeId> gatewayIds);
    boolean checkForUpdate(OpenstackNode node);
    void setUpdate(boolean updated);


    Iterable<Gateway> getGateways();
    void addGateway(List<Gateway> gatewayList);

    void deleteGateway(Gateway gateway);

    Gateway getGateway(PortNumber inPort);
}
