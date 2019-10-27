package andrewgrant.friendsdrinks.email;

import static org.junit.Assert.*;

import static andrewgrant.friendsdrinks.email.Config.TEST_CONFIG_FILE;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import andrewgrant.friendsdrinks.avro.*;
import andrewgrant.friendsdrinks.user.UserAvro;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

/**
 * Tests for EmailWriterService.
 */
public class EmailWriterServiceTest {


    /**
     * Integration test that requires kafka and schema registry to be running.
     * @throws IOException
     */
    @Test
    public void testValidate() throws IOException {
        EmailWriterService emailWriterService = new EmailWriterService();
        Properties envProps = emailWriterService.loadEnvProperties(TEST_CONFIG_FILE);
        Topology topology = emailWriterService.buildTopology(envProps);

        Properties streamProps = emailWriterService.buildStreamsProperties(envProps);
        TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps);

        SpecificAvroSerializer<UserId> userIdSerializer = UserAvro.userIdSerializer(envProps);
        SpecificAvroSerializer<User> userSerializer = UserAvro.userSerializer(envProps);

        ConsumerRecordFactory<UserId, User> inputFactory =
                new ConsumerRecordFactory<>(userIdSerializer, userSerializer);

        List<User> input = new ArrayList<>();
        input.add(
                User.newBuilder()
                        .setRequestId("1")
                        .setUserId(new UserId(UUID.randomUUID().toString()))
                        .setEmail(UUID.randomUUID().toString())
                        .setEventType(UserEvent.VALIDATED).build());
        input.add(
                User.newBuilder()
                        .setRequestId("2")
                        .setUserId(new UserId(UUID.randomUUID().toString()))
                        .setEmail(UUID.randomUUID().toString())
                        .setEventType(UserEvent.REJECTED).build());

        final String userTopic = envProps.getProperty("user.topic.name");
        for (User user : input) {
            testDriver.pipeInput(inputFactory.create(userTopic, user.getUserId(), user));
        }

        SpecificAvroDeserializer<EmailId> emailIdDeserializer = EmailAvro
                .emailIdDeserializer(envProps);
        SpecificAvroDeserializer<Email> emailDeserializer = EmailAvro
                .emailDeserializer(envProps);

        final String emailTopic = envProps.getProperty("email.topic.name");
        List<Email> output = new ArrayList<>();
        while (true) {
            ProducerRecord<EmailId, Email> emailRecord =
                    testDriver.readOutput(emailTopic, emailIdDeserializer, emailDeserializer);
            if (emailRecord != null) {
                output.add(emailRecord.value());
            } else {
                break;
            }
        }

        assertEquals(2, output.size());
        Email email1 = output.get(0);
        assertEquals(EmailEvent.RESERVED, email1.getEventType());
        Email email2 = output.get(1);
        assertEquals(EmailEvent.REJECTED, email2.getEventType());
    }

}
