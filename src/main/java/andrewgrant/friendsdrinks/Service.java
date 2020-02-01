package andrewgrant.friendsdrinks;

import static andrewgrant.friendsdrinks.env.Properties.load;

import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Main FriendsDrinks service.
 */
public class Service {

    private Topology buildTopology() {
        StreamsBuilder builder = new StreamsBuilder();
        return builder.build();
    }

    public static void main(String[] args) throws IOException {
        Properties envProperties = load(args[0]);
        Service service = new Service();
        Topology topology = service.buildTopology();
        Properties streamProps = envProperties;
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
