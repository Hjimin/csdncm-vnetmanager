package org.iris4sdn.csdncm.vnetmanager;

import java.util.Objects;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

public final class OpenstackNodeId {
    private final String id;

    private OpenstackNodeId(String openstackNodeId) {
        checkNotNull(openstackNodeId, "openstackNodeId cannot be null");
        this.id = openstackNodeId;
    }

    public static OpenstackNodeId valueOf(String openstackNodeId) {
        return new OpenstackNodeId(openstackNodeId);
    }

    public String openstackNodeId() {
        return id;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof OpenstackNodeId) {
            final OpenstackNodeId that = (OpenstackNodeId) obj;
            return Objects.equals(this.id, that.id);
        }
        return false;
    }

    @Override
    public String toString() {
        return toStringHelper(this).add("openstackNodeId", id).toString();
    }
}
