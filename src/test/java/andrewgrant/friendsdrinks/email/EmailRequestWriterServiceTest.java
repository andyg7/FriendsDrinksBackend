package andrewgrant.friendsdrinks.email;

import static org.junit.Assert.*;

import static andrewgrant.friendsdrinks.email.Config.TEST_CONFIG_FILE;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import andrewgrant.friendsdrinks.avro.EmailId;
import andrewgrant.friendsdrinks.avro.User;
import andrewgrant.friendsdrinks.avro.UserEvent;
import andrewgrant.friendsdrinks.avro.UserId;
import andrewgrant.friendsdrinks.user.UserAvro;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

/**
 * Tests EmailRequestWriterService.
 */
public class EmailRequestWriterServiceTest {

    /**
     * Integration test for EmailRequestWriterService.
     */
    @Test
    public void testWrite() throws IOException {
        EmailRequestWriterService emailRequestWriterService =
                new EmailRequestWriterService();
        Properties envProps = emailRequestWriterService.loadEnvProperties(TEST_CONFIG_FILE);
        Topology topology = emailRequestWriterService.buildTopology(envProps);

        Properties streamProps = emailRequestWriterService
                .buildStreamsProperties(envProps);
        TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps);

        SpecificAvroSerializer<UserId> userIdSerializer = UserAvro.userIdSerializer(envProps);
        SpecificAvroSerializer<User> userSerializer = UserAvro.userSerializer(envProps);

        ConsumerRecordFactory<UserId, User> inputFactory =
                new ConsumerRecordFactory<>(userIdSerializer, userSerializer);

        List<User> input = new ArrayList<>();
        String requestId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        String email = UUID.randomUUID().toString();
        input.add(
                User.newBuilder()
                        .setRequestId(requestId)
                        .setUserId(new UserId(userId))
                        .setEmail(email)
                        .setEventType(UserEvent.REQUESTED).build());

        final String userTopic = envProps.getProperty("user.topic.name");
        for (User user : input) {
            testDriver.pipeInput(inputFactory
                    .create(userTopic, user.getUserId(), user));
        }

        final String emailRequestTopic = envProps.getProperty("email_request.topic.name");
        SpecificAvroDeserializer<EmailId> emailIdDeserializer =
                EmailAvro.emailIdDeserializer(envProps);
        SpecificAvroDeserializer<User> userDeserializer = UserAvro.userDeserializer(envProps);

        List<User> output = new ArrayList<>();
        while (true) {
            ProducerRecord<EmailId, User> userRecord =
                    testDriver.readOutput(emailRequestTopic, emailIdDeserializer, userDeserializer);
            if (userRecord != null) {
                output.add(userRecord.value());
            } else {
                break;
            }
        }

        assertEquals(1, output.size());
        assertEquals(input.get(0).getEmail(), output.get(0).getEmail());
    }

}
