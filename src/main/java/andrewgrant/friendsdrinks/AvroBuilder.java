package andrewgrant.friendsdrinks;

import java.util.HashMap;
import java.util.Map;

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;

/**
 * Contains (de)serialization logic for friends drinks avros.
 */
public class AvroBuilder {

    private SchemaRegistryClient registryClient;
    private String registryUrl;

    public AvroBuilder(String registryUrl) {
        this.registryUrl = registryUrl;
        registryClient = null;
    }

    public AvroBuilder(String registryUrl, SchemaRegistryClient registryClient) {
        this.registryUrl = registryUrl;
        this.registryClient = registryClient;
    }



    public SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksState> friendsDrinksStateSerde() {
        SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksState> serde;
        if (registryClient != null) {
            serde = new SpecificAvroSerde<>(registryClient);
        } else {
            serde = new SpecificAvroSerde<>();
        }
        Map<String, String> config = new HashMap<>();
        config.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
        serde.configure(config, false);
        return serde;
    }

    public SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksEvent> friendsDrinksEventSerde() {
        SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksEvent> serde;
        if (registryClient != null) {
            serde = new SpecificAvroSerde<>(registryClient);
        } else {
            serde = new SpecificAvroSerde<>();
        }
        Map<String, String> config = new HashMap<>();
        config.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
        serde.configure(config, false);
        return serde;
    }

    public SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksId> friendsDrinksIdSerde() {
        SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksId> serde;
        if (registryClient != null) {
            serde = new SpecificAvroSerde<>(registryClient);
        } else {
            serde = new SpecificAvroSerde<>();
        }
        Map<String, String> config = new HashMap<>();
        config.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
        serde.configure(config, true);
        return serde;
    }


    public SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksStateAggregate> friendsDrinksStateAggregateSerde() {
        SpecificAvroSerde<andrewgrant.friendsdrinks.avro.FriendsDrinksStateAggregate> serde;
        if (registryClient != null) {
            serde = new SpecificAvroSerde<>(registryClient);
        } else {
            serde = new SpecificAvroSerde<>();
        }
        Map<String, String> config = new HashMap<>();
        config.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);
        serde.configure(config, false);
        return serde;
    }


}
