package org.iris4sdn.csdncm.vnetmanager.virtualmachine;

import org.onosproject.event.AbstractEvent;

public class VirtualMachineEvent
        extends AbstractEvent<VirtualMachineEvent.Type, VirtualMachine> {
    public enum Type {
        VIRTUAL_MACHINE_PUT,
        VIRTUAL_MACHINE_REMOVE,
    }

    public VirtualMachineEvent(Type type, VirtualMachine virtualMachine) {
        super(type,virtualMachine);
    }

    public VirtualMachineEvent(Type type, VirtualMachine virtualMachine, long time) {
        super(type,virtualMachine,time);
    }
}
