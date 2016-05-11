package org.iris4sdn.csdncm.vnetmanager.virtualmachine;

import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.DeviceId;
import org.onosproject.vtnrsc.SegmentationId;
import org.onosproject.vtnrsc.TenantId;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;

public class DefaultVirtualMachine implements VirtualMachine {
    private final VirtualMachineId id;
    private final SegmentationId segmentationId;
    private final TenantId tenantId;
    private final IpAddress ipAddress;
    private final String name;
    private final MacAddress macAddress;
    private final DeviceId deviceId;

    public DefaultVirtualMachine(VirtualMachineId id,
                                 SegmentationId segmentationId,TenantId tenantId, IpAddress ipAddress,
                                 String name, MacAddress mac, DeviceId deviceId) {
        this.id = id;
        this.segmentationId = segmentationId;
        this.tenantId = tenantId;
        this.ipAddress = ipAddress;
        this.name = name;
        this.macAddress = mac;
        this.deviceId = deviceId;
    }


    @Override
    public int hashCode() {
        return Objects.hash(id, ipAddress, name, macAddress, deviceId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof DefaultVirtualMachine) {
            final DefaultVirtualMachine that = (DefaultVirtualMachine) obj;
            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.segmentationId, that.segmentationId)
                    && Objects.equals(this.tenantId, that.tenantId)
                    && Objects.equals(this.ipAddress, that.ipAddress)
                    && Objects.equals(this.name, that.name)
                    && Objects.equals(this.macAddress, that.macAddress)
                    && Objects.equals(this.deviceId, that.deviceId);
        }
        return false;
    }

    @Override
    public String toString() {
        return toStringHelper(this).add("id", id)
                .add("segmentationId", segmentationId)
                .add("tenent_id", tenantId)
                .add("ipAddress", ipAddress)
                .add("name", name)
                .add("macAddress", macAddress)
                .add("deviceId", deviceId).toString();
    }

    @Override
    public VirtualMachineId id() {
        return id;
    }

    @Override
    public SegmentationId segmentationId() {
        return segmentationId;
    }

    @Override
    public TenantId tenantId(){
        return tenantId;
    }
    @Override
    public IpAddress ipAddress() {
        return ipAddress;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public DeviceId deviceId() {
        return deviceId;
    }

    @Override
    public MacAddress macAddress() {
        return macAddress;
    }
}

