package org.iris4sdn.csdncm.vnetmanager.virtualmachine;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public final class VirtualMachineId {
    private final String virtualMachineId;

    private VirtualMachineId(String virtualMachineId) {
        checkNotNull(virtualMachineId, "VirtualMachineId cannot be null");
        this.virtualMachineId = virtualMachineId;
    }

    public String id() {
        return virtualMachineId;
    }

    public static VirtualMachineId virtualMachineId(String portId) {
        return new VirtualMachineId(portId);
    }

    @Override
    public int hashCode() {
        return virtualMachineId.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof VirtualMachineId) {
            final VirtualMachineId that = (VirtualMachineId) obj;
            return this.getClass() == that.getClass()
                    && Objects.equals(this.virtualMachineId, that.virtualMachineId);
        }
        return false;
    }

    @Override
    public String toString() {
        return virtualMachineId;
    }
}
