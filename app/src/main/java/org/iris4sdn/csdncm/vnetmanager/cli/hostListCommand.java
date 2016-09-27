package org.iris4sdn.csdncm.vnetmanager.cli;

import org.apache.karaf.shell.commands.Command;
import org.iris4sdn.csdncm.vnetmanager.VnetManagerService;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.Host;

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
        Iterator<Host> hosts = service.getHosts().iterator();
        while(hosts.hasNext()){
            Host host = hosts.next();
            String id = service.getId(host);
            printHost(host, id);
        }
    }

    private void printHost(Host host, String id) {
        print(FMT, host, id);
    }
}




