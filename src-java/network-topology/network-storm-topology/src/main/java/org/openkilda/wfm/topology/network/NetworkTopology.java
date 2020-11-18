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

package org.openkilda.wfm.topology.network;

import org.openkilda.config.KafkaTopicsConfig;
import org.openkilda.messaging.Message;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.spi.PersistenceProvider;
import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.kafka.MessageSerializer;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorSpout;
import org.openkilda.wfm.share.hubandspoke.WorkerBolt;
import org.openkilda.wfm.share.zk.ZooKeeperBolt;
import org.openkilda.wfm.share.zk.ZooKeeperSpout;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.network.model.NetworkOptions;
import org.openkilda.wfm.topology.network.storm.ComponentId;
import org.openkilda.wfm.topology.network.storm.bolt.GrpcEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.GrpcRouter;
import org.openkilda.wfm.topology.network.storm.bolt.NorthboundEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.RerouteEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.SpeakerEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.SpeakerRulesEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.StatusEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.SwitchManagerEncoder;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.hub.BfdHub;
import org.openkilda.wfm.topology.network.storm.bolt.bfd.worker.BfdWorker;
import org.openkilda.wfm.topology.network.storm.bolt.decisionmaker.DecisionMakerHandler;
import org.openkilda.wfm.topology.network.storm.bolt.history.HistoryHandler;
import org.openkilda.wfm.topology.network.storm.bolt.isl.IslHandler;
import org.openkilda.wfm.topology.network.storm.bolt.port.PortHandler;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRouter;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRulesRouter;
import org.openkilda.wfm.topology.network.storm.bolt.speaker.SpeakerRulesWorker;
import org.openkilda.wfm.topology.network.storm.bolt.sw.SwitchHandler;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.SwitchManagerRouter;
import org.openkilda.wfm.topology.network.storm.bolt.swmanager.SwitchManagerWorker;
import org.openkilda.wfm.topology.network.storm.bolt.uniisl.UniIslHandler;
import org.openkilda.wfm.topology.network.storm.bolt.watcher.WatcherHandler;
import org.openkilda.wfm.topology.network.storm.bolt.watchlist.WatchListHandler;
import org.openkilda.wfm.topology.network.storm.spout.NetworkHistory;
import org.openkilda.wfm.topology.utils.MessageKafkaTranslator;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;

import java.util.concurrent.TimeUnit;

public class NetworkTopology extends AbstractTopology<NetworkTopologyConfig> {
    private final PersistenceManager persistenceManager;
    private final NetworkOptions options;
    private final KafkaTopicsConfig kafkaTopics;

    public NetworkTopology(LaunchEnvironment env) {
        super(env, NetworkTopologyConfig.class);

        persistenceManager = PersistenceProvider.getInstance().getPersistenceManager(configurationProvider);
        options = new NetworkOptions(getConfig());
        kafkaTopics = getConfig().getKafkaTopics();
    }

    /**
     * Discovery topology factory.
     */
    @Override
    public StormTopology createTopology() {
        int scaleFactor = topologyConfig.getScaleFactor();

        TopologyBuilder topology = new TopologyBuilder();

        zookeeperSpout(topology);

        inputSwitchManager(topology, scaleFactor);
        switchManagerRouter(topology, scaleFactor);
        workerSwitchManager(topology, scaleFactor);

        inputSpeaker(topology, scaleFactor);
        inputSpeakerRules(topology, scaleFactor);
        workerSpeakerRules(topology, scaleFactor);

        inputGrpc(topology, scaleFactor);
        routeGrpc(topology, scaleFactor);

        coordinator(topology);
        networkHistory(topology);

        speakerRouter(topology, scaleFactor);
        speakerRulesRouter(topology, scaleFactor);
        watchList(topology, scaleFactor);
        watcher(topology, scaleFactor);
        decisionMaker(topology, scaleFactor);

        switchHandler(topology, scaleFactor);
        portHandler(topology, scaleFactor);
        bfdHub(topology, scaleFactor);
        bfdWorker(topology, scaleFactor);
        uniIslHandler(topology, scaleFactor);
        islHandler(topology, scaleFactor);

        outputSpeaker(topology, scaleFactor);
        outputSwitchManager(topology, scaleFactor);
        outputSpeakerRules(topology, scaleFactor);
        outputReroute(topology, scaleFactor);
        outputStatus(topology, scaleFactor);
        outputNorthbound(topology, scaleFactor);
        outputGrpc(topology, scaleFactor);

        historyBolt(topology, scaleFactor);

        zookeeperBolt(topology);

        return topology.createTopology();
    }

    private void coordinator(TopologyBuilder topology) {
        topology.setSpout(CoordinatorSpout.ID, new CoordinatorSpout(), 1);

        Fields keyGrouping = new Fields(MessageKafkaTranslator.KEY_FIELD);
        topology.setBolt(CoordinatorBolt.ID, new CoordinatorBolt(), 1)
                .allGrouping(CoordinatorSpout.ID)
                .fieldsGrouping(BfdWorker.BOLT_ID, CoordinatorBolt.INCOME_STREAM, keyGrouping)
                .fieldsGrouping(SwitchManagerWorker.BOLT_ID, CoordinatorBolt.INCOME_STREAM, keyGrouping);
    }

    private void zookeeperSpout(TopologyBuilder topology) {
        String zkString = "zookeeper.pendev:2181/kilda";
        ZooKeeperSpout zooKeeperSpout = new ZooKeeperSpout("green", "network", 1, zkString);
        topology.setSpout(ComponentId.INPUT_ZOOKEEPER.toString(), zooKeeperSpout, 1);
    }

    private void inputSpeaker(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                kafkaTopics.getTopoDiscoTopic(), ComponentId.INPUT_SPEAKER.toString());
        topology.setSpout(ComponentId.INPUT_SPEAKER.toString(), spout, scaleFactor);
    }

    private void inputSwitchManager(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                kafkaTopics.getNorthboundTopic(), ComponentId.INPUT_SWMANAGER.toString());
        topology.setSpout(ComponentId.INPUT_SWMANAGER.toString(), spout, scaleFactor);
    }

    private void inputSpeakerRules(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                kafkaTopics.getTopoSwitchManagerTopic(), ComponentId.INPUT_SPEAKER_RULES.toString());
        topology.setSpout(ComponentId.INPUT_SPEAKER_RULES.toString(), spout, scaleFactor);
    }

    private void workerSpeakerRules(TopologyBuilder topology, int scaleFactor) {
        long speakerIoTimeout = TimeUnit.SECONDS.toMillis(topologyConfig.getSpeakerIoTimeoutSeconds());
        WorkerBolt.Config workerConfig = SpeakerRulesWorker.Config.builder()
                .hubComponent(IslHandler.BOLT_ID)
                .workerSpoutComponent(SpeakerRulesRouter.BOLT_ID)
                .streamToHub(SpeakerRulesWorker.STREAM_HUB_ID)
                .defaultTimeout((int) speakerIoTimeout)
                .build();
        SpeakerRulesWorker speakerRulesWorker = new SpeakerRulesWorker(workerConfig);
        Fields keyGrouping = new Fields(MessageKafkaTranslator.KEY_FIELD);
        topology.setBolt(SpeakerRulesWorker.BOLT_ID, speakerRulesWorker, scaleFactor)
                .directGrouping(CoordinatorBolt.ID)
                .fieldsGrouping(workerConfig.getHubComponent(), IslHandler.STREAM_SPEAKER_RULES_ID, keyGrouping)
                .fieldsGrouping(workerConfig.getWorkerSpoutComponent(),
                        SpeakerRulesRouter.STREAM_WORKER_ID, keyGrouping);
    }

    private void speakerRouter(TopologyBuilder topology, int scaleFactor) {
        Fields keyGrouping = new Fields(MessageKafkaTranslator.KEY_FIELD);
        SpeakerRouter bolt = new SpeakerRouter();
        topology.setBolt(SpeakerRouter.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(ComponentId.INPUT_SPEAKER.toString(), keyGrouping)
                .allGrouping(ComponentId.INPUT_ZOOKEEPER.toString());
    }

    private void inputGrpc(TopologyBuilder topology, int scaleFactor) {
        KafkaSpout<String, Message> spout = buildKafkaSpout(
                kafkaTopics.getGrpcResponseTopic(), ComponentId.INPUT_GRPC.toString());
        topology.setSpout(ComponentId.INPUT_GRPC.toString(), spout, scaleFactor);
    }

    private void routeGrpc(TopologyBuilder topology, int scaleFactor) {
        GrpcRouter bolt = new GrpcRouter();
        topology.setBolt(GrpcRouter.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(ComponentId.INPUT_GRPC.toString());
    }

    private void workerSwitchManager(TopologyBuilder topology, int scaleFactor) {
        long swmanagerIoTimeout = TimeUnit.SECONDS.toMillis(topologyConfig.getSwitchManagerIoTimeoutSeconds());
        WorkerBolt.Config workerConfig = SwitchManagerWorker.Config.builder()
                .hubComponent(SwitchHandler.BOLT_ID)
                .workerSpoutComponent(SwitchManagerRouter.BOLT_ID)
                .streamToHub(SwitchManagerWorker.STREAM_HUB_ID)
                .defaultTimeout((int) swmanagerIoTimeout)
                .build();
        SwitchManagerWorker switchManagerWorker = new SwitchManagerWorker(workerConfig, options);
        Fields keyGrouping = new Fields(MessageKafkaTranslator.FIELD_ID_KEY);
        topology.setBolt(SwitchManagerWorker.BOLT_ID, switchManagerWorker, scaleFactor)
                .directGrouping(CoordinatorBolt.ID)
                .fieldsGrouping(workerConfig.getHubComponent(), SwitchHandler.STREAM_SWMANAGER_ID, keyGrouping)
                .fieldsGrouping(workerConfig.getWorkerSpoutComponent(),
                        SwitchManagerRouter.STREAM_WORKER_ID, keyGrouping);
    }

    private void switchManagerRouter(TopologyBuilder topology, int scaleFactor) {
        Fields keyGrouping = new Fields(MessageKafkaTranslator.FIELD_ID_KEY);
        SwitchManagerRouter bolt = new SwitchManagerRouter();
        topology.setBolt(SwitchManagerRouter.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(ComponentId.INPUT_SWMANAGER.toString(), keyGrouping);
    }

    private void speakerRulesRouter(TopologyBuilder topology, int scaleFactor) {
        Fields keyGrouping = new Fields(MessageKafkaTranslator.KEY_FIELD);
        SpeakerRulesRouter bolt = new SpeakerRulesRouter();
        topology.setBolt(SpeakerRulesRouter.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(ComponentId.INPUT_SPEAKER_RULES.toString(), keyGrouping);
    }

    private void networkHistory(TopologyBuilder topology) {
        NetworkHistory spout = new NetworkHistory(persistenceManager);
        topology.setSpout(NetworkHistory.SPOUT_ID, spout, 1);
    }

    private void watchList(TopologyBuilder topology, int scaleFactor) {
        WatchListHandler bolt = new WatchListHandler(options);
        Fields portGrouping = new Fields(PortHandler.FIELD_ID_DATAPATH, PortHandler.FIELD_ID_PORT_NUMBER);
        Fields uniIslGrouping = new Fields(UniIslHandler.FIELD_ID_DATAPATH, UniIslHandler.FIELD_ID_PORT_NUMBER);
        Fields islGrouping = new Fields(IslHandler.FIELD_ID_DATAPATH, IslHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(WatchListHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .allGrouping(ComponentId.INPUT_ZOOKEEPER.toString())
                .fieldsGrouping(PortHandler.BOLT_ID, PortHandler.STREAM_POLL_ID, portGrouping)
                .fieldsGrouping(UniIslHandler.BOLT_ID, UniIslHandler.STREAM_POLL_ID, uniIslGrouping)
                .fieldsGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_POLL_ID, islGrouping);
    }

    private void watcher(TopologyBuilder topology, int scaleFactor) {
        WatcherHandler bolt = new WatcherHandler(options);
        Fields watchListGrouping = new Fields(WatchListHandler.FIELD_ID_DATAPATH,
                WatchListHandler.FIELD_ID_PORT_NUMBER);
        Fields speakerGrouping = new Fields(SpeakerRouter.FIELD_ID_DATAPATH, SpeakerRouter.FIELD_ID_PORT_NUMBER);
        topology.setBolt(WatcherHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .allGrouping(ComponentId.INPUT_ZOOKEEPER.toString())
                .fieldsGrouping(WatchListHandler.BOLT_ID, watchListGrouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_WATCHER_ID, speakerGrouping);
    }

    private void decisionMaker(TopologyBuilder topology, int scaleFactor) {
        DecisionMakerHandler bolt = new DecisionMakerHandler(options);
        Fields watcherGrouping = new Fields(WatcherHandler.FIELD_ID_DATAPATH, WatcherHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(DecisionMakerHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .allGrouping(ComponentId.INPUT_ZOOKEEPER.toString())
                .fieldsGrouping(WatcherHandler.BOLT_ID, watcherGrouping);
    }

    private void switchHandler(TopologyBuilder topology, int scaleFactor) {
        SwitchHandler bolt = new SwitchHandler(options, persistenceManager);
        Fields grouping = new Fields(SpeakerRouter.FIELD_ID_DATAPATH);
        topology.setBolt(SwitchHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(NetworkHistory.SPOUT_ID, grouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, grouping)
                .directGrouping(SwitchManagerWorker.BOLT_ID, SwitchManagerWorker.STREAM_HUB_ID);
    }

    private void portHandler(TopologyBuilder topology, int scaleFactor) {
        PortHandler bolt = new PortHandler(options, persistenceManager);
        Fields endpointGrouping = new Fields(SwitchHandler.FIELD_ID_DATAPATH, SwitchHandler.FIELD_ID_PORT_NUMBER);
        Fields decisionMakerGrouping = new Fields(DecisionMakerHandler.FIELD_ID_DATAPATH,
                DecisionMakerHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(PortHandler.BOLT_ID, bolt, scaleFactor)
                .allGrouping(CoordinatorSpout.ID)
                .allGrouping(ComponentId.INPUT_ZOOKEEPER.toString())
                .fieldsGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_PORT_ID, endpointGrouping)
                .fieldsGrouping(DecisionMakerHandler.BOLT_ID, decisionMakerGrouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_PORT_ID, endpointGrouping)
                .fieldsGrouping(WatcherHandler.BOLT_ID, WatcherHandler.STREAM_PORT_ID, endpointGrouping);
    }

    private void bfdHub(TopologyBuilder topology, int scaleFactor) {
        BfdHub bolt = new BfdHub(options, persistenceManager);
        Fields switchGrouping = new Fields(SwitchHandler.FIELD_ID_DATAPATH);
        Fields islGrouping = new Fields(IslHandler.FIELD_ID_DATAPATH);
        topology.setBolt(BfdHub.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_BFD_HUB_ID, switchGrouping)
                .fieldsGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_BFD_HUB_ID, islGrouping)
                .directGrouping(BfdWorker.BOLT_ID, BfdWorker.STREAM_HUB_ID)
                .allGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_BCAST_ID);
    }

    private void bfdWorker(TopologyBuilder topology, int scaleFactor) {
        long speakerIoTimeout = TimeUnit.SECONDS.toMillis(topologyConfig.getSpeakerIoTimeoutSeconds());
        WorkerBolt.Config workerConfig = BfdWorker.Config.builder()
                .hubComponent(BfdHub.BOLT_ID)
                .workerSpoutComponent(SpeakerRouter.BOLT_ID)
                .streamToHub(BfdWorker.STREAM_HUB_ID)
                .defaultTimeout((int) speakerIoTimeout)
                .build();
        BfdWorker bolt = new BfdWorker(workerConfig, persistenceManager);
        Fields keyGrouping = new Fields(MessageKafkaTranslator.KEY_FIELD);
        topology.setBolt(BfdWorker.BOLT_ID, bolt, scaleFactor)
                .directGrouping(CoordinatorBolt.ID)
                .fieldsGrouping(workerConfig.getHubComponent(), BfdHub.STREAM_WORKER_ID, keyGrouping)
                .fieldsGrouping(
                        workerConfig.getWorkerSpoutComponent(), SpeakerRouter.STREAM_BFD_WORKER_ID, keyGrouping)
                .fieldsGrouping(GrpcRouter.BOLT_ID, GrpcRouter.STREAM_BFD_WORKER_ID, keyGrouping);
    }

    private void uniIslHandler(TopologyBuilder topology, int scaleFactor) {
        UniIslHandler bolt = new UniIslHandler();
        Fields portGrouping = new Fields(PortHandler.FIELD_ID_DATAPATH, PortHandler.FIELD_ID_PORT_NUMBER);
        Fields bfdPortGrouping = new Fields(BfdHub.FIELD_ID_DATAPATH, BfdHub.FIELD_ID_PORT_NUMBER);
        Fields islGrouping = new Fields(IslHandler.FIELD_ID_DATAPATH, IslHandler.FIELD_ID_PORT_NUMBER);
        topology.setBolt(UniIslHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(PortHandler.BOLT_ID, portGrouping)
                .fieldsGrouping(BfdHub.BOLT_ID, BfdHub.STREAM_UNIISL_ID, bfdPortGrouping)
                .fieldsGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_UNIISL_ID, islGrouping);
    }

    private void islHandler(TopologyBuilder topology, int scaleFactor) {
        IslHandler bolt = new IslHandler(persistenceManager, options);
        Fields islGrouping = new Fields(UniIslHandler.FIELD_ID_ISL_SOURCE, UniIslHandler.FIELD_ID_ISL_DEST);
        topology.setBolt(IslHandler.BOLT_ID, bolt, scaleFactor)
                .fieldsGrouping(UniIslHandler.BOLT_ID, islGrouping)
                .fieldsGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_ISL_ID, islGrouping)
                .directGrouping(SpeakerRulesWorker.BOLT_ID, SpeakerRulesWorker.STREAM_HUB_ID);
    }

    private void outputSpeaker(TopologyBuilder topology, int scaleFactor) {
        SpeakerEncoder bolt = new SpeakerEncoder();
        topology.setBolt(SpeakerEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(WatcherHandler.BOLT_ID, WatcherHandler.STREAM_SPEAKER_ID)
                .shuffleGrouping(BfdWorker.BOLT_ID, BfdWorker.STREAM_SPEAKER_ID);

        KafkaBolt output = buildKafkaBolt(kafkaTopics.getSpeakerDiscoTopic());
        topology.setBolt(ComponentId.SPEAKER_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(SpeakerEncoder.BOLT_ID);
    }

    private void outputSwitchManager(TopologyBuilder topology, int scaleFactor) {
        SwitchManagerEncoder bolt = new SwitchManagerEncoder();
        topology.setBolt(SwitchManagerEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(SwitchManagerWorker.BOLT_ID);

        KafkaBolt output = buildKafkaBolt(kafkaTopics.getTopoSwitchManagerNetworkTopic());
        topology.setBolt(ComponentId.SWMANAGER_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(SwitchManagerEncoder.BOLT_ID);
    }

    private void outputSpeakerRules(TopologyBuilder topology, int scaleFactor) {
        SpeakerRulesEncoder encoderRules = new SpeakerRulesEncoder();
        topology.setBolt(SpeakerRulesEncoder.BOLT_ID, encoderRules, scaleFactor)
                .shuffleGrouping(SpeakerRulesWorker.BOLT_ID);

        KafkaBolt outputRules = buildKafkaBolt(kafkaTopics.getSpeakerTopic());
        topology.setBolt(ComponentId.SPEAKER_RULES_OUTPUT.toString(), outputRules, scaleFactor)
                .shuffleGrouping(SpeakerRulesEncoder.BOLT_ID);

    }

    private void outputReroute(TopologyBuilder topology, int scaleFactor) {
        RerouteEncoder bolt = new RerouteEncoder();
        topology.setBolt(RerouteEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_REROUTE_ID)
                .shuffleGrouping(SwitchHandler.BOLT_ID, SwitchHandler.STREAM_REROUTE_ID);

        KafkaBolt output = buildKafkaBolt(kafkaTopics.getTopoRerouteTopic());
        topology.setBolt(ComponentId.REROUTE_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(RerouteEncoder.BOLT_ID);
    }

    private void outputStatus(TopologyBuilder topology, int scaleFactor) {
        StatusEncoder bolt = new StatusEncoder();
        topology.setBolt(StatusEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(IslHandler.BOLT_ID, IslHandler.STREAM_STATUS_ID);

        KafkaBolt output = buildKafkaBolt(kafkaTopics.getNetworkIslStatusTopic());
        topology.setBolt(ComponentId.STATUS_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(StatusEncoder.BOLT_ID);
    }

    private void outputNorthbound(TopologyBuilder topology, int scaleFactor) {
        NorthboundEncoder bolt = new NorthboundEncoder();
        topology.setBolt(NorthboundEncoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(PortHandler.BOLT_ID, PortHandler.STREAM_NORTHBOUND_ID);

        KafkaBolt kafkaNorthboundBolt = buildKafkaBolt(kafkaTopics.getNorthboundTopic());
        topology.setBolt(ComponentId.NB_OUTPUT.toString(), kafkaNorthboundBolt, scaleFactor)
                .shuffleGrouping(NorthboundEncoder.BOLT_ID);
    }

    private void outputGrpc(TopologyBuilder topology, int scaleFactor) {
        GrpcEncoder encoder = new GrpcEncoder();
        topology.setBolt(GrpcEncoder.BOLT_ID, encoder, scaleFactor)
                .shuffleGrouping(BfdWorker.BOLT_ID, BfdWorker.STREAM_GRPC_ID);

        KafkaBolt<String, Message> output = makeKafkaBolt(kafkaTopics.getGrpcSpeakerTopic(), MessageSerializer.class);
        topology.setBolt(ComponentId.GRPC_OUTPUT.toString(), output, scaleFactor)
                .shuffleGrouping(GrpcEncoder.BOLT_ID);
    }

    private void historyBolt(TopologyBuilder topology, int scaleFactor) {
        HistoryHandler bolt = new HistoryHandler(persistenceManager);
        topology.setBolt(ComponentId.HISTORY_HANDLER.toString(), bolt, scaleFactor)
                .shuffleGrouping(PortHandler.BOLT_ID, PortHandler.STREAM_HISTORY_ID);
    }

    private void zookeeperBolt(TopologyBuilder topology) {
        String zkString = "zookeeper.pendev:2181/kilda";
        ZooKeeperBolt zooKeeperBolt = new ZooKeeperBolt("green", "network", 1, zkString);
        topology.setBolt(ComponentId.ZOOKEEPER_OUTPUT.toString(), zooKeeperBolt, 1)
                .shuffleGrouping(SpeakerRouter.BOLT_ID, SpeakerRouter.STREAM_ZOOKEEPER_ID)
                .shuffleGrouping(WatcherHandler.BOLT_ID, WatcherHandler.STREAM_ZOOKEEPER_ID)
                .shuffleGrouping(WatchListHandler.BOLT_ID, WatchListHandler.STREAM_ZOOKEEPER_ID)
                .shuffleGrouping(DecisionMakerHandler.BOLT_ID, DecisionMakerHandler.STREAM_ZOOKEEPER_ID)
                .shuffleGrouping(PortHandler.BOLT_ID, PortHandler.STREAM_ZOOKEEPER_ID);
    }

    /**
     * Discovery topology uploader.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new NetworkTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
