package org.iris4sdn.csdncm.vnetmanager.virtualmachine;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.vtnrsc.SegmentationId;
import org.onosproject.vtnrsc.TenantId;

public interface VirtualMachine {
    VirtualMachineId id();

    SegmentationId segmentationId();

    TenantId tenantId();

    IpAddress ipAddress();

    String name();

    DeviceId deviceId();

    MacAddress macAddress();

}
