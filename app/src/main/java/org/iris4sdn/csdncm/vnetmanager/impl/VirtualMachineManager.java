package org.iris4sdn.csdncm.vnetmanager.impl;

import org.iris4sdn.csdncm.vnetmanager.virtualmachine.*;
import org.onlab.packet.Ip4Address;
import org.onlab.util.KryoNamespace;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.*;
import org.apache.felix.scr.annotations.*;
import org.slf4j.Logger;

import java.util.Collections;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;


@Component(immediate = true)
@Service
public class VirtualMachineManager extends AbstractListenerManager<VirtualMachineEvent, VirtualMachineListener> implements VirtualMachineService {
    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LogicalClockService clockService;

    private EventuallyConsistentMap<VirtualMachineId, VirtualMachine> vmStore;
    private EventuallyConsistentMapListener<VirtualMachineId, VirtualMachine> vmListener =
            new InnerVirtualMachineStoreListener();

    private static final String VIRTUAL_MACHINES = "virtual-machines";
    private static final String EVENT_NOT_NULL = "event cannot be null";
    private static final String VM_NOT_NULL = "VirutalMachine cannot be null";
    @Activate
    public void activate() {

        eventDispatcher.addSink(VirtualMachineEvent.class,listenerRegistry);
        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(MultiValuedTimestamp.class);

        vmStore = storageService
                .<VirtualMachineId, VirtualMachine>eventuallyConsistentMapBuilder()
                .withName(VIRTUAL_MACHINES).withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        vmStore.addListener(vmListener);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        eventDispatcher.removeSink(VirtualMachineEvent.class);
        vmStore.destroy();
        log.info("Stopped");
    }

    @Override
    public boolean addVirtualMachine(Iterable<VirtualMachine> vms) {
        checkNotNull(vms, VM_NOT_NULL);
        for (VirtualMachine vm : vms) {
            vmStore.put(vm.id(), vm);
            if (!vmStore.containsKey(vm.id())) {
                log.debug("The VirtualMachine is created failed whose identifier is {} ",
                        vm.id());
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean deleteVirtualMachine(Iterable<VirtualMachineId> vmIds) {
        checkNotNull(vmIds, VM_NOT_NULL);
        for (VirtualMachineId vmId : vmIds) {
            vmStore.remove(vmId);
            if (vmStore.containsKey(vmId)) {
                log.debug("The VirtualMachine is removed failed whose identifier is {}",
                        vmId);
                return false;
            }
        }
        return true;
    }
    @Override
    public Iterable<VirtualMachine> getVirtualMachines(){
        return Collections.unmodifiableCollection(this.vmStore.values());
    }


    @Override
    public VirtualMachine getVirtualMachineByIp(Ip4Address vmIp){
       return vmStore.values().stream()
               .filter(vm -> vm.ipAddress().equals(vmIp.toString()))
               .findFirst().orElse(null);
    }

    private class InnerVirtualMachineStoreListener
            implements
            EventuallyConsistentMapListener<VirtualMachineId, VirtualMachine> {

        @Override
        public void event(EventuallyConsistentMapEvent<VirtualMachineId, VirtualMachine> event) {
            checkNotNull(event, EVENT_NOT_NULL);
            VirtualMachine vm = event.value();
            if (EventuallyConsistentMapEvent.Type.PUT == event.type()) {
                notifyListeners(new VirtualMachineEvent(
                        VirtualMachineEvent.Type.VIRTUAL_MACHINE_PUT,vm));
            }
            if (EventuallyConsistentMapEvent.Type.REMOVE == event.type()) {
                notifyListeners(new VirtualMachineEvent(
                        VirtualMachineEvent.Type.VIRTUAL_MACHINE_REMOVE,vm));
            }
        }
    }


    private void notifyListeners(VirtualMachineEvent event) {
        checkNotNull(event, EVENT_NOT_NULL);
        post(event);
    }

}
