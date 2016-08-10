package org.iris4sdn.csdncm.vnetmanager.impl;

import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.DefaultGroupId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.ExtensionTreatmentResolver;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.DriverService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.instructions.ExtensionTreatment;
import org.onosproject.net.flow.instructions.ExtensionTreatmentType;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.vtnrsc.SegmentationId;
import org.slf4j.Logger;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.slf4j.LoggerFactory.getLogger;

public class L2RuleInstaller {
    private final Logger log = getLogger(getClass());
    private static final String CONTROLLER_IP_KEY = "ipaddress";
    private final FlowObjectiveService flowObjectiveService;
    private final DriverService driverService;
    private final ApplicationId appId;

    private static final int PREFIX_LENGTH = 32;

    private static final short ARP_RESPONSE = 0x2;
    private static final int NORMAL_FLOW_PRIORITY = 50000;
    private static final int DROP_FLOW_PRIORITY = 45000;
    private static final int L2_CLASSIFIER_PRIORITY = 50000;
    private static final int DEFAULT_PRIORITY = 50000;
    private static final int ARP_DEFAULT_PRIORITY = 55000;

    private L2RuleInstaller(ApplicationId appId) {
        this.appId = checkNotNull(appId, "ApplicationId can not be null");
        ServiceDirectory serviceDirectory = new DefaultServiceDirectory();
        this.flowObjectiveService = serviceDirectory.get(FlowObjectiveService.class);
        this.driverService = serviceDirectory.get(DriverService.class);
    }

    public static L2RuleInstaller ruleInstaller(ApplicationId appId) {
        return new L2RuleInstaller(appId);
    }

    public void programArpRequest(DeviceId deviceId,
                                IpAddress dstIP, MacAddress dstMac,
                                       Objective.Operation type) {
        log.info("Install ARP request rules");

        DriverHandler handler = driverService.createHandler(deviceId);

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.ARP.ethType().toShort())
                .matchArpOp(1)
                .matchArpTpa(Ip4Address.valueOf(dstIP.toString()));

        ExtensionTreatmentResolver resolver = handler
                .behaviour(ExtensionTreatmentResolver.class);
        ExtensionTreatment ethSrcToDst = resolver
                .getExtensionInstruction(ExtensionTreatmentType.ExtensionTreatmentTypes
                        .NICIRA_MOV_ETH_SRC_TO_DST.type());
        ExtensionTreatment arpShaToTha = resolver
                .getExtensionInstruction(ExtensionTreatmentType.ExtensionTreatmentTypes
                        .NICIRA_MOV_ARP_SHA_TO_THA.type());
        ExtensionTreatment arpSpaToTpa = resolver
                .getExtensionInstruction(ExtensionTreatmentType.ExtensionTreatmentTypes
                        .NICIRA_MOV_ARP_SPA_TO_TPA.type());
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .extension(ethSrcToDst, deviceId)
                .setEthSrc(dstMac).setArpOp(ARP_RESPONSE)
                .extension(arpShaToTha, deviceId)
                .extension(arpSpaToTpa, deviceId)
                .setArpSha(dstMac).setArpSpa(dstIP)
                .setOutput(PortNumber.IN_PORT).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector.build())
                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(DEFAULT_PRIORITY);

        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programArpResponse(DeviceId deviceId,
                                      IpAddress dstIP, Objective.Operation type) {
        log.info("Install ARP response rules");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.ARP.ethType().toShort())
                .matchArpOp(2)
                .matchArpTpa(Ip4Address.valueOf(dstIP.toString())).build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(PortNumber.LOCAL).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(DEFAULT_PRIORITY);

        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programArpClassifier(DeviceId deviceId, IpAddress SrcIp,
                                     SegmentationId actionVni,
                                     Objective.Operation type) {
        log.info("Program ARP classifier rules");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP)
                .matchArpSpa(Ip4Address.valueOf(SrcIp.toString()))
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setTunnelId(Long.parseLong(actionVni.segmentationId()))
                .punt().build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).withFlag(ForwardingObjective.Flag.SPECIFIC)
                .withPriority(65004);

        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programGatewayArp (DeviceId deviceId,
             SegmentationId segmentationId, MacAddress srcMac,
             Objective.Operation type) {
        log.info("Program flow toward local VMs");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_ARP).matchArpSha(srcMac).add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .punt().build();
//                .setOutput(outPort).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).withFlag(ForwardingObjective.Flag.SPECIFIC)
                .withPriority(65003);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programNormalIn(DeviceId deviceId, PortNumber inPort,
                                IpAddress dstIp,
                                Objective.Operation type) {
        log.info("Program normal inward rules");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPDst(IpPrefix.valueOf(dstIp, PREFIX_LENGTH))
                .matchInPort(inPort).build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder().
                setOutput(PortNumber.LOCAL).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(NORMAL_FLOW_PRIORITY);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programNormalOut(DeviceId deviceId, PortNumber outPort,
                                Objective.Operation type) {
        log.info("Program normal outward rules");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(PortNumber.LOCAL).build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder().
                setOutput(outPort).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.VERSATILE)
                .withPriority(NORMAL_FLOW_PRIORITY);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programDrop(DeviceId deviceId,
                                PortNumber inPort,
                                Objective.Operation type) {
        log.info("Program drop action from port of {}", inPort == null ? "ALL" : inPort);

        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        if (inPort != null)
            selector.matchInPort(inPort);

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder().drop();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build())
                .withSelector(selector.build()).fromApp(appId).makePermanent()
                .withFlag(ForwardingObjective.Flag.VERSATILE).withPriority(DROP_FLOW_PRIORITY);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programLocalOut(DeviceId deviceId,
                                SegmentationId segmentationId,
                                PortNumber inPort, MacAddress srcMac,
                                Objective.Operation type) {
        log.info("Program flow from a local VM");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(inPort).matchEthSrc(srcMac).build();

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        treatment.add(Instructions
                .modTunnelId(Long.parseLong(segmentationId.toString())));

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build())
                .withSelector(selector).fromApp(appId).makePermanent()
                .withFlag(ForwardingObjective.Flag.SPECIFIC).withPriority(L2_CLASSIFIER_PRIORITY);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programLocalIn(DeviceId deviceId,
                                SegmentationId segmentationId,
                                Set<PortNumber> outPorts, MacAddress dstMac,
                                Objective.Operation type) {
        log.info("Program flow toward local VMs");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(dstMac).add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        for (PortNumber outPort : outPorts) {
                treatment.setOutput(outPort);
        }

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build()).withSelector(selector)
                .fromApp(appId).withFlag(ForwardingObjective.Flag.SPECIFIC)
//                .withPriority(L2_CLASSIFIER_PRIORITY);
        .withPriority(50000);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programLocalIn(DeviceId deviceId,
                                SegmentationId segmentationId,
                                PortNumber outPort, MacAddress dstMac,
                                Objective.Operation type) {
        log.info("Program flow toward local VMs");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(dstMac).add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).withFlag(ForwardingObjective.Flag.SPECIFIC)
//                .withPriority(L2_CLASSIFIER_PRIORITY);
        .withPriority(50000);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programLocalInWithGateway(DeviceId deviceId,
                                SegmentationId segmentationId, MacAddress dstMac,
                                Objective.Operation type) {
        log.info("Program flow toward local VMs");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(dstMac).add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
//                .setOutput(outPort);
        treatment.group(new DefaultGroupId(1));

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build()).withSelector(selector)
                .fromApp(appId).withFlag(ForwardingObjective.Flag.SPECIFIC)
//                .withPriority(L2_CLASSIFIER_PRIORITY);
        .withPriority(50000);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programLocalInGateway(DeviceId deviceId,

                                SegmentationId segmentationId,
                                PortNumber outPort, MacAddress dstMac,
                                Objective.Operation type) {
        log.info("Program flow toward local VMs");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(dstMac).add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(outPort).build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).withFlag(ForwardingObjective.Flag.SPECIFIC)
//                .withPriority(L2_CLASSIFIER_PRIORITY);
        .withPriority(50000);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

//    public void programTunnelIn(DeviceId deviceId, SegmentationId segmentationId,
//                                 PortNumber inPort, Objective.Operation type) {
//        log.info("Program flow toward local VMs from tunnel ports");
//
//        TrafficSelector selector = DefaultTrafficSelector.builder()
//                .matchInPort(inPort).add(Criteria.matchTunnelId(Long
//                        .parseLong(segmentationId.toString())))
//                .build();
//
//        TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();
//
//        ForwardingObjective.Builder objective = DefaultForwardingObjective
//                .builder().withTreatment(treatment).withSelector(selector)
//                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.SPECIFIC)
//                .withPriority(L2_CLASSIFIER_PRIORITY);
//        if (type.equals(Objective.Operation.ADD)) {
//            flowObjectiveService.forward(deviceId, objective.add());
//        } else {
//            flowObjectiveService.forward(deviceId, objective.remove());
//        }
//    }

    public void programTunnelIn(DeviceId deviceId, PortNumber inPort, Objective.Operation type) {
        log.info("Program flow toward local VMs from tunnel ports");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(inPort)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder().build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.SPECIFIC)
                .withPriority(49999);
//                .withPriority(L2_CLASSIFIER_PRIORITY);

        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programGatewayIn(DeviceId deviceId,
                                 PortNumber inPort, Objective.Operation type) {
        log.info("Program Gateway In Rule");

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(EthType.EtherType.ARP.ethType().toShort())
                .matchInPort(inPort)
                .build();

        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
//                .build();
                .punt().build();

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment).withSelector(selector)
                .fromApp(appId).makePermanent().withFlag(ForwardingObjective.Flag.SPECIFIC)
                .withPriority(50010);
//                .withPriority(L2_CLASSIFIER_PRIORITY);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }


    public void programBroadcast(DeviceId deviceId,
                                        SegmentationId segmentationId,
                                        Set<PortNumber> outPorts,
                                        Objective.Operation type) {
        log.info("Program for broadcast");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(MacAddress.BROADCAST)
                .add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        for (PortNumber outPort : outPorts) {
//            if (inPort != outPort) {
                treatment.setOutput(outPort);
//            }
        }


        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build())
                .withSelector(selector).fromApp(appId).makePermanent()
                //.withFlag(ForwardingObjective.Flag.SPECIFIC).withPriority(L2_CLASSIFIER_PRIORITY);
                .withFlag(ForwardingObjective.Flag.SPECIFIC).withPriority(49000);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }

    public void programBroadcastWithGroup(DeviceId deviceId,
                                        SegmentationId segmentationId,
                                        Set<PortNumber> outPorts,
                                        Objective.Operation type) {
        log.info("Program for broadcast");
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(MacAddress.BROADCAST)
                .add(Criteria.matchTunnelId(Long
                        .parseLong(segmentationId.toString())))
                .build();

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        for (PortNumber outPort : outPorts) {
//            if (inPort != outPort) {
                treatment.setOutput(outPort);
//            }
        }
        treatment.group(new DefaultGroupId(1));


        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build())
                .withSelector(selector).fromApp(appId).makePermanent()
                //.withFlag(ForwardingObjective.Flag.SPECIFIC).withPriority(L2_CLASSIFIER_PRIORITY);
                .withFlag(ForwardingObjective.Flag.SPECIFIC).withPriority(49000);
        if (type.equals(Objective.Operation.ADD)) {
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }





}
