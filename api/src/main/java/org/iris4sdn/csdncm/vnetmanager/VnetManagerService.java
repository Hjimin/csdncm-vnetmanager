package org.iris4sdn.csdncm.vnetmanager;

import org.onosproject.net.Host;

public interface VnetManagerService {
    String VNETMANAGER_APP_ID = "org.onosproject.vnetmanager";

    Iterable<Host> getHosts();
    String getId(Host host);
//
//    void addGateway(Gateway gateway);
//    void deleteGateway(Gateway gateway);
//
//    void addOpenstackNode(OpenstackNode node);
//
//    void deleteOpenstackNode(OpenstackNode node);
//
//    Iterable<OpenstackNode> getOpenstackNodes();
//
//    OpenstackNode getOpenstackNode(DeviceId deviceId);
//
//    OpenstackNode getOpenstackNode(String hostName);
//
//    OpenstackNode getOpenstackNode(VirtualPortId virtualPortId);
}
