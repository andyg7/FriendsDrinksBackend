package andrewgrant.friendsdrinks.email;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import andrewgrant.friendsdrinks.avro.Email;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer;

/**
 * Factory class for building an Email Avro serializer and deserializer.
 */
public class EmailAvroSerializer {

    public static SpecificAvroSerializer<Email> buildSerializer(Properties envProps) {
        SpecificAvroSerializer<Email> serializer = new SpecificAvroSerializer<>();
        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", envProps.getProperty("schema.registry.url"));
        serializer.configure(config, false);
        return serializer;
    }

    public static SpecificAvroDeserializer<Email> buildDeserializer(Properties envProps) {
        SpecificAvroDeserializer<Email> deserializer = new SpecificAvroDeserializer<>();
        Map<String, String> config = new HashMap<>();
        config.put("schema.registry.url", envProps.getProperty("schema.registry.url"));
        deserializer.configure(config, false);
        return deserializer;
    }

}