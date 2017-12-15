package KafkaStreams;

import avro.account_snapshot;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;

import java.util.Properties;


public class ProcessUsage {

    public static void main(String[] args) {

        Double pre_usage = 0.0 ;

        Properties config = new Properties();

        config.put(StreamsConfig.APPLICATION_ID_CONFIG,"streams-app-update-usage");

        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");

        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);

        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG,StreamsConfig.EXACTLY_ONCE);

        config.put("schema.registry.url", "http://localhost:8081");

        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, account_snapshot> account_snapshot_tmp = builder.stream("temp001_to_account_snapshot");

        account_snapshot_tmp.to("account_snapshot");


        org.apache.kafka.streams.KafkaStreams streams = new org.apache.kafka.streams.KafkaStreams(builder.build(), config);

        streams.start();

        //print the topology
        System.out.println(streams.allMetadata());

        //shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));


    }


}
