package org.iris4sdn.csdncm.vnetmanager.instance;

import org.onosproject.event.ListenerService;
import org.onosproject.net.HostId;

/**
 * Created by gurum on 16. 9. 27.
 */
public interface InstanceManagerService extends ListenerService<InstanceEvent, InstanceListener> {

    void addInstance(HostId hostId, String ifaceId);
    void deleteInstance(HostId hostId);
    Iterable<HostId> getHostIds();
    String getHostIfaceId(HostId hostId);
}
