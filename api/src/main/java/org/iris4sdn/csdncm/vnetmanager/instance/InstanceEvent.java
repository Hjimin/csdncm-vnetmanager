package org.iris4sdn.csdncm.vnetmanager.instance;

import org.onosproject.event.AbstractEvent;
import org.onosproject.net.HostId;

public class InstanceEvent extends AbstractEvent<InstanceEvent.Type, HostId> {
    public enum Type {
        INSTANCE_PUT,
        INSTANCE_REMOVE
    }

    public InstanceEvent(Type type, HostId id) {
        super(type, id);
    }

}
