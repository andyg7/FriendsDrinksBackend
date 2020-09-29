package andrewgrant.friendsdrinks;

import static andrewgrant.friendsdrinks.env.Properties.load;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import andrewgrant.friendsdrinks.api.avro.*;
import andrewgrant.friendsdrinks.avro.CreatedFriendsDrinks;
import andrewgrant.friendsdrinks.avro.FriendsDrinksState;
import andrewgrant.friendsdrinks.avro.FriendsDrinksStateAggregate;
import andrewgrant.friendsdrinks.avro.UpdatedFriendsDrinks;

/**
 * Owns writing to non-API topics.
 */
public class WriterService {

    private static final Logger log = LoggerFactory.getLogger(WriterService.class);

    public Topology buildTopology(Properties envProps,
                                  FriendsDrinksAvro avro) {
        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, FriendsDrinksEvent> apiEvents = builder.stream(envProps.getProperty("friendsdrinks-api.topic.name"),
                Consumed.with(Serdes.String(), avro.apiFriendsDrinksSerde()));

        KStream<String, FriendsDrinksEvent> successApiResponses = apiEvents.filter((friendsDrinksId, friendsDrinksEvent) ->
                (friendsDrinksEvent.getEventType().equals(EventType.CREATE_FRIENDSDRINKS_RESPONSE) &&
                        friendsDrinksEvent.getCreateFriendsDrinksResponse().getResult().equals(Result.SUCCESS)) ||
                        (friendsDrinksEvent.getEventType().equals(EventType.UPDATE_FRIENDSDRINKS_RESPONSE) &&
                                friendsDrinksEvent.getUpdateFriendsDrinksResponse().getResult().equals(Result.SUCCESS)) ||
                        (friendsDrinksEvent.getEventType().equals(EventType.DELETE_FRIENDSDRINKS_RESPONSE) &&
                                friendsDrinksEvent.getDeleteFriendsDrinksResponse().getResult().equals(Result.SUCCESS))
        );

        KStream<String, FriendsDrinksEvent> apiRequests = apiEvents
                .filter((k, v) -> v.getEventType().equals(EventType.CREATE_FRIENDSDRINKS_REQUEST) ||
                        v.getEventType().equals(EventType.UPDATE_FRIENDSDRINKS_REQUEST) ||
                        v.getEventType().equals(EventType.DELETE_FRIENDSDRINKS_REQUEST));

        successApiResponses.join(apiRequests,
                (l, r) -> {
                    if (r.getEventType().equals(EventType.CREATE_FRIENDSDRINKS_REQUEST)) {
                        log.info("Got create join {}", r.getCreateFriendsDrinksRequest().getRequestId());
                        CreateFriendsDrinksRequest createFriendsDrinksRequest =
                                r.getCreateFriendsDrinksRequest();
                        CreatedFriendsDrinks friendsDrinks = CreatedFriendsDrinks
                                .newBuilder()
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setAdminUserId(createFriendsDrinksRequest.getFriendsDrinksId().getAdminUserId())
                                        .setFriendsDrinksId(createFriendsDrinksRequest.getFriendsDrinksId().getFriendsDrinksId())
                                        .build())
                                .setName(createFriendsDrinksRequest.getName())
                                .setScheduleType(andrewgrant.friendsdrinks.avro.ScheduleType.valueOf(
                                        createFriendsDrinksRequest.getScheduleType().toString()))
                                .setCronSchedule(createFriendsDrinksRequest.getCronSchedule())
                                .build();
                        return andrewgrant.friendsdrinks.avro.FriendsDrinksEvent.newBuilder()
                                .setEventType(andrewgrant.friendsdrinks.avro.EventType.CREATED)
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setFriendsDrinksId(r.getCreateFriendsDrinksRequest().getFriendsDrinksId().getFriendsDrinksId())
                                        .setAdminUserId(r.getCreateFriendsDrinksRequest().getFriendsDrinksId().getAdminUserId())
                                        .build())
                                .setCreatedFriendsDrinks(friendsDrinks)
                                .build();
                    } else if (r.getEventType().equals(EventType.DELETE_FRIENDSDRINKS_REQUEST)) {
                        log.info("Got delete join {}", r.getDeleteFriendsDrinksRequest().getRequestId());
                        return andrewgrant.friendsdrinks.avro.FriendsDrinksEvent
                                .newBuilder()
                                .setEventType(andrewgrant.friendsdrinks.avro.EventType.DELETED)
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setFriendsDrinksId(r.getDeleteFriendsDrinksRequest().getFriendsDrinksId().getFriendsDrinksId())
                                        .setAdminUserId(r.getDeleteFriendsDrinksRequest().getFriendsDrinksId().getAdminUserId())
                                        .build())
                                .build();
                    } else if (r.getEventType().equals(EventType.UPDATE_FRIENDSDRINKS_REQUEST)) {
                        log.info("Got update join {}", r.getUpdateFriendsDrinksRequest().getRequestId());
                        UpdateFriendsDrinksRequest updateFriendsDrinksRequest = r.getUpdateFriendsDrinksRequest();
                        andrewgrant.friendsdrinks.avro.ScheduleType scheduleType = null;
                        if (updateFriendsDrinksRequest.getScheduleType() != null) {
                            scheduleType = andrewgrant.friendsdrinks.avro.ScheduleType.valueOf(
                                    updateFriendsDrinksRequest.getScheduleType().toString());
                        }

                        UpdatedFriendsDrinks friendsDrinks = UpdatedFriendsDrinks
                                .newBuilder()
                                .setUpdateType(
                                        andrewgrant.friendsdrinks.avro.UpdateType.valueOf(
                                                updateFriendsDrinksRequest.getUpdateType().toString()))
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setAdminUserId(updateFriendsDrinksRequest.getFriendsDrinksId().getAdminUserId())
                                        .setFriendsDrinksId(updateFriendsDrinksRequest.getFriendsDrinksId().getFriendsDrinksId())
                                        .build())
                                .setName(updateFriendsDrinksRequest.getName())
                                .setScheduleType(scheduleType)
                                .setCronSchedule(updateFriendsDrinksRequest.getCronSchedule())
                                .build();
                        return andrewgrant.friendsdrinks.avro.FriendsDrinksEvent
                                .newBuilder()
                                .setEventType(andrewgrant.friendsdrinks.avro.EventType.UPDATED)
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setFriendsDrinksId(r.getUpdateFriendsDrinksRequest().getFriendsDrinksId().getFriendsDrinksId())
                                        .setAdminUserId(r.getUpdateFriendsDrinksRequest().getFriendsDrinksId().getAdminUserId())
                                        .build())
                                .setUpdatedFriendsDrinks(friendsDrinks)
                                .build();
                    } else {
                        throw new RuntimeException(
                                String.format("Received unexpected event type %s", r.getEventType().toString()));
                    }
                },
                JoinWindows.of(Duration.ofSeconds(30)),
                StreamJoined.with(Serdes.String(),
                        avro.apiFriendsDrinksSerde(),
                        avro.apiFriendsDrinksSerde()))
                .selectKey((k, v) -> v.getFriendsDrinksId())
                .to(envProps.getProperty("friendsdrinks-event.topic.name"),
                        Produced.with(avro.friendsDrinksIdSerde(), avro.friendsDrinksEventSerde()));


        KStream<andrewgrant.friendsdrinks.avro.FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> friendsDrinksStateStream =
                builder.stream(envProps.getProperty("friendsdrinks-event.topic.name"),
                        Consumed.with(avro.friendsDrinksIdSerde(), avro.friendsDrinksEventSerde()))
                        .groupByKey(Grouped.with(avro.friendsDrinksIdSerde(), avro.friendsDrinksEventSerde()))
                        .aggregate(
                                () -> FriendsDrinksStateAggregate.newBuilder().build(),
                                (aggKey, newValue, aggValue) -> {
                                    if (newValue.getEventType().equals(andrewgrant.friendsdrinks.avro.EventType.CREATED)) {
                                        CreatedFriendsDrinks createdFriendsDrinks = newValue.getCreatedFriendsDrinks();
                                        FriendsDrinksState.Builder friendsDrinksStateBuilder;
                                        if (aggValue.getFriendsDrinksState() == null) {
                                            friendsDrinksStateBuilder = FriendsDrinksState
                                                    .newBuilder();
                                        } else {
                                            friendsDrinksStateBuilder = FriendsDrinksState
                                                    .newBuilder(aggValue.getFriendsDrinksState());
                                        }

                                        FriendsDrinksState friendsDrinksState =
                                                friendsDrinksStateBuilder.setName(createdFriendsDrinks.getName())
                                                        .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                                                .newBuilder()
                                                                .setFriendsDrinksId(
                                                                        newValue.getFriendsDrinksId().getFriendsDrinksId())
                                                                .setAdminUserId(newValue.getFriendsDrinksId().getAdminUserId())
                                                                .build())
                                                        .setCronSchedule(createdFriendsDrinks.getCronSchedule())
                                                        .setScheduleType(createdFriendsDrinks.getScheduleType())
                                                        .build();
                                        return FriendsDrinksStateAggregate.newBuilder()
                                                .setFriendsDrinksState(friendsDrinksState)
                                                .build();
                                    } else if (newValue.getEventType().equals(andrewgrant.friendsdrinks.avro.EventType.UPDATED)) {
                                        UpdatedFriendsDrinks updatedFriendsDrinks = newValue.getUpdatedFriendsDrinks();
                                        FriendsDrinksState.Builder friendsDrinksStateBuilder;
                                        if (aggValue.getFriendsDrinksState() == null) {
                                            throw new RuntimeException(String.format("FriendsDrinksState is null for %s", aggKey));
                                        }
                                        andrewgrant.friendsdrinks.avro.UpdateType updateType = updatedFriendsDrinks.getUpdateType();
                                        friendsDrinksStateBuilder = FriendsDrinksState.newBuilder(aggValue.getFriendsDrinksState());
                                        String name;
                                        if (updatedFriendsDrinks.getName() != null) {
                                            name = updatedFriendsDrinks.getName();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.PARTIAL)) {
                                            name = aggValue.getFriendsDrinksState().getName();
                                        } else {
                                            name = null;
                                        }
                                        friendsDrinksStateBuilder.setName(name);

                                        String cronSchedule;
                                        if (updatedFriendsDrinks.getCronSchedule() != null) {
                                            cronSchedule = updatedFriendsDrinks.getCronSchedule();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.PARTIAL)) {
                                            cronSchedule = aggValue.getFriendsDrinksState().getCronSchedule();
                                        } else {
                                            cronSchedule = null;
                                        }
                                        friendsDrinksStateBuilder.setCronSchedule(cronSchedule);

                                        andrewgrant.friendsdrinks.avro.ScheduleType scheduleType;
                                        if (updatedFriendsDrinks.getScheduleType() != null) {
                                            scheduleType = updatedFriendsDrinks.getScheduleType();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.PARTIAL)) {
                                            scheduleType = aggValue.getFriendsDrinksState().getScheduleType();
                                        } else {
                                            scheduleType = null;
                                        }
                                        friendsDrinksStateBuilder.setScheduleType(scheduleType);

                                        return FriendsDrinksStateAggregate.newBuilder()
                                                .setFriendsDrinksState(friendsDrinksStateBuilder.build())
                                                .build();
                                    } else if (newValue.getEventType().equals(andrewgrant.friendsdrinks.avro.EventType.DELETED)) {
                                        return null;
                                    } else {
                                        throw new RuntimeException(String.format("Unexpected event type %s", newValue.getEventType().name()));
                                    }
                                },
                                Materialized.<
                                        andrewgrant.friendsdrinks.avro.FriendsDrinksId,
                                        FriendsDrinksStateAggregate, KeyValueStore<Bytes, byte[]>>
                                        as("internal_writer_service_friendsdrinks-state_tracker")
                                        .withKeySerde(avro.friendsDrinksIdSerde())
                                        .withValueSerde(avro.friendsDrinksStateAggregateSerde())
                        ).toStream().mapValues(value -> {
                    if (value == null) {
                        return null;
                    }
                    return value.getFriendsDrinksState();
                });

        friendsDrinksStateStream.to(envProps.getProperty("friendsdrinks-state.topic.name"),
                Produced.with(avro.friendsDrinksIdSerde(), avro.friendsDrinksStateSerde()));

        return builder.build();
    }

    public Properties buildStreamsProperties(Properties envProps) {
        Properties streamProps = new Properties();
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("friendsdrinks-writer.application.id"));
        streamProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        streamProps.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 0);
        return streamProps;
    }

    public static void main(String[] args) throws IOException {
        Properties envProps = load(args[0]);
        WriterService writerService = new WriterService();
        String schemaRegistryUrl = envProps.getProperty("schema.registry.url");
        FriendsDrinksAvro friendsDrinksAvro = new FriendsDrinksAvro(schemaRegistryUrl);
        Topology topology = writerService.buildTopology(envProps, friendsDrinksAvro);
        Properties streamProps = writerService.buildStreamsProperties(envProps);
        KafkaStreams kafkaStreams = new KafkaStreams(topology, streamProps);
        log.info("Starting WriterService application...");

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
