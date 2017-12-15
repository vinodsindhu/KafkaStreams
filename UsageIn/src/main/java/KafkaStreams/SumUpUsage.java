package KafkaStreams;

import avro.account_snapshot;
import avro.meter_usage;
import avro.usage_alert;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;

import java.util.Properties;

public class SumUpUsage {

//    private org.slf4j.Logger log = LoggerFactory.getLogger(KafkaStreams.SumUpUsage.class.getSimpleName());

    static account_snapshot buildAccountSnapShot(meter_usage Usage_Obj, account_snapshot SnapShot_Obj) {


        account_snapshot.Builder builder_obj = account_snapshot.newBuilder();

        builder_obj.setUserName(SnapShot_Obj.getUserName());
        builder_obj.setEmailId(SnapShot_Obj.getEmailId());
        builder_obj.setCustomer(SnapShot_Obj.getCustomer());
        builder_obj.setPremise(SnapShot_Obj.getPremise());
        builder_obj.setCompany(SnapShot_Obj.getCompany());
        builder_obj.setMeter(SnapShot_Obj.getMeter());
        builder_obj.setTotalUsage(SnapShot_Obj.getTotalUsage() + Usage_Obj.getUsage());

        builder_obj.setUsageAlertedAt100(SnapShot_Obj.getUsageAlertedAt100());
        builder_obj.setUsageAlertedAt200(SnapShot_Obj.getUsageAlertedAt200());

        //--------------------------
        if ((builder_obj.getTotalUsage() >= 100) && (builder_obj.getUsageAlertedAt100().toString().equals("Y") ))
            builder_obj.setUsageAlertedAt100("S");

        if ((builder_obj.getTotalUsage() >= 100) && (builder_obj.getUsageAlertedAt100().toString().equals("N") ))
            builder_obj.setUsageAlertedAt100("Y");
        //--------------------------
        if ((builder_obj.getTotalUsage() >= 200) && (builder_obj.getUsageAlertedAt200().toString().equals("Y") ))
            builder_obj.setUsageAlertedAt200("S");

        if ((builder_obj.getTotalUsage() >= 200) && (builder_obj.getUsageAlertedAt200().toString().equals("N") ))
            builder_obj.setUsageAlertedAt200("Y");
        //--------------------------

        account_snapshot final_snapshot_obj = builder_obj.build();

        /* Initializing object with constructor is better performing than builder
           because builder creates copy of record before it is written.
           Builder is better on customizations and

        account_snapshot  final_snapshot_obj2 =
                new account_snapshot(SnapShot_Obj.getUserName(),
                                     SnapShot_Obj.getEmailId(),
                                     SnapShot_Obj.getCustomer(),
                                     SnapShot_Obj.getPremise(),
                                     SnapShot_Obj.getCompany(),
                                     SnapShot_Obj.getMeter(),
                                     (SnapShot_Obj.getTotalUsage() + Usage_Obj.getUsage()),
                                    "N",
                                    "N");
       */

        return final_snapshot_obj;
    }


    static usage_alert buildUsageAlert(account_snapshot SnapShot_Obj) {

        usage_alert.Builder builder_obj = usage_alert.newBuilder();

        builder_obj.setUserName(SnapShot_Obj.getUserName());
        builder_obj.setEmailId(SnapShot_Obj.getEmailId());
        builder_obj.setUsageAlert("Usage crossed 100. Current Usage is : " + SnapShot_Obj.getTotalUsage());

        return builder_obj.build();
    }


    public static void main(String[] args) {

        Properties config = new Properties();

        config.put(StreamsConfig.APPLICATION_ID_CONFIG,"usage-in");
        config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG,"localhost:9092");
        config.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        config.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        config.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG,StreamsConfig.EXACTLY_ONCE);

        config.put("schema.registry.url", "http://localhost:8081");

        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");


        StreamsBuilder builder = new StreamsBuilder();

        KStream<String, meter_usage> usage_instream = builder.stream("meter_usage");

        KTable<String, account_snapshot> account_table = builder.table("account_snapshot");

//        KStream<String,account_snapshot> Final_Stream =
//                usage_instream.leftJoin(account_table,(meter_usage value1, account_snapshot value2) -> buildAccountSnapShot(value1,value2));

        KStream<String,account_snapshot> Final_Stream =
                usage_instream.leftJoin(account_table, SumUpUsage::buildAccountSnapShot);

        Final_Stream.through("temp001_to_account_snapshot")
               .peek((key,value) -> System.out.println("This is from peek 1 : " + value ))
                 .filter((key,value) -> (value.getUsageAlertedAt100().toString().equals("Y") || value.getUsageAlertedAt200().toString().equals("Y") ))
               .peek((key,value) -> System.out.println("This is from peek 2 : " + value))
//               .mapValues((value -> buildUsageAlert(value)));
                 .mapValues(SumUpUsage::buildUsageAlert).to("usage_alert");



        org.apache.kafka.streams.KafkaStreams  streams = new org.apache.kafka.streams.KafkaStreams(builder.build(),config);
        streams.start();

        System.out.println(streams.allMetadata());

        //print the topology
        System.out.println(streams.allMetadata());

        //shutdown hook to correctly close the streams application
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

    }
}
