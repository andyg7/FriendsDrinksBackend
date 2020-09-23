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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

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

        KStream<FriendsDrinksId, FriendsDrinksEvent> apiEvents = builder.stream(envProps.getProperty("friendsdrinks_api.topic.name"),
                Consumed.with(avro.apiFriendsDrinksIdSerde(), avro.apiFriendsDrinksSerde()));

        KStream<String, FriendsDrinksEvent> successApiResponses = apiEvents.filter((friendsDrinksId, friendsDrinksEvent) ->
                        (friendsDrinksEvent.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_RESPONSE) &&
                                friendsDrinksEvent.getCreateFriendsDrinksResponse().getResult().equals(Result.SUCCESS)) ||
                                (friendsDrinksEvent.getEventType().equals(EventType.UPDATE_FRIENDS_DRINKS_RESPONSE) &&
                                        friendsDrinksEvent.getUpdateFriendsDrinksResponse().getResult().equals(Result.SUCCESS)) ||
                                (friendsDrinksEvent.getEventType().equals(EventType.DELETE_FRIENDS_DRINKS_RESPONSE) &&
                                        friendsDrinksEvent.getDeleteFriendsDrinksResponse().getResult().equals(Result.SUCCESS))
                )
                .selectKey((k, v) -> {
                    if (v.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_RESPONSE)) {
                        log.info("Got create response {}", v.getCreateFriendsDrinksResponse().getRequestId());
                        return v.getCreateFriendsDrinksResponse().getRequestId();
                    } else if (v.getEventType().equals(EventType.DELETE_FRIENDS_DRINKS_RESPONSE)) {
                        log.info("Got delete response {}", v.getDeleteFriendsDrinksResponse().getRequestId());
                        return v.getDeleteFriendsDrinksResponse().getRequestId();
                    } else if (v.getEventType().equals(EventType.UPDATE_FRIENDS_DRINKS_RESPONSE)) {
                        log.info("Got update response {}", v.getUpdateFriendsDrinksResponse().getRequestId());
                        return v.getUpdateFriendsDrinksResponse().getRequestId();
                    } else {
                        throw new RuntimeException(
                                String.format("Received unexpected event type %s", v.getEventType().toString()));
                    }
                });

        KStream<String, FriendsDrinksEvent> apiRequests = apiEvents
                .filter((k, v) -> v.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_REQUEST) ||
                        v.getEventType().equals(EventType.UPDATE_FRIENDS_DRINKS_REQUEST) ||
                        v.getEventType().equals(EventType.DELETE_FRIENDS_DRINKS_REQUEST))
                .selectKey(((k, v) -> {
                    if (v.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_REQUEST)) {
                        log.info("Got create request {}", v.getCreateFriendsDrinksRequest().getRequestId());
                        return v.getCreateFriendsDrinksRequest().getRequestId();
                    } else if (v.getEventType().equals(EventType.DELETE_FRIENDS_DRINKS_REQUEST)) {
                        log.info("Got delete request {}", v.getDeleteFriendsDrinksRequest().getRequestId());
                        return v.getDeleteFriendsDrinksRequest().getRequestId();
                    } else if (v.getEventType().equals(EventType.UPDATE_FRIENDS_DRINKS_REQUEST)) {
                        log.info("Got update request {}", v.getUpdateFriendsDrinksRequest().getRequestId());
                        return v.getUpdateFriendsDrinksRequest().getRequestId();
                    } else {
                        throw new RuntimeException(
                                String.format("Received unexpected event type %s", v.getEventType().toString()));
                    }
                }));

        successApiResponses.join(apiRequests,
                (l, r) -> {
                    if (r.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_REQUEST)) {
                        log.info("Got create join {}", r.getCreateFriendsDrinksRequest().getRequestId());
                        CreateFriendsDrinksRequest createFriendsDrinksRequest =
                                r.getCreateFriendsDrinksRequest();
                        CreatedFriendsDrinks friendsDrinks = CreatedFriendsDrinks
                                .newBuilder()
                                .setAdminUserId(createFriendsDrinksRequest.getAdminUserId())
                                .setName(createFriendsDrinksRequest.getName())
                                .setUserIds(createFriendsDrinksRequest.getUserIds().stream().collect(Collectors.toList()))
                                .setScheduleType(andrewgrant.friendsdrinks.avro.ScheduleType.valueOf(
                                        createFriendsDrinksRequest.getScheduleType().toString()))
                                .setCronSchedule(createFriendsDrinksRequest.getCronSchedule())
                                .build();
                        return andrewgrant.friendsdrinks.avro.FriendsDrinksEvent.newBuilder()
                                .setEventType(andrewgrant.friendsdrinks.avro.EventType.CREATED)
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setId(r.getCreateFriendsDrinksRequest().getFriendsDrinksId().getId())
                                        .build())
                                .setCreatedFriendsDrinks(friendsDrinks)
                                .build();
                    } else if (r.getEventType().equals(EventType.DELETE_FRIENDS_DRINKS_REQUEST)) {
                        log.info("Got delete join {}", r.getDeleteFriendsDrinksRequest().getRequestId());
                        return andrewgrant.friendsdrinks.avro.FriendsDrinksEvent
                                .newBuilder()
                                .setEventType(andrewgrant.friendsdrinks.avro.EventType.DELETED)
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setId(r.getDeleteFriendsDrinksRequest().getFriendsDrinksId().getId())
                                        .build())
                                .build();
                    } else if (r.getEventType().equals(EventType.UPDATE_FRIENDS_DRINKS_REQUEST)) {
                        log.info("Got update join {}", r.getDeleteFriendsDrinksRequest().getRequestId());
                        UpdateFriendsDrinksRequest updateFriendsDrinksRequest = r.getUpdateFriendsDrinksRequest();
                        UpdatedFriendsDrinks friendsDrinks = UpdatedFriendsDrinks
                                .newBuilder()
                                .setUpdateType(
                                        andrewgrant.friendsdrinks.avro.UpdateType.valueOf(
                                                updateFriendsDrinksRequest.getUpdateType().toString()))
                                .setAdminUserId(updateFriendsDrinksRequest.getAdminUserId())
                                .setName(updateFriendsDrinksRequest.getName())
                                .setUserIds(updateFriendsDrinksRequest.getUserIds().stream().collect(Collectors.toList()))
                                .setScheduleType(andrewgrant.friendsdrinks.avro.ScheduleType.valueOf(
                                        updateFriendsDrinksRequest.getScheduleType().toString()))
                                .setCronSchedule(updateFriendsDrinksRequest.getCronSchedule())
                                .build();
                        return andrewgrant.friendsdrinks.avro.FriendsDrinksEvent
                                .newBuilder()
                                .setEventType(andrewgrant.friendsdrinks.avro.EventType.UPDATED)
                                .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                        .newBuilder()
                                        .setId(r.getUpdateFriendsDrinksRequest().getFriendsDrinksId().getId())
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
                .to(envProps.getProperty("friendsdrinks_event.topic.name"),
                        Produced.with(avro.friendsDrinksIdSerde(), avro.friendsDrinksEventSerde()));


        KStream<andrewgrant.friendsdrinks.avro.FriendsDrinksId, andrewgrant.friendsdrinks.avro.FriendsDrinksState> friendsDrinksStateStream =
                builder.stream(envProps.getProperty("friendsdrinks_event.topic.name"),
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
                                        List<String> userIds;
                                        if (createdFriendsDrinks.getUserIds() != null) {
                                            userIds = createdFriendsDrinks.getUserIds().stream().collect(Collectors.toList());
                                        } else {
                                            userIds = new ArrayList<>();
                                        }
                                        FriendsDrinksState friendsDrinksState =
                                                friendsDrinksStateBuilder.setName(createdFriendsDrinks.getName())
                                                        .setFriendsDrinksId(andrewgrant.friendsdrinks.avro.FriendsDrinksId
                                                                .newBuilder()
                                                                .setId(newValue.getFriendsDrinksId().getId())
                                                                .build())
                                                        .setUserIds(userIds)
                                                        .setAdminUserId(createdFriendsDrinks.getAdminUserId())
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
                                        friendsDrinksStateBuilder = FriendsDrinksState.newBuilder();
                                        String name;
                                        if (updatedFriendsDrinks.getName() != null) {
                                            name = updatedFriendsDrinks.getName();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.Partial)) {
                                            name = aggValue.getFriendsDrinksState().getName();
                                        } else {
                                            name = null;
                                        }
                                        friendsDrinksStateBuilder.setName(name);
                                        List<String> userIds;
                                        if (updatedFriendsDrinks.getUserIds() != null) {
                                            userIds = updatedFriendsDrinks.getUserIds();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.Partial)) {
                                            userIds = aggValue.getFriendsDrinksState().getUserIds();
                                        } else {
                                            userIds = null;
                                        }
                                        friendsDrinksStateBuilder.setUserIds(userIds);

                                        String adminUserId;
                                        if (updatedFriendsDrinks.getAdminUserId() != null) {
                                            adminUserId = updatedFriendsDrinks.getAdminUserId();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.Partial)) {
                                            adminUserId = aggValue.getFriendsDrinksState().getAdminUserId();
                                        } else {
                                            adminUserId = null;
                                        }
                                        friendsDrinksStateBuilder.setAdminUserId(adminUserId);

                                        String cronSchedule;
                                        if (updatedFriendsDrinks.getCronSchedule() != null) {
                                            cronSchedule = updatedFriendsDrinks.getCronSchedule();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.Partial)) {
                                            cronSchedule = aggValue.getFriendsDrinksState().getCronSchedule();
                                        } else {
                                            cronSchedule = null;
                                        }
                                        friendsDrinksStateBuilder.setCronSchedule(cronSchedule);

                                        andrewgrant.friendsdrinks.avro.ScheduleType scheduleType;
                                        if (updatedFriendsDrinks.getScheduleType() != null) {
                                            scheduleType = updatedFriendsDrinks.getScheduleType();
                                        } else if (updateType.equals(andrewgrant.friendsdrinks.avro.UpdateType.Partial)) {
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
                                        as("internal_writer_service_friendsdrinks_state_tracker")
                                        .withKeySerde(avro.friendsDrinksIdSerde())
                                        .withValueSerde(avro.friendsDrinksStateAggregateSerde())
                        ).toStream().mapValues(value -> {
                    if (value == null) {
                        return null;
                    }
                    return value.getFriendsDrinksState();
                });

        friendsDrinksStateStream.to(envProps.getProperty("friendsdrinks_state.topic.name"),
                Produced.with(avro.friendsDrinksIdSerde(), avro.friendsDrinksStateSerde()));

        return builder.build();
    }

    public Properties buildStreamsProperties(Properties envProps) {
        Properties streamProps = new Properties();
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("friendsdrinks_writer.application.id"));
        streamProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
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
