package org.iris4sdn.csdncm.vnetmanager.cli;

import org.apache.karaf.shell.commands.Command;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachine;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachineService;
import org.onosproject.cli.AbstractShellCommand;

import java.util.Iterator;

/**
 * Created by gurum on 16. 2. 24.
 */
@Command(scope = "onos", name = "vm",
        description = "Lists all vm registered in L3Manager Service")
public class VMListCommand extends AbstractShellCommand{
    private static final String FMT = "vm=%s";

    @Override
    protected void execute() {
        VirtualMachineService service = get(VirtualMachineService.class);
        Iterator<VirtualMachine> vms = service.getVirtualMachines().iterator();
        while(vms.hasNext()){
            VirtualMachine vm = vms.next();
            printVM(vm);
        }
    }

    private void printVM(VirtualMachine vm) {
        print(FMT, vm);
    }
}




