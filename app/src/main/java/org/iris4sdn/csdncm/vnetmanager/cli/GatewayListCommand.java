package org.iris4sdn.csdncm.vnetmanager.cli;

import com.google.common.collect.Lists;
import org.apache.karaf.shell.commands.Command;
import org.iris4sdn.csdncm.vnetmanager.gateway.Gateway;
import org.iris4sdn.csdncm.vnetmanager.gateway.GatewayService;
import org.onosproject.cli.AbstractShellCommand;

import java.util.List;

/**
 * Created by gurum on 16. 6. 28.
 */


@Command(scope = "onos", name = "gateways",
        description = "Lists all nodes registered in VnetManager Service")
public class GatewayListCommand extends AbstractShellCommand {

    @Override
    protected void execute() {
        GatewayService service = AbstractShellCommand.get(GatewayService.class);
        List<Gateway> gateways = Lists.newArrayList(service.getGateways());

        int nodeCount = 0;
        for (Gateway gateway : gateways) {
            print("gatewayname=%s, mac=%s, dataNetworkIP=%s, " +
                            "weight=%d",
                    gateway.gatewayName(),
                    gateway.macAddress().toString(),
                    gateway.getDataNetworkIp().toString(),
                    gateway.getWeight());

            nodeCount++;
        }
        print("Total %s gateway count", nodeCount);
    }

}
