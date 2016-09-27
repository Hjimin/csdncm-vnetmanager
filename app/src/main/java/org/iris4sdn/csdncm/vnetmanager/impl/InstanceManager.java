package org.iris4sdn.csdncm.vnetmanager.impl;

import org.apache.felix.scr.annotations.*;
import org.iris4sdn.csdncm.vnetmanager.instance.InstanceEvent;
import org.iris4sdn.csdncm.vnetmanager.instance.InstanceListener;
import org.iris4sdn.csdncm.vnetmanager.instance.InstanceManagerService;
import org.onlab.util.KryoNamespace;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.net.HostId;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.*;
import org.slf4j.Logger;

import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;
/**
 * Created by gurum on 16. 9. 27.
 */
@Component(immediate = true)
@Service
public class InstanceManager extends AbstractListenerManager<InstanceEvent, InstanceListener>
        implements InstanceManagerService {
    private final Logger log = getLogger(getClass());

    private static final String OPENSTACK_NODE_NOT_NULL = "Openstack node cannot be null";
    private static final String EVENT_NOT_NULL = "VirtualMachine event cannot be null";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LogicalClockService clockService;

    private EventuallyConsistentMap<HostId, String> instanceStore;

    private EventuallyConsistentMapListener<HostId, String> instanceListener =
            new InnerInstanceListener();
//    private GatewayListener gatewayListener = new InnerGatewayListener();

    @Activate
    public void activate() {
        eventDispatcher.addSink(InstanceEvent.class,listenerRegistry);
        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API);

        instanceStore = storageService
                .<HostId, String>eventuallyConsistentMapBuilder()
                .withName("instance").withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        instanceStore.addListener(instanceListener);

        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }


    @Override
    public Iterable<HostId> getHostIds() {
        return Collections.unmodifiableCollection(instanceStore.keySet());
    }

    @Override
    public String getHostIfaceId(HostId hostId) {
        return instanceStore.get(hostId);
    }


    @Override
    public void addInstance(HostId hostId, String ifaceId) {
        checkNotNull(hostId, OPENSTACK_NODE_NOT_NULL);
        if(instanceStore.containsKey(hostId)) {
            log.info("Remove pre-configured instance {} ", hostId);
            instanceStore.remove(hostId);
        }
        log.info("Add configured instance {}", hostId);
        instanceStore.put(hostId, ifaceId);
    }

    @Override
    public void deleteInstance(HostId hostId) {
        checkNotNull(hostId, OPENSTACK_NODE_NOT_NULL);
        instanceStore.remove(hostId);
    }


     private class InnerInstanceListener
            implements
            EventuallyConsistentMapListener<HostId, String> {

        @Override
        public void event(EventuallyConsistentMapEvent<HostId, String> event) {
            checkNotNull(event, EVENT_NOT_NULL);
            HostId hostId = event.key();
            if (EventuallyConsistentMapEvent.Type.PUT == event.type()) {
                notifyListeners(new InstanceEvent(
                        InstanceEvent.Type.INSTANCE_PUT, hostId));
            }
            if (EventuallyConsistentMapEvent.Type.REMOVE == event.type()) {
                notifyListeners(new InstanceEvent(
                        InstanceEvent.Type.INSTANCE_REMOVE, hostId));
            }
        }
    }

    private void notifyListeners(InstanceEvent event) {
        checkNotNull(event, EVENT_NOT_NULL);
        post(event);
    }


}
