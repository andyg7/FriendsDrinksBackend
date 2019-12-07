package andrewgrant.friendsdrinks.fraud;

import static andrewgrant.friendsdrinks.env.Properties.loadEnvProperties;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import andrewgrant.friendsdrinks.avro.*;
import andrewgrant.friendsdrinks.user.AvroSerdeFactory;

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;


/**
 * Validates user request.
 */
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();

        final String userTopic = envProps.getProperty("user.topic.name");
        final String fraudTmpTopic = envProps.getProperty("fraud_tmp.topic.name");
        KStream<UserId, UserEvent> users = builder
                .stream(userTopic,
                        Consumed.with(AvroSerdeFactory.buildUserId(envProps),
                                AvroSerdeFactory.buildUserEvent(envProps)));

        users.filter(((key, value) -> value.getEventType().equals(EventType.REQUESTED)))
                .groupByKey(
                        Grouped.with(AvroSerdeFactory.buildUserId(envProps),
                                AvroSerdeFactory.buildUserEvent(envProps)))
                .windowedBy(SessionWindows.with(Duration.ofMinutes(1)))
                .aggregate(
                        () -> 0L,
                        ((key, value, aggregate) -> 1 + aggregate),
                        ((aggKey, aggOne, aggTwo) -> aggOne + aggTwo),
                        Materialized.with(AvroSerdeFactory.buildUserId(envProps), Serdes.Long())
                )
                // Get rid of windowed key.
                .toStream(((key, value) -> key.key()))
                .to(fraudTmpTopic,
                        Produced.with(AvroSerdeFactory.buildUserId(envProps),
                                Serdes.Long()));

        KTable<UserId, Long> userRequestCount = builder.table(fraudTmpTopic,
                Consumed.with(AvroSerdeFactory.buildUserId(envProps),
                        Serdes.Long()));

        KStream<UserId, FraudTracker> trackedUsers = users.filter(((key, value) ->
                value.getEventType().equals(EventType.REQUESTED)))
                .leftJoin(userRequestCount,
                        FraudTracker::new,
                        Joined.with(AvroSerdeFactory.buildUserId(envProps),
                        AvroSerdeFactory.buildUserEvent(envProps),
                        Serdes.Long()));

        KStream<UserId, FraudTracker>[] trackedUserResults = trackedUsers.branch(
                (key, tracker) -> tracker.getCount() == null ||
                        tracker.getCount() < 10,
                (key, value) -> true
        );

        final String userValidationsTopic = envProps.getProperty("user_validation.topic.name");

        // Validated requests.
        trackedUserResults[0].mapValues(value -> value.getUserEvent())
                .mapValues(value -> {
                    UserValidated userValidated = UserValidated.newBuilder()
                            .setEmail(value.getUserRequest().getEmail())
                            .setUserId(value.getUserRequest().getUserId())
                            .setRequestId(value.getUserRequest().getRequestId())
                            .build();
                    return UserEvent.newBuilder()
                            .setEventType(EventType.VALIDATED)
                            .setUserValidated(userValidated)
                            .build();
                })
                .to(userValidationsTopic, Produced.with(
                        AvroSerdeFactory.buildUserId(envProps),
                        AvroSerdeFactory.buildUserEvent(envProps)));

        // Rejected requests.
        trackedUserResults[1].mapValues(value -> value.getUserEvent())
                .mapValues(value -> {
                    UserRejected userRejected = UserRejected.newBuilder()
                            .setEmail(value.getUserRequest().getEmail())
                            .setUserId(value.getUserRequest().getUserId())
                            .setRequestId(value.getUserRequest().getRequestId())
                            .setErrorCode(ErrorCode.DOS.toString())
                            .build();
                    return UserEvent.newBuilder()
                            .setEventType(EventType.REJECTED)
                            .setUserRejected(userRejected)
                            .build();
                })
                .to(userValidationsTopic, Produced.with(
                        AvroSerdeFactory.buildUserId(envProps),
                        AvroSerdeFactory.buildUserEvent(envProps)));

        return builder.build();
    }

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG,
                envProps.getProperty("fraud_validation_application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,
                envProps.getProperty("bootstrap.servers"));
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));
        return props;
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: " +
                    "the path to an environment configuration file.");
        }

        ValidationService validationService =
                new ValidationService();
        Properties envProps = loadEnvProperties(args[0]);
        Topology topology = validationService.buildTopology(envProps);
        log.debug("Built stream");

        Properties streamProps = validationService.buildStreamsProperties(envProps);
        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}

