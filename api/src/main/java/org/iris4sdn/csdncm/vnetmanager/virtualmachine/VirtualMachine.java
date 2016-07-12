package org.iris4sdn.csdncm.vnetmanager.virtualmachine;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.vtnrsc.SegmentationId;

public interface VirtualMachine {
    VirtualMachineId id();

    SegmentationId segmentationId();

//    TenantId tenantId();

    IpAddress ipAddress();

//    String name();

//    DeviceId deviceId();

    MacAddress macAddress();

}
