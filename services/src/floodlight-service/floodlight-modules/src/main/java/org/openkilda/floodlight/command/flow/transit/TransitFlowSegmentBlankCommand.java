/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.command.flow.transit;

import org.openkilda.floodlight.api.FlowTransitEncapsulation;
import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.command.flow.AbstractNotIngressFlowSegmentCommand;
import org.openkilda.floodlight.command.flow.FlowSegmentReport;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.Cookie;
import org.openkilda.model.SwitchId;

import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

abstract class TransitFlowSegmentBlankCommand extends AbstractNotIngressFlowSegmentCommand {
    protected final Integer egressIslPort;

    public TransitFlowSegmentBlankCommand(MessageContext messageContext, SwitchId switchId, UUID commandId,
                                          String flowId, Cookie cookie, Integer ingressIslPort,
                                          FlowTransitEncapsulation encapsulation, Integer egressIslPort) {
        super(messageContext, switchId, commandId, flowId, cookie, ingressIslPort, encapsulation);
        this.egressIslPort = egressIslPort;
    }

    @Override
    protected CompletableFuture<FlowSegmentReport> makeExecutePlan(
            SpeakerCommandProcessor commandProcessor) {
        try (Session session = getSessionService().open(messageContext, getSw())) {
            return session.write(makeTransitModMessage())
                    .thenApply(ignore -> makeSuccessReport());
        }
    }

    private OFFlowMod makeTransitModMessage() {
        OFFactory of = getSw().getOFFactory();
        List<OFAction> applyActions = ImmutableList.of(
                of.actions().buildOutput()
                        .setPort(OFPort.of(egressIslPort))
                        .build());

        return makeFlowModBuilder(of)
                .setInstructions(ImmutableList.of(of.instructions().applyActions(applyActions)))
                .setMatch(makeTransitMatch(of))
                .build();
    }
}
