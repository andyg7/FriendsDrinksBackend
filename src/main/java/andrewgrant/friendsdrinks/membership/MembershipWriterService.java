package andrewgrant.friendsdrinks.membership;

import static andrewgrant.friendsdrinks.env.Properties.load;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import andrewgrant.friendsdrinks.avro.*;

/**
 * Owns writing to friendsdrinks-membership-event.
 */
public class MembershipWriterService {

    private static final Logger log = LoggerFactory.getLogger(MembershipWriterService.class);

    private Properties envProps;
    private AvroBuilder avroBuilder;

    public MembershipWriterService(Properties envProps,
                                   AvroBuilder avroBuilder) {
        this.envProps = envProps;
        this.avroBuilder = avroBuilder;
    }

    public Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();

        builder.stream(envProps.getProperty(TopicNameConfigKey.FRIENDSDRINKS_INVITATION_EVENT),
                Consumed.with(avroBuilder.friendsDrinksMembershipIdSerdes(),
                        avroBuilder.friendsDrinksInvitationEventSerde()))
                .filter((k, v) -> v.getEventType().equals(InvitationEventType.RESPONDED_TO) &&
                        v.getFriendsDrinksInvitationRespondedTo().getResponse().equals(Response.ACCEPTED))
                .mapValues(v -> v.getFriendsDrinksInvitationRespondedTo())
                .mapValues(v -> FriendsDrinksMembershipEvent
                        .newBuilder()
                        .setRequestId(v.getRequestId())
                        .setEventType(FriendsDrinksMembershipEventType.ADDED)
                        .setMembershipId(v.getMembershipId())
                        .setFriendsDrinksMembershipAdded(FriendsDrinksMembershipAdded
                                .newBuilder()
                                .setMembershipId(v.getMembershipId())
                                .build())
                        .build())
                .to(envProps.getProperty(TopicNameConfigKey.FRIENDSDRINKS_MEMBERSHIP_EVENT),
                        Produced.with(avroBuilder.friendsDrinksMembershipIdSerdes(), avroBuilder.friendsDrinksMembershipEventSerdes()));


        buildMembershipState(
                builder.stream(envProps.getProperty(TopicNameConfigKey.FRIENDSDRINKS_MEMBERSHIP_EVENT),
                        Consumed.with(avroBuilder.friendsDrinksMembershipIdSerdes(), avroBuilder.friendsDrinksMembershipEventSerdes())))
                .to(envProps.getProperty(TopicNameConfigKey.FRIENDSDRINKS_MEMBERSHIP_STATE),
                        Produced.with(avroBuilder.friendsDrinksMembershipIdSerdes(), avroBuilder.friendsDrinksMembershipStateSerdes()));

        return builder.build();
    }

    private KStream<FriendsDrinksMembershipId, FriendsDrinksMembershipState> buildMembershipState(
            KStream<FriendsDrinksMembershipId, FriendsDrinksMembershipEvent> membershipEventKStream) {
        return membershipEventKStream.groupByKey(Grouped.with(
                avroBuilder.friendsDrinksMembershipIdSerdes(),
                avroBuilder.friendsDrinksMembershipEventSerdes()))
                .aggregate(
                        () -> FriendsDrinksMembershipStateAggregate.newBuilder().build(),
                        (aggKey, newValue, aggValue) -> new MembershipStateAggregator().handleNewEvent(aggKey, newValue, aggValue),
                        Materialized.<
                                FriendsDrinksMembershipId,
                                FriendsDrinksMembershipStateAggregate, KeyValueStore<Bytes, byte[]>>
                                as("friendsdrinks-membership-state-aggregate-state-store")
                                .withKeySerde(avroBuilder.friendsDrinksMembershipIdSerdes())
                                .withValueSerde(avroBuilder.friendsDrinksMembershipStateAggregateSerdes())
                ).toStream().mapValues(v -> v.getFriendsDrinksMembershipState());
    }

    public Properties buildStreamsProperties(Properties envProps) {
        Properties streamProps = new Properties();
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG,
                envProps.getProperty("friendsdrinks-membership-writer.application.id"));
        streamProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        streamProps.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        return streamProps;
    }

    public static void main(String[] args) throws IOException {
        Properties envProps = load(args[0]);
        String schemaRegistryUrl = envProps.getProperty("schema.registry.url");
        MembershipWriterService writerService = new MembershipWriterService(
                envProps, new AvroBuilder(schemaRegistryUrl));
        Topology topology = writerService.buildTopology();
        Properties streamProps = writerService.buildStreamsProperties(envProps);
        KafkaStreams kafkaStreams = new KafkaStreams(topology, streamProps);
        log.info("Starting MembershipWriterService application...");

        final CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                kafkaStreams.close();
                latch.countDown();
            }
        });

        kafkaStreams.start();
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
