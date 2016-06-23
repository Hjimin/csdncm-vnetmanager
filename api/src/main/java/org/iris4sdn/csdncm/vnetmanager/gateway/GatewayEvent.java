package org.iris4sdn.csdncm.vnetmanager.gateway;

import org.onosproject.event.AbstractEvent;

public class GatewayEvent extends AbstractEvent<GatewayEvent.Type, Gateway> {
    public enum Type {
        GATEWAY_PUT,
        GATEWAY_REMOVE,
    }

    public GatewayEvent(Type type, Gateway gateway) {
        super(type, gateway);
    }

    public GatewayEvent(Type type, Gateway gateway, long time) {
        super(type, gateway, time);
    }
}
