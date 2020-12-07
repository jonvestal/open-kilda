/* Copyright 2020 Telstra Open Source
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


package org.openkilda.bluegreen;

import com.google.common.annotations.VisibleForTesting;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Slf4j
public class ZkWatchDog extends ZkClient implements DataCallback {

    private static final String SIGNAL = "signal";
    private static final String BUILD_VERSION = "build-version";

    @VisibleForTesting
    protected String signalPath;
    private Signal signal;

    @VisibleForTesting
    protected String buildVersionPath;
    private String buildVersion;

    @VisibleForTesting
    Set<LifeCycleObserver> observers = new HashSet<>();
    @VisibleForTesting
    Set<BuildVersionObserver> buildVersionObservers = new HashSet<>();

    @Builder
    public ZkWatchDog(String id, String serviceName, String connectionString,
                      int sessionTimeout, Signal signal) throws IOException {
        super(id, serviceName, connectionString, sessionTimeout);

        this.buildVersionPath = getPaths(serviceName, id, BUILD_VERSION);
        this.signalPath = getPaths(serviceName, id, SIGNAL);
        if (signal == null) {
            signal = Signal.NONE;
        }
        this.signal = signal;
        initWatch();
    }

    @Override
    void validateNodes() throws KeeperException, InterruptedException {
        super.validateNodes();
        ensureZNode(serviceName, id, SIGNAL);
        ensureZNode(serviceName, id, BUILD_VERSION);
    }


    @VisibleForTesting
    void subscribeSignal() throws KeeperException, InterruptedException {
        checkData(signalPath);
    }

    @VisibleForTesting
    void subscribeBuildVersion() throws KeeperException, InterruptedException {
        checkData(buildVersionPath);
    }

    private void checkData(String path) throws KeeperException, InterruptedException {
        zookeeper.getData(path, this, this, null);
    }

    @Override
    void initWatch() {
        try {
            validateNodes();
            subscribeSignal();
            subscribeBuildVersion();
        } catch (KeeperException | InterruptedException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Subscribe for events.
     */
    public void subscribe(LifeCycleObserver observer) {
        observers.add(observer);
    }

    /**
     * Subscribe for events.
     */
    public void subscribe(BuildVersionObserver observer) {
        buildVersionObservers.add(observer);
    }

    /**
     * Unsubscribe for events.
     */
    public void unsubscribe(LifeCycleObserver observer) {
        observers.remove(observer);
    }

    /**
     * Unsubscribe for events.
     */
    public void unsubscribe(BuildVersionObserver observer) {
        buildVersionObservers.remove(observer);
    }

    @Override
    public void process(WatchedEvent event) {
        log.info("Received event: {}", event);
        try {
            if (!refreshConnection(event.getState())) {
                if (signalPath.equals(event.getPath())) {
                    subscribeSignal();
                }
                if (buildVersionPath.equals(event.getPath())) {
                    subscribeBuildVersion();
                }
            }
        } catch (IOException | InterruptedException | KeeperException e) {
            log.error("Failed to read zk event: {}", e.getMessage(), e);
        }
    }

    @Override
    public void processResult(int rc, String path, Object ctx, byte[] data, Stat stat) {
        log.debug("Received result on path: {}", path);
        if (signalPath.equals(path) && data != null && data.length > 0) {
            String signalString = new String(data);
            try {
                signal = Signal.valueOf(signalString);
            } catch (Exception e) {
                log.error("Received unknown signal: {}", signalString, e);
            }
            notifyObservers();
        }

        if (buildVersionPath.equals(path) && data != null && data.length > 0) {
            this.buildVersion = new String(data);
            notifyBuildVersionObservers();
        }
    }

    protected void notifyObservers() {
        for (LifeCycleObserver observer : observers) {
            observer.handle(signal);
        }
    }

    protected void notifyBuildVersionObservers() {
        for (BuildVersionObserver observer : buildVersionObservers) {
            observer.handle(buildVersion);
        }
    }
}
