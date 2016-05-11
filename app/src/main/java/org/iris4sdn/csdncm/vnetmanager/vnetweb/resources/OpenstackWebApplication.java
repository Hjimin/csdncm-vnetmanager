package org.iris4sdn.csdncm.vnetmanager.vnetweb.resources;

import org.onlab.rest.AbstractWebApplication;

import java.util.Set;


public class OpenstackWebApplication extends AbstractWebApplication {
    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(OpenstackConfig.class,
                VirtualMachineWebResource.class,
                GatewayConfig.class);
    }
}
