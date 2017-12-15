# Kafka Streams learning 

# Quick summary
This project is for Kafka Streaming POC/Demo and learning purpose. Sample of how to use Avro Specific Records with Kafka Streaming. 

# Summary of set up.
Based on Confluent Open Source 4.0.0 with Java 1.8. Maven based JAVA project using InteliJ.

# Configuration / Setup for Demo.
Using Confluent CLI **confluent start** starts zookeeper, kafka, schema-registry, rest-proxy and kafka connect. After that create 4 topics as below.

```
confluent start

kafka-topics --zookeeper 127.0.0.1:2181 --create --topic meter_usage --partitions 1 --replication-factor 1
kafka-topics --zookeeper 127.0.0.1:2181 --create --topic account_snapshot --partitions 1 --replication-factor 1
kafka-topics --zookeeper 127.0.0.1:2181 --create --topic temp001_to_account_snapshot --partitions 1 --replication-factor 1
kafka-topics --zookeeper 127.0.0.1:2181 --create --topic usage_alert --partitions 1 --replication-factor 1
```
Project is divided into two Stream Processing Apps.
```
1. UsageIn and
2. ProcessUsage
```

# Story or Functionality 

#UsageIn : 

Electric Meters from field are sending Usage or Consumption after every certain interval like 15 minutes. This usage is going to topic meter_usage.

Avro Schema for topic meter_usage:

```
{ "namespace": "avro",  
     "type": "record",  
     "name": "meter_usage",
     "fields": [  {"name": "premise",     "type": "string",  "doc": "Premise"},
                  {"name": "meter",       "type": "string",  "doc": "Meter Number"},
                  {"name": "usage",       "type": "int",     "doc": "Usage from field."}  ]}
```
               
Second Topic is account_snapshot. Master data of Account is streamed from Database to this topic.

```
{ "namespace": "avro",  
  "type": "record",  
  "name": "account_snapshot",  
  "fields": [ {"name": "user_name",              "type": "string",                    "doc": "Web User Name"},
              {"name": "email_id",               "type": "string",                    "doc": "Email ID"},
              {"name": "customer",               "type": "string",                    "doc": "Customer code"},
              {"name": "premise",                "type": "string",                    "doc": "Premise code"},
              {"name": "company",                "type": "string",                    "doc": "Company South or North"},
              {"name": "meter",                  "type": "string",                    "doc": "Meter Number"},
              {"name": "total_usage",            "type": "int",                       "doc": "Total unbilled usage on this meter."},
              {"name": "usage_alerted_at_100",   "type": "string",  "default": "N",   "doc": "Usage alert once usage crossed 100"},
              {"name": "usage_alerted_at_200",   "type": "string",  "default": "N",   "doc": "Usage alert once usage crossed 200"}
              ]}
```

Meter usage comming from topic meter_usage is accumulated to field total_usage in here. This is acheved by joining Kstream from topic meter_usage and KTable from topic account_snapshot. Summarised usage record is written to one intermediate temporary topic temp001_to_account_snapshot.

During the stream processing there that check for wheather the usage has crossed 100 or 200 units and accordingly Usage Alert record is generated and sent to another topic usage_alert that has below schema defination.

```
{ "namespace": "avro",
  "type": "record",
  "name": "usage_alert",
  "fields": [ {"name": "user_name",      "type": "string",     "doc": "Web User Name"},
              {"name": "email_id",       "type": "string",     "doc": "Email ID"},
              {"name": "usage_alert",    "type": "string",     "doc": "Usage Alert description"}
              ]}
```
#ProcessUsage : 

This app writes summarised results from temp001_to_account_snapshot to the main account snapshot topic account_snapshot. We need this in seperate app because in above app **UsageIn** we are reading account_snapshot as KTable. Reading as Ktable and writing to same topic create cyclic reference and create issues.

# Rest Proxy as Data Producer.

I have used below statements to produce / simulate data to topics account_snapshot and meter_usage.

#Sending Account Master data to account_snapshot.

Here in first POST full key and value AVRO schema is specified. 

```
curl -X POST -H "Content-Type: application/vnd.kafka.avro.v2+json" \
-H "Accept: application/vnd.kafka.v2+json" \
--data '{"key_schema":"{\"namespace\": \"avro\",\"name\":\"premise_key\",\"type\":\"string\"}",
         "value_schema":"{\"type\":\"record\",\"name\":\"account_snapshot\",\"namespace\":\"avro\",\"fields\":[{\"name\":\"user_name\",\"type\":\"string\",\"doc\":\"Web User Name\"},{\"name\":\"email_id\",\"type\":\"string\",\"doc\":\"Email ID\"},{\"name\":\"customer\",\"type\":\"string\",\"doc\":\"Customer code\"},{\"name\":\"premise\",\"type\":\"string\",\"doc\":\"Premise code\"},{\"name\":\"company\",\"type\":\"string\",\"doc\":\"Company South or North\"},{\"name\":\"meter\",\"type\":\"string\",\"doc\":\"Meter Number\"},{\"name\":\"total_usage\",\"type\":\"int\",\"doc\":\"Total unbilled usage on this meter.\"},{\"name\":\"usage_alerted_at_100\",\"type\":\"string\",\"doc\":\"Usage alert created once usage crossed 100\",\"default\":\"N\"},{\"name\":\"usage_alerted_at_200\",\"type\":\"string\",\"doc\":\"Usage alert created once usage crossed 200\",\"default\":\"N\"}]}",
"records": [{"key":"1000001","value":{"user_name":"vsindhu","email_id":"vsindhu@temp.com","customer":"10001","premise":"1000001","company":"30","meter":"M001","total_usage":1,"usage_alerted_at_100":"N","usage_alerted_at_200":"N"} } ] }'\
"http://localhost:8082/topics/account_snapshot"
```

If schemas are created in advance and we know schema ID's then we dont need to specify schema. Only have to specify schema ID's.

```
curl -X POST -H "Content-Type: application/vnd.kafka.avro.v2+json" \
-H "Accept: application/vnd.kafka.v2+json" \
--data '{"key_schema_id": "1",
         "value_schema_id": "2",
         "records": [{"key":"1000002","value":{"user_name":"nolson","email_id":"nolson@temp.com","customer":"10002","premise":"1000002","company":"10","meter":"M002","total_usage":2,"usage_alerted_at_100":"N","usage_alerted_at_200":"N"}}]}'\
         "http://localhost:8082/topics/account_snapshot"
```

Here we are pushing myltiple reords in single POST with schema ID's

```
curl -X POST -H "Content-Type: application/vnd.kafka.avro.v2+json" \
   -H "Accept: application/vnd.kafka.v2+json" \
   --data '{"key_schema_id": "1",
   "value_schema_id": "2",
   "records": [
   {"key":"1000003","value":{"user_name":"dtheiss",   "email_id":"dtheiss@temp.com",   "customer":"10003","premise":"1000003","company":"30","meter":"M003","total_usage":3,"usage_alerted_at_100":"N","usage_alerted_at_200":"N"}},
   {"key":"1000004","value":{"user_name":"dfrohlich", "email_id":"dfrohlich@temp.com", "customer":"10004","premise":"1000004","company":"30","meter":"M004","total_usage":4,"usage_alerted_at_100":"N","usage_alerted_at_200":"N"}},
   {"key":"1000005","value":{"user_name":"bphilippus","email_id":"bphilippus@temp.com","customer":"10005","premise":"1000005","company":"30","meter":"M005","total_usage":5,"usage_alerted_at_100":"N","usage_alerted_at_200":"N"}}                          ] }'\
   "http://localhost:8082/topics/account_snapshot"
```

#Sending Usage data to meter_usage.

Here in first POST full key and value AVRO schema is specified. 

```
 curl -X POST -H "Content-Type: application/vnd.kafka.avro.v2+json" \
 -H "Accept: application/vnd.kafka.v2+json" \
 --data '{"key_schema":"{\"namespace\": \"avro\",\"name\":\"premise_key\",\"type\":\"string\"}",
          "value_schema":"{\"type\":\"record\",\"name\":\"meter_usage\",\"namespace\":\"avro\",\"fields\":[{\"name\":\"premise\",\"type\":\"string\",\"doc\":\"Premise\"},{\"name\":\"meter\",\"type\":\"string\",\"doc\":\"Meter Number\"},{\"name\":\"usage\",\"type\":\"int\",\"doc\":\"Usage from field.\"}]}",
          "records": [{"key":"1000001","value":{"premise":"1000001","meter":"M001","usage":50} } ] }'\
          "http://localhost:8082/topics/meter_usage"
```
          
Pushing data with schema ID's

```
curl -X POST -H "Content-Type: application/vnd.kafka.avro.v2+json" \
-H "Accept: application/vnd.kafka.v2+json" \
--data '{"key_schema_id":"1",
"value_schema_id":"3",
"records": [{"key":"1000002","value":{"premise":"1000002","meter":"M002","usage":20} } ] }'\
"http://localhost:8082/topics/meter_usage"
```

Pushing multiple records with schema ID's

```
curl -X POST -H "Content-Type: application/vnd.kafka.avro.v2+json" \
-H "Accept: application/vnd.kafka.v2+json" \
--data '{"key_schema_id":"1",
"value_schema_id":"3",
"records": [
{"key":"1000003","value":{"premise":"1000003","meter":"M003","usage":3} },                           
{"key":"1000004","value":{"premise":"1000004","meter":"M004","usage":4} },                           
{"key":"1000005","value":{"premise":"1000005","meter":"M005","usage":5} } ] }'\
"http://localhost:8082/topics/meter_usage"      
```
