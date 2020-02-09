package andrewgrant.friendsdrinks;

import static andrewgrant.friendsdrinks.env.Properties.load;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import andrewgrant.friendsdrinks.avro.*;
import andrewgrant.friendsdrinks.user.UserAvro;
import andrewgrant.friendsdrinks.user.avro.UserId;

/**
 * Main FriendsDrinks service.
 */
public class Service {

    public Topology buildTopology(Properties envProps, FriendsDrinksAvro friendsDrinksAvro, UserAvro userAvro) {
        StreamsBuilder builder = new StreamsBuilder();
        final String friendsDrinksTopicName = envProps.getProperty("friendsdrinks.topic.name");

        KStream<UserId, FriendsDrinksEvent> friendsDrinks = builder.stream(friendsDrinksTopicName,
                Consumed.with(userAvro.userIdSerde(), friendsDrinksAvro.createFriendsDrinksEventSerde()));

        KTable<UserId, Long> friendsDrinksCount = friendsDrinks.filter(((s, friendsDrinksEvent) ->
                friendsDrinksEvent.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_RESPONSE) &&
                        friendsDrinksEvent.getCreateFriendsDrinksResponse().getResult().equals(Result.SUCCESS)))
                .mapValues(friendsDrinksEvent -> friendsDrinksEvent.getCreateFriendsDrinksResponse())
                .groupByKey()
                .count();

        KStream<UserId, CreateFriendsDrinksRequest> requests = friendsDrinks
                .filter(((s, friendsDrinksEvent) -> friendsDrinksEvent.getEventType().equals(EventType.CREATE_FRIENDS_DRINKS_REQUEST)))
                .mapValues(friendsDrinksEvent -> friendsDrinksEvent.getCreateFriendsDrinksRequest());

        KStream<UserId, FriendsDrinksEvent> responses = requests.leftJoin(friendsDrinksCount,
                (request, count) -> {
                    CreateFriendsDrinksResponse.Builder response = CreateFriendsDrinksResponse.newBuilder();
                    response.setRequestId(request.getRequestId());
                    if (count == null) {
                        response.setResult(Result.SUCCESS);
                    } else if (count < 5) {
                        response.setResult(Result.SUCCESS);
                    } else {
                        response.setResult(Result.FAIL);
                    }
                    FriendsDrinksEvent event = FriendsDrinksEvent.newBuilder()
                            .setEventType(EventType.CREATE_FRIENDS_DRINKS_RESPONSE)
                            .setCreateFriendsDrinksResponse(response.build())
                            .build();
                    return event;
                },
                Joined.with(userAvro.userIdSerde(), friendsDrinksAvro.createFriendsDrinksRequestSerde(), Serdes.Long()));

        responses.to(friendsDrinksTopicName,
                Produced.with(userAvro.userIdSerde(), friendsDrinksAvro.createFriendsDrinksEventSerde()));
        return builder.build();
    }

    public Properties buildStreamProperties(Properties envProps) {
        Properties streamProps = new Properties();
        streamProps.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("friendsdrinks.application.id"));
        streamProps.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        return streamProps;
    }

    public static void main(String[] args) throws IOException {
        Properties envProps = load(args[0]);
        Service service = new Service();
        Topology topology = service.buildTopology(envProps,
                new FriendsDrinksAvro(envProps.getProperty("schema.registry.url")),
                new UserAvro(envProps.getProperty("schema.registry.url")));
        Properties streamProps = service.buildStreamProperties(envProps);
        KafkaStreams streams = new KafkaStreams(topology, streamProps);

        final CountDownLatch latch = new CountDownLatch(1);
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
    }
}
