package org.iris4sdn.csdncm.vnetmanager;

import org.onosproject.net.DeviceId;
import org.onosproject.vtnrsc.VirtualPortId;

public interface NodeManagerService {



    void addOpenstackNode(OpenstackNode node);

    void deleteOpenstackNode(OpenstackNode node);

    Iterable<OpenstackNode> getOpenstackNodes();

    OpenstackNode getOpenstackNode(DeviceId deviceId);

    OpenstackNode getOpenstackNode(String hostName);

    OpenstackNode getOpenstackNode(VirtualPortId virtualPortId);
}
