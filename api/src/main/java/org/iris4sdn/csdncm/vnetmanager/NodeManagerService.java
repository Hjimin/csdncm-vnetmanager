package org.iris4sdn.csdncm.vnetmanager;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.vtnrsc.VirtualPortId;

public interface NodeManagerService {
    Gateway getGateway(PortNumber inPort);
    Iterable<PortNumber> getGatewayPorts();
    Iterable<Gateway> getGateways();
    void addGateway(Gateway gateway);
    void deleteGateway(Gateway gateway);

    void addOpenstackNode(OpenstackNode node);

    void deleteOpenstackNode(OpenstackNode node);

    Iterable<OpenstackNode> getOpenstackNodes();

    OpenstackNode getOpenstackNode(DeviceId deviceId);

    OpenstackNode getOpenstackNode(String hostName);

    OpenstackNode getOpenstackNode(VirtualPortId virtualPortId);
}
