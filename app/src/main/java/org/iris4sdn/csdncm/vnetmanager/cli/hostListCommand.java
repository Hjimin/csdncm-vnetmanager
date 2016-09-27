package org.iris4sdn.csdncm.vnetmanager.cli;

import org.apache.karaf.shell.commands.Command;
import org.iris4sdn.csdncm.vnetmanager.VnetManagerService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.HostId;

import java.util.Iterator;

/**
 * Created by gurum on 16. 2. 24.
 */
@Command(scope = "onos", name = "myHost",
        description = "Lists all instance registered in Vnetmanager Service")
public class hostListCommand extends AbstractShellCommand{
    private static final String FMT = "myhost=%s ifaceid=%s";

    @Override
    protected void execute() {
        VnetManagerService service = get(VnetManagerService.class);
        Iterator<HostId> hosts = service.getHosts().iterator();
        int nodeCount = 0;
        while(hosts.hasNext()){
            HostId host = hosts.next();
            String id = service.getId(host);
            printHost(host, id);
            nodeCount++;
        }
        print("Total count %s", nodeCount);
    }

    private void printHost(HostId host, String id) {
        print(FMT, host, id);
    }
}




