/*
 * Copyright 2015 Data Artisans GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dataartisans.flink.example.eventpattern.kafka

import java.util.Properties

import com.dataartisans.flink.example.eventpattern.{StandaloneGeneratorBase, Event}
import kafka.producer.{KeyedMessage, Producer, ProducerConfig}
import kafka.serializer.DefaultEncoder
import kafka.utils.VerifiableProperties
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.connectors.kafka.api.config.PartitionerWrapper
import org.apache.flink.util.Collector

/**
 * A generator that pushes the data into Kafka.
 */
object KafkaGenerator extends StandaloneGeneratorBase {

  val TOPIC = "test"

  def main(args: Array[String]): Unit = {
    val pt = ParameterTool.fromArgs(args)

    val numPartitions = 1 //args(0).toInt
    val collectors = new Array[KafkaCollector](numPartitions)

    // create the generator threads
    for (i <- 0 until collectors.length) {
      collectors(i) = new KafkaCollector(i, pt)
    }

    runGenerator(collectors)
  }
}
class AllToOne(props: VerifiableProperties) extends kafka.producer.Partitioner {
  override def partition(key: Any, numPartitions: Int): Int = 68
}

class KafkaCollector(private[this] val partition: Int, private val pt: ParameterTool) extends Collector[Event] {

  // create Kafka producer
  val properties = new Properties()
  properties.put("metadata.broker.list", "localhost:9092")
  properties.put("serializer.class", classOf[DefaultEncoder].getCanonicalName)
  properties.put("key.serializer.class", classOf[DefaultEncoder].getCanonicalName)
  properties.put("partitioner.class", classOf[AllToOne].getCanonicalName)

  properties.putAll(pt.toMap)

  val config: ProducerConfig = new ProducerConfig(properties)

  val producer = new Producer[Event, Array[Byte]](config)

  val serializer = new EventDeSerializer()

  override def collect(t: Event): Unit = {
    val serialized = serializer.serialize(t)

    producer.send(new KeyedMessage[Event, Array[Byte]](
      KafkaGenerator.TOPIC, null, t, serialized))
  }

  override def close(): Unit = {
    producer.close()
  }
}
