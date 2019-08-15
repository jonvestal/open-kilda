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

package org.openkilda.floodlight.command;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;

import org.openkilda.floodlight.command.meter.MeterReport;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.model.SpeakerSwitchView.Feature;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import lombok.AllArgsConstructor;
import lombok.Value;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitchManager;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.EasyMockSupport;
import org.easymock.IAnswer;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.ver13.OFFactoryVer13;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AbstractSpeakerCommandTest extends EasyMockSupport {
    protected final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    protected final OFFactory of = new OFFactoryVer13();
    protected final DatapathId dpId = DatapathId.of(1);
    protected final Map<DatapathId, Iterator<Session>> switchSessionProducePlan = new HashMap<>();

    @Mock
    protected SessionService sessionService;

    @Mock
    protected OFSwitchManager ofSwitchManager;

    @Mock
    protected FeatureDetectorService featureDetectorService;

    @Mock
    protected SpeakerCommandProcessor commandProcessor;

    @Mock
    protected Session session;

    @Mock
    protected IOFSwitch sw;

    protected final List<SessionWriteRecord> writeHistory = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        moduleContext.addService(SessionService.class, sessionService);
        moduleContext.addService(OFSwitchManager.class, ofSwitchManager);

        expect(commandProcessor.getModuleContext()).andReturn(moduleContext).anyTimes();

        expect(sw.getId()).andReturn(dpId).anyTimes();
        expect(sw.getOFFactory()).andReturn(of).anyTimes();

        expect(ofSwitchManager.getActiveSwitch(dpId)).andReturn(sw).anyTimes();
        expect(sessionService.open(anyObject(MessageContext.class), anyObject(IOFSwitch.class)))
                .andAnswer(new IAnswer<Session>() {
                    @Override
                    public Session answer() throws Throwable {
                        IOFSwitch target = (IOFSwitch) getCurrentArguments()[1];
                        Iterator<Session> producePlan = switchSessionProducePlan.get(target.getId());
                        return producePlan.next();
                    }
                });

        moduleContext.addService(FeatureDetectorService.class, featureDetectorService);
        switchSessionProducePlan.put(dpId, ImmutableList.of(session).iterator());
        expect(session.write(anyObject(OFMessage.class)))
                .andAnswer(new IAnswer<CompletableFuture<Optional<OFMessage>>>() {
                    @Override
                    public CompletableFuture<Optional<OFMessage>> answer() throws Throwable {
                        SessionWriteRecord historyEntry = new SessionWriteRecord(
                                (OFMessage) (getCurrentArguments()[0]));
                        writeHistory.add(historyEntry);
                        return historyEntry.getFuture();
                    }
                })
                .anyTimes();

        session.close();
        expectLastCall();
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    protected void verifyOfMessageEquals(OFMessage expect, OFMessage actual) {
        if (! expect.equalsIgnoreXid(actual)) {
            Assert.assertEquals(expect, actual);
        }
    }

    protected void verifySuccessCompletion(CompletableFuture<? extends SpeakerCommandReport> future) throws Exception {
        completeAllSessionWriteRequests();
        Assert.assertTrue(future.isDone());
        future.get(1, TimeUnit.SECONDS).raiseError();
    }

    protected void verifyErrorCompletion(CompletableFuture<? extends SpeakerCommandReport> result, Class<? extends Throwable> errorType) {
        completeAllSessionWriteRequests();
        try {
            result.get().raiseError();
            Assert.fail("must never reach this line");
        } catch (Exception e) {
            Assert.assertTrue(errorType.isAssignableFrom(e.getClass()));
        }
    }

    protected void verifyWriteCount(int count) {
        Assert.assertEquals(count, writeHistory.size());
    }

    protected void switchFeaturesSetup(boolean metersSupport) {
        Set<Feature> features = new HashSet<>();

        if (metersSupport) {
            features.add(Feature.METERS);
        }

        expect(featureDetectorService.detectSwitch(sw))
                .andReturn(ImmutableSet.copyOf(features))
                .anyTimes();
    }

    protected void completeAllSessionWriteRequests() {
        for (SessionWriteRecord record : writeHistory) {
            CompletableFuture<Optional<OFMessage>> future = record.getFuture();
            if (future.isDone()) {
                continue;
            }
            future.complete(Optional.empty());
        }
    }

    protected SessionWriteRecord getWriteRecord(int idx) {
        Assert.assertTrue(idx < writeHistory.size());
        return writeHistory.get(idx);
    }

    @Value
    @AllArgsConstructor
    protected static class SessionWriteRecord {
        private final OFMessage request;
        private final CompletableFuture<Optional<OFMessage>> future;

        public SessionWriteRecord(OFMessage request) {
            this(request, new CompletableFuture<Optional<OFMessage>>());
        }
    }
}
