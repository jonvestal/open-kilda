/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.ctrl;

import org.openkilda.messaging.ctrl.AbstractDumpState;
import org.openkilda.messaging.ctrl.DumpStateBySwitchRequestData;
import org.openkilda.messaging.ctrl.DumpStateResponseData;
import org.openkilda.wfm.error.MessageFormatException;
import org.openkilda.wfm.error.UnsupportedActionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.storm.task.TopologyContext;

public class DumpBySwitchStateAction extends CtrlEmbeddedAction {

    private final DumpStateBySwitchRequestData payload;

    public DumpBySwitchStateAction(CtrlAction master, RouteMessage message) {
        super(master, message);
        payload = (DumpStateBySwitchRequestData) message.getPayload();
    }

    @Override
    protected void handle()
            throws MessageFormatException, UnsupportedActionException, JsonProcessingException {
        AbstractDumpState state = getMaster().getBolt().dumpStateBySwitchId(payload.getSwitchId());
        TopologyContext context = getBolt().getContext();
        emitResponse(new DumpStateResponseData(context.getThisComponentId(),
                context.getThisTaskId(), getMessage().getTopology(), state));
    }
}
