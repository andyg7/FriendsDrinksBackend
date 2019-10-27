package andrewgrant.friendsdrinks.email;

import static org.junit.Assert.*;

import static andrewgrant.friendsdrinks.email.Config.TEST_CONFIG_FILE;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.test.ConsumerRecordFactory;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import andrewgrant.friendsdrinks.avro.Email;
import andrewgrant.friendsdrinks.avro.EmailEvent;
import andrewgrant.friendsdrinks.avro.User;
import andrewgrant.friendsdrinks.avro.UserEvent;
import andrewgrant.friendsdrinks.user.UserAvro;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

/**
 * Tests for validation.
 */
public class UserEmailValidatorServiceTest {

    /**
     * Integration test that requires kafka and schema registry to be running and so is ignored
     * by default.
     * @throws IOException
     */
    @Test
    public void testValidation() throws IOException {
        UserEmailValidatorService validatorService = new UserEmailValidatorService();
        Properties envProps = validatorService.loadEnvProperties(TEST_CONFIG_FILE);
        Topology topology = validatorService.buildTopology(envProps);

        Properties streamProps = validatorService.buildStreamsProperties(envProps);
        TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps);

        Serializer<String> stringSerializer = Serdes.String().serializer();

        SpecificAvroSerializer<User> userSerializer = UserAvro.serializer(envProps);
        SpecificAvroSerializer<Email> emailSerializer = EmailAvro.serializer(envProps);

        List<Email> emailInput = new ArrayList<>();
        String takenEmail = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        emailInput.add(Email.newBuilder()
                .setEmail(takenEmail)
                .setUserId(userId)
                .setEventType(EmailEvent.RESERVED)
                .build());

        ConsumerRecordFactory<String, Email> emailInputFactory =
                new ConsumerRecordFactory<>(stringSerializer, emailSerializer);

        final String emailTopic = envProps.getProperty("email.topic.name");
        for (Email email : emailInput) {
            testDriver.pipeInput(
                    emailInputFactory.create(emailTopic, email.getEmail(), email));
        }

        List<User> userInput = new ArrayList<>();
        String newRequestId = UUID.randomUUID().toString();
        String newUserId = UUID.randomUUID().toString();
        String newEmail = UUID.randomUUID().toString();
        // Valid request.
        userInput.add(User.newBuilder()
                .setRequestId(newRequestId)
                .setEmail(newEmail)
                .setEventType(UserEvent.REQUESTED)
                .setUserId(newUserId)
                .build());

        String newRequestId2 = UUID.randomUUID().toString();
        String newUserId2 = UUID.randomUUID().toString();
        // Invalid request for taken email.
        userInput.add(User.newBuilder()
                .setRequestId(newRequestId2)
                .setEmail(takenEmail)
                .setEventType(UserEvent.REQUESTED)
                .setUserId(newUserId2)
                .build());

        ConsumerRecordFactory<String, User> userInputFactory =
                new ConsumerRecordFactory<>(stringSerializer, userSerializer);
        final String emailRequestTopic = envProps.getProperty("email_request.topic.name");
        for (User user : userInput) {
            testDriver.pipeInput(
                    userInputFactory.create(emailRequestTopic, user.getEmail(), user));
        }

        final String userValidationTopic = envProps.getProperty("user_validation.topic.name");
        Deserializer<String> stringDeserializer = Serdes.String().deserializer();
        SpecificAvroDeserializer<User> userDeserializer = UserAvro.deserializer(envProps);

        List<User> userValidationOutput = new ArrayList<>();
        while (true) {
            ProducerRecord<String, User> userRecord = testDriver.readOutput(
                    userValidationTopic, stringDeserializer, userDeserializer);
            if (userRecord != null) {
                userValidationOutput.add(userRecord.value());
            } else {
                break;
            }
        }

        assertEquals(2, userValidationOutput.size());

        User validatedUser = userValidationOutput.get(0);
        assertEquals(newUserId, validatedUser.getUserId());
        assertEquals(UserEvent.VALIDATED, validatedUser.getEventType());
        assertEquals(null, validatedUser.getErrorCode());

        User rejectedUser = userValidationOutput.get(1);
        assertEquals(newUserId2, rejectedUser.getUserId());
        assertEquals(UserEvent.REJECTED, rejectedUser.getEventType());
        assertEquals(ErrorCode.EXISTS.toString(), rejectedUser.getErrorCode());
    }

}
