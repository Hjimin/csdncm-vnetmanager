package org.iris4sdn.csdncm.vnetmanager.cli;

import com.google.common.collect.Lists;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNode;
import org.iris4sdn.csdncm.vnetmanager.VnetManagerService;

import java.util.Collections;
import java.util.List;

/**
 * Lists all Openstack nodes registered in VnetManager service.
 */
@Command(scope = "onos", name = "openstack-nodes",
        description = "Lists all nodes registered in VnetManager Service")
public class OpenstackNodeListCommand extends AbstractShellCommand {

    @Override
    protected void execute() {
        VnetManagerService service = AbstractShellCommand.get(VnetManagerService.class);
        List<OpenstackNode> nodes = Lists.newArrayList(service.getOpenstackNodes());
        Collections.sort(nodes, OpenstackNode.OPENSTACK_NODE_COMPARATOR);

        int nodeCount = 0;
        for (OpenstackNode node : nodes) {
            print("hostname=%s, manageNetworkIP=%s, dataNetworkIP=%s, " +
                    "State=%s, TunnelType=%s, NodeType=%s",
                    node.name(),
                    node.getManageNetworkIp().toString(),
                    node.getDataNetworkIp().toString(),
                    node.getState().toString(),
                    node.getTunnelType().toString(),
                    node.getNodeType().toString());

            nodeCount++;
        }
        print("Total %s nodes", nodeCount);
    }

}
