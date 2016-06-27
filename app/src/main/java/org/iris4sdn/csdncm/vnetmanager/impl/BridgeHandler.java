/*
 * Copyright 2015 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.iris4sdn.csdncm.vnetmanager.impl;


import org.iris4sdn.csdncm.tunnelmanager.TunnelManagerService;
import org.iris4sdn.csdncm.vnetmanager.gateway.Gateway;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.IpAddress;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.BridgeConfig;
import org.onosproject.net.behaviour.BridgeDescription;
import org.onosproject.net.behaviour.BridgeName;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.basics.BasicDeviceConfig;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.DriverService;
import org.onosproject.ovsdb.controller.OvsdbClientService;
import org.onosproject.ovsdb.controller.OvsdbController;
import org.onosproject.ovsdb.controller.OvsdbNodeId;
import org.iris4sdn.csdncm.vnetmanager.Bridge;
import org.iris4sdn.csdncm.vnetmanager.OpenstackNode;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.List;

import static org.iris4sdn.csdncm.vnetmanager.OpenstackNode.State.GATEWAY_CREATED;
import static org.iris4sdn.csdncm.vnetmanager.OpenstackNode.State.TUNNEL_CREATED;
import static org.slf4j.LoggerFactory.getLogger;

public final class BridgeHandler {
    private final Logger log = getLogger(getClass());

    private final DeviceService deviceService;
    private final DriverService driverService;
    private final NetworkConfigService configService;
    private final TunnelManagerService tunnelManagerService;
    private final OvsdbController controller;

    private static final String DRIVER_NAME = "csdncm";
    private static final String INT_BRIDGE_NAME = "br-int";
    private static final String EX_BRIDGE_NAME = "br-ex";
    private static final String PATCH_PORT_EX_NAME = "patch-br-ex";
    private static final String PATCH_PORT_INT_NAME = "patch-br-int";
    private static final String EX_PORT_NAME= "eth0";
    private static final int OVSDB_PORT = 6640;

    private static final BridgeHandler handler = new BridgeHandler();

//    public enum BridgeType {
//        INTEGRATION,
//        EXTERNAL
//    }

    private BridgeHandler() {
        ServiceDirectory serviceDirectory = new DefaultServiceDirectory();
        this.deviceService = serviceDirectory.get(DeviceService.class);
        this.driverService = serviceDirectory.get(DriverService.class);
        this.configService = serviceDirectory.get(NetworkConfigService.class);
        this.tunnelManagerService = serviceDirectory.get(TunnelManagerService.class);
        this.controller = serviceDirectory.get(OvsdbController.class);
    }

    public static BridgeHandler bridgeHandler() {
        return handler;
    }

    private void applyBridgeConfig(DeviceId deviceId, String bridgeName, String dpid, String exPortName) {
        DriverHandler handler = driverService.createHandler(deviceId);
        BridgeConfig bridgeConfig = handler.behaviour(BridgeConfig.class);
        bridgeConfig.addBridge(BridgeName.bridgeName(bridgeName), dpid, exPortName);
    }

    private DeviceId getBridgeId(DeviceId deviceId, String name) {
        DriverHandler handler = driverService.createHandler(deviceId);
        BridgeConfig bridgeConfig = handler.behaviour(BridgeConfig.class);

        Collection<BridgeDescription> descriptions = bridgeConfig.getBridges();

        if (descriptions == null)
            return null;

        BridgeDescription description = descriptions.stream()
                .filter(e -> e.bridgeName().equals(BridgeName.bridgeName(name)))
                .findAny()
                .orElse(null);

        if (description != null) {
            return description.deviceId();
        } else {
            return null;
        }
    }

    // FIXME: need to modifiy port name dependency.
    private PortNumber getPortNumber(DeviceId deviceId, String subString) {
        DriverHandler handler = driverService.createHandler(deviceId);
        BridgeConfig bridgeConfig = handler.behaviour(BridgeConfig.class);
        List<PortNumber> ports = bridgeConfig.getPortNumbers();

        return ports.stream().filter(e -> e.name().contains(subString))
                .findFirst().orElse(null);
    }

//    public void updateBridge(OpenstackNode node, Bridge.BridgeType type) {
//        String bridgeName = null;
//        if (type == Bridge.BridgeType.INTEGRATION) {
//            bridgeName = INT_BRIDGE_NAME;
//        } else if (type == Bridge.BridgeType.EXTERNAL) {
//            bridgeName = EX_BRIDGE_NAME;
//        }
//
//        DeviceId deviceId = node.getControllerId();
//        log.info("node id {}", deviceId);
//        DeviceId bridgeDeviceId = getBridgeId(deviceId, bridgeName);
//        log.info("brdgeDevice id {}", bridgeDeviceId);
//
//        if(type == Bridge.BridgeType.INTEGRATION) {
//            BasicDeviceConfig basicDeviceConfig = configService.getConfig(bridgeDeviceId, BasicDeviceConfig.class);
//            log.info("basicDeviceConfig {}", basicDeviceConfig);
//            basicDeviceConfig.managementAddress(node.getManageNetworkIp().toString());
//            configService.applyConfig(bridgeDeviceId, BasicDeviceConfig.class, basicDeviceConfig.node());
//        }
//
//    }

    public void createBridge(OpenstackNode node, Bridge.BridgeType type) {
        String bridgeName = null;
        if (type == Bridge.BridgeType.INTEGRATION) {
            bridgeName = INT_BRIDGE_NAME;
        } else if (type == Bridge.BridgeType.EXTERNAL) {
            bridgeName = EX_BRIDGE_NAME;
        }

        DeviceId ovsdbId = node.getOvsdbId();
        DeviceId bridgeDeviceId = getBridgeId(ovsdbId, bridgeName);


        DataPathIdGenerator dpidGenerator = DataPathIdGenerator.builder()
                .addIpAddress(node.getManageNetworkIp().toString()).build();

        // Create id for new bridge.
        if (bridgeDeviceId == null) {
            bridgeDeviceId = dpidGenerator.getDeviceId();
        }

        if (type == Bridge.BridgeType.INTEGRATION) {
            BasicDeviceConfig config = configService.addConfig(bridgeDeviceId, BasicDeviceConfig.class);
            config.driver(DRIVER_NAME);
            configService.applyConfig(bridgeDeviceId, BasicDeviceConfig.class, config.node());
        }

        String dpid = dpidGenerator.getDpId();
        applyBridgeConfig(ovsdbId, bridgeName, dpid, null);
        node.setBridgeId(bridgeDeviceId, type);

        log.info("A new bridge {} is created in node {}", bridgeDeviceId, node.id());
    }

    public void createGatewayTunnel(OpenstackNode node, OpenstackNode gateway) {
        if (node.getState().contains(GATEWAY_CREATED)) {
            log.info("Gateway already created at {}", node.id());
            return ;
        }

        DeviceId deviceId = node.getOvsdbId();
        IpAddress srcIpAddress = node.getDataNetworkIp();
        IpAddress dstIpAddress = gateway.getDataNetworkIp();

        tunnelManagerService.createTunnel(deviceId, srcIpAddress, dstIpAddress);

        PortNumber port = null;

        for (int i = 0; i < 10; i++) {
            port = getPortNumber(deviceId, dstIpAddress.toString());
            if (port == null) {
                try {
                    // Need to wait for synchronising
                    Thread.sleep(500);
                } catch (InterruptedException exeption) {
                    log.warn("Interrupted while waiting to get bridge");
                    Thread.currentThread().interrupt();
                }
            } else  {
                break;
            }
        }

        if (port == null) {
            log.error("Tunnel create failed at {}", node.id());
            return ;
        }

        // Save tunnel port which mapped to Openstack node otherside.
        node.addTunnelPortNumber(gateway.id(), port);
        node.setGatewayTunnelPortNumber(port);
        node.applyState(GATEWAY_CREATED);

        log.info("Tunnel from " + node.getDataNetworkIp() + " to "
                + gateway.getDataNetworkIp() + " created" );
    }

    public void createGatewayTunnel(OpenstackNode node, Gateway gateway) {
//        if (node.getState().contains(GATEWAY_CREATED)) {
//            log.info("Gateway already created at {}", node.id());
//            return ;
//        }

        DeviceId deviceId = node.getOvsdbId();
        IpAddress srcIpAddress = node.getDataNetworkIp();
        IpAddress dstIpAddress = gateway.getDataNetworkIp();

        tunnelManagerService.createTunnel(deviceId, srcIpAddress, dstIpAddress);

        PortNumber port = null;

        for (int i = 0; i < 10; i++) {
            port = getPortNumber(deviceId, dstIpAddress.toString());
            if (port == null) {
                try {
                    // Need to wait for synchronising
                    Thread.sleep(500);
                } catch (InterruptedException exeption) {
                    log.warn("Interrupted while waiting to get bridge");
                    Thread.currentThread().interrupt();
                }
            } else  {
                break;
            }
        }

        if (port == null) {
            log.error("Gateway Tunnel create failed at {}", node.id());
            return ;
        }

        // Save tunnel port which mapped to Openstack node otherside.
        //node.addTunnelPortNumber(gateway.id(), port);
        gateway.setGatewayPortNumber(port);
        gateway.setBridgeId(deviceId, Bridge.BridgeType.INTEGRATION);
//        node.setGatewayTunnelPortNumber(port);
        node.applyState(GATEWAY_CREATED);

        log.info("Tunnel from " + node.getDataNetworkIp() + " to "
                + gateway.getDataNetworkIp() + " created" );
    }

    public void createTunnel(OpenstackNode srcNode, OpenstackNode dstNode) {
        DeviceId srcDeviceId = srcNode.getOvsdbId();
        DeviceId dstDeviceId = dstNode.getOvsdbId();

        IpAddress srcIpAddress = srcNode.getDataNetworkIp();
        IpAddress dstIpAddress = dstNode.getDataNetworkIp();

        tunnelManagerService.createTunnel(srcDeviceId, srcIpAddress, dstIpAddress);
        tunnelManagerService.createTunnel(dstDeviceId, dstIpAddress, srcIpAddress);

        // WARN: port name format is already determined to "vxlan-[IP address of other side]"
        // ex. "vxlan-192.168.10.1" on 192.168.10.2
        PortNumber srcPort = null;
        PortNumber dstPort = null;
        for (int i = 0; i < 10; i++) {
            srcPort = getPortNumber(srcDeviceId, dstIpAddress.toString());
            dstPort = getPortNumber(dstDeviceId, srcIpAddress.toString());
            if (srcPort == null || dstPort == null) {
                try {
                    // Need to wait for synchronising
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    log.warn("Interrupted while waiting to get bridge");
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }

        if (srcPort == null || dstPort == null) {
            log.error("Tunnel create failed at {}", srcNode.id());
            return;
        }

        // Save tunnel port which mapped to Openstack node otherside.
        srcNode.addTunnelPortNumber(dstNode.id(), srcPort);
        dstNode.addTunnelPortNumber(srcNode.id(), dstPort);

        dstNode.applyState(TUNNEL_CREATED);
        srcNode.applyState(TUNNEL_CREATED);

        log.info("Tunnel from " + srcNode.getDataNetworkIp() + " to "
                + dstNode.getDataNetworkIp() + " created");
    }

    public void destroyTunnel(OpenstackNode srcNode, OpenstackNode dstNode) {
        tunnelManagerService.removeTunnel(dstNode.getOvsdbId(),
                dstNode.getDataNetworkIp(), srcNode.getDataNetworkIp());

        // Remove tunnel port which mapped to Openstack node otherside.
        srcNode.removeTunnelPortNumber(dstNode.id());
        dstNode.removeTunnelPortNumber(srcNode.id());

        log.info("Tunnel from " + srcNode.getDataNetworkIp() + " to "
                + dstNode.getDataNetworkIp() + " destroyed" );
    }

    public boolean createExPort(OpenstackNode node) {
        DeviceId deviceId = node.getOvsdbId();
        applyBridgeConfig(deviceId, EX_BRIDGE_NAME, null, EX_PORT_NAME);

        return setExPort(node);
    }

    public boolean createPatchPort(OpenstackNode node, Bridge.BridgeType type) {
        OvsdbClientService ovsdbClient = controller.getOvsdbClient(
                new OvsdbNodeId(node.getManageNetworkIp(), OVSDB_PORT));

        if (ovsdbClient == null) {
            log.info("Couldn't find OVSDB client for {}", node.name());
            return false;
        }

        String bridgeName = null;
        String portName = null;
        String peerName = null;

        if (type == Bridge.BridgeType.EXTERNAL) {
            bridgeName = EX_BRIDGE_NAME;
            portName = PATCH_PORT_EX_NAME;
            peerName = PATCH_PORT_INT_NAME;

        } else if (type == Bridge.BridgeType.INTEGRATION) {
            bridgeName = INT_BRIDGE_NAME;
            portName = PATCH_PORT_INT_NAME;
            peerName = PATCH_PORT_EX_NAME;
        }

        tunnelManagerService.createPatchPort(ovsdbClient, bridgeName, portName, peerName);

        DeviceId deviceId = node.getOvsdbId();
        PortNumber portNumber = null;
        for (int i = 0; i < 10; i++) {
            portNumber = getPortNumber(deviceId, portName);
            if (portNumber == null) {
                try {
                    // Need to wait for synchronising
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    log.warn("Interrupted while waiting to get bridge");
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }

        if (portNumber == null) {
            log.error("Patch port create failed at {}", node.name());
            return false;
        }

        return setPatchPort(node, type);
    }

    public boolean setExPort(OpenstackNode node) {
        DeviceId deviceId = node.getBridgeId(Bridge.BridgeType.EXTERNAL);

        Port exPort = null;
        for (int i = 0; i < 10; i++) {
            exPort = getPort(deviceId, EX_PORT_NAME);
            if (exPort == null) {
                try {
                    // Need to wait for synchronising
                    Thread.sleep(500);
                } catch (InterruptedException exception) {
                    log.warn("Interrupted while waiting to get bridge");
                    Thread.currentThread().interrupt();
                }
            } else {
                break;
            }
        }

        if (exPort == null) {
            log.info("Could not find external port {} at {}", exPort, node.id());
            return false;
        }

        node.setExPort(exPort);
        log.info("A new exteranl port {} is created in bridge of {}", EX_PORT_NAME, deviceId);

        return true;
    }

    public boolean setPatchPort(OpenstackNode node, Bridge.BridgeType type) {
        DeviceId deviceId = node.getBridgeId(type);

        String portName = null;
        if (type.equals(Bridge.BridgeType.EXTERNAL))
            portName = PATCH_PORT_EX_NAME;
        else if (type.equals(Bridge.BridgeType.INTEGRATION))
            portName = PATCH_PORT_INT_NAME;

        Port port = getPort(deviceId, portName);
        if (port == null) {
            log.info("Could not find patch port {} at {}", portName, node.id());
            return false;
        }

        node.setPatchPort(port, type);
        log.info("A new patch port {} is created in bridge of {}", portName, deviceId);

        return true;
    }

    private Port getPort(DeviceId deviceId, String name) {
        List<Port> ports = deviceService.getPorts(deviceId);
        Port exPort = null;
        for (Port port : ports) {
            String portName = port.annotations().value(AnnotationKeys.PORT_NAME);

            if (portName != null && portName.equals(name)) {
                exPort = port;
                break;
            }
        }

        return exPort;
    }

    public boolean setBridgeOutbandControl(DeviceId deviceId, Bridge.BridgeType type) {
        String bridgeName = null;
        if (type.equals(Bridge.BridgeType.EXTERNAL))
            bridgeName = EX_BRIDGE_NAME;
        else if (type.equals(Bridge.BridgeType.INTEGRATION))
            bridgeName = INT_BRIDGE_NAME;

        log.info("Set bridge of {} to out-band control", bridgeName);
        DriverHandler handler = driverService.createHandler(deviceId);
        BridgeConfig bridgeConfig = handler.behaviour(BridgeConfig.class);

        return bridgeConfig.setBridgeOutbandControl(BridgeName.bridgeName(bridgeName));
    }
}


