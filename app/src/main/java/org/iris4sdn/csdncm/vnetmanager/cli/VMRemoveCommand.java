package org.iris4sdn.csdncm.vnetmanager.cli;

import org.apache.karaf.shell.commands.Command;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachine;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineId;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineService;
import org.onosproject.cli.AbstractShellCommand;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by gurum on 16. 2. 24.
 */
@Command(scope = "onos", name = "vm-remove",
        description = "Lists all vm registered in L3Manager Service")
public class VMRemoveCommand extends AbstractShellCommand{
    private static final String FMT = "vm=%s";

    @Override
    protected void execute() {
        VirtualMachineService service = get(VirtualMachineService.class);
        Set<VirtualMachineId> vmIds = new HashSet<>();
        Iterator<VirtualMachine> vms = service.getVirtualMachines().iterator();
        while (vms.hasNext()) {
            vmIds.add(vms.next().id());
        }
        service.deleteVirtualMachine(vmIds);
    }
}




