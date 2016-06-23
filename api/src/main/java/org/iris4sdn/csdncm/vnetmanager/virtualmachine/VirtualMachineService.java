package org.iris4sdn.csdncm.vnetmanager.virtualmachine;

import org.onlab.packet.Ip4Address;
import org.onosproject.event.ListenerService;

public interface VirtualMachineService
        extends ListenerService<VirtualMachineEvent,VirtualMachineListener> {

    boolean addVirtualMachine(Iterable<VirtualMachine> vms);

    boolean deleteVirtualMachine(Iterable<VirtualMachineId> vmIds);

    Iterable<VirtualMachine> getVirtualMachines();


    VirtualMachine getVirtualMachineByIp(Ip4Address vmIp);
}
