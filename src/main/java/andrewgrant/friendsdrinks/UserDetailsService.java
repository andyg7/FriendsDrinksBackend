package andrewgrant.friendsdrinks;

import andrewgrant.friendsdrinks.avro.Email;
import andrewgrant.friendsdrinks.avro.User;
import andrewgrant.friendsdrinks.avro.EmailEvent;
import andrewgrant.friendsdrinks.avro.UserEvent;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class UserDetailsService {
    private static final Logger log = LoggerFactory.getLogger(UserDetailsService.class);

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));

        return props;
    }

    public static String USER_TOPIC;
    public static String USER_VALIDATION_TOPIC;
    public static String EMAIL_TOPIC;

    private static final String PENDING_EMAILS_STORE_NAME = "pending_emails_store_name";

    public Topology buildTopology(Properties envProps) {
        final StreamsBuilder builder = new StreamsBuilder();
        USER_TOPIC = envProps.getProperty("user.topic.name");
        USER_VALIDATION_TOPIC = envProps.getProperty("user_validation.topic.name");
        EMAIL_TOPIC = envProps.getProperty("email.topic.name");


        final StoreBuilder pendingEmails = Stores
                .keyValueStoreBuilder(Stores.persistentKeyValueStore(PENDING_EMAILS_STORE_NAME),
                        Serdes.String(), Serdes.String())
                .withLoggingEnabled(new HashMap<>());
        builder.addStateStore(pendingEmails);

        KStream<String, User> userIdKStream = builder.stream(USER_TOPIC);

        KTable<String, Email> emailKTable = builder.table(EMAIL_TOPIC,
                Consumed.with(Serdes.String(), emailAvroSerde(envProps)));

        // Filter by requests.
        KStream<String, User> userRequestsKStream = userIdKStream.filter(((key, value) -> value.getEventType()
                .equals(UserEvent.REQUESTED)));
        // Key by email so we can join on email.
        KStream<String, User> userKStream = userRequestsKStream.selectKey(((key, value) -> value.getEmail()));

        KStream<String, EmailRequest> userAndEmail = userKStream.leftJoin(emailKTable, EmailRequest::new,
                Joined.with(Serdes.String(), userAvroSerde(envProps), emailAvroSerde(envProps)));

        KStream<String, User> validatedUser = userAndEmail.transform(EmailValidator::new, PENDING_EMAILS_STORE_NAME);
        validatedUser.to(USER_VALIDATION_TOPIC, Produced.with(Serdes.String(), userAvroSerde(envProps)));

        return builder.build();
    }

    private SpecificAvroSerde<Email> emailAvroSerde(Properties envProps) {
        SpecificAvroSerde<Email> emailAvroSerde = new SpecificAvroSerde<>();

        final HashMap<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));

        emailAvroSerde.configure(serdeConfig, false);
        return emailAvroSerde;
    }

    private SpecificAvroSerde<User> userAvroSerde(Properties envProps) {
        SpecificAvroSerde<User> userAvroSerde = new SpecificAvroSerde<>();

        final HashMap<String, String> serdeConfig = new HashMap<>();
        serdeConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
                envProps.getProperty("schema.registry.url"));

        userAvroSerde.configure(serdeConfig, false);
        return userAvroSerde;
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        UserDetailsService userDetailsService = new UserDetailsService();
        Properties envProps = userDetailsService.loadEnvProperties(args[0]);
        Properties streamProps = userDetailsService.buildStreamsProperties(envProps);
        Topology topology = userDetailsService.buildTopology(envProps);
        log.debug("Built stream");

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

    private static class EmailValidator implements
            Transformer<String, EmailRequest, KeyValue<String, User>> {

        private KeyValueStore<String, String> pendingEmailsStore;

        @Override
        @SuppressWarnings("unchecked")
        public void init(final ProcessorContext context) {
            pendingEmailsStore = (KeyValueStore<String, String>) context
                    .getStateStore(PENDING_EMAILS_STORE_NAME);
        }

        @Override
        public KeyValue<String, User> transform(final String str,
                                                final EmailRequest emailRequest) {
            Email email = emailRequest.getEmail();
            if (email == null) {
                User user = emailRequest.getUser();
                user.setEventType(UserEvent.VALIDATED);
                return new KeyValue<>(str, user);
            } else if (email.equals(EmailEvent.RECLAIMED)) {
                User user = emailRequest.getUser();
                user.setEventType(UserEvent.VALIDATED);
                return new KeyValue<>(str, user);
            } else if (pendingEmailsStore.get(email.getEmail()) != null) {
                User user = emailRequest.getUser();
                user.setEventType(UserEvent.REJECTED);
                return new KeyValue<>(str, user);
            }
            throw new RuntimeException();
        }

        @Override
        public void close() {
        }
    }

}
