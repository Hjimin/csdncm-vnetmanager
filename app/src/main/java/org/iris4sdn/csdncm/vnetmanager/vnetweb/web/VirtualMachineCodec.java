package org.iris4sdn.csdncm.vnetmanager.vnetweb.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.iris4sdn.csdncm.vnetmanager.virtualmachine.VirtualMachine;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;

import static com.google.common.base.Preconditions.checkNotNull;

public final class VirtualMachineCodec extends JsonCodec<VirtualMachine> {

    @Override
    public ObjectNode encode(VirtualMachine vm, CodecContext context) {
        checkNotNull(vm, "Virtual Machine cannot be null");
        ObjectNode result = context
                .mapper()
                .createObjectNode()
                .put("vm_id", vm.id().toString())
                .put("segmentation_id", vm.segmentationId().toString())
                .put("device_ip", vm.ipAddress().toString())
                .put("device_mac", vm.macAddress().toString());
        return result;
    }

}
