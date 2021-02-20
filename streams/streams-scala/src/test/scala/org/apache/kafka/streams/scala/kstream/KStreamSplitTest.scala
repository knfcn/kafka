/*
 * Copyright (C) 2018 Joan Goyeau.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.scala.kstream

import org.apache.kafka.streams.kstream.Named
import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala.StreamsBuilder
import org.apache.kafka.streams.scala.serialization.Serdes._
import org.apache.kafka.streams.scala.utils.TestDriver
import org.junit.runner.RunWith
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.junit.JUnitRunner

import scala.jdk.CollectionConverters._

@RunWith(classOf[JUnitRunner])
class KStreamSplitTest extends FlatSpec with Matchers with TestDriver {
  "split" should "route messages according to predicates" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"
    val sinkTopic = Array("default", "even", "three");

    val m = builder
      .stream[Integer, Integer](sourceTopic)
      .split(Named.as("_"))
      .branch((_, v) => v % 2 == 0)
      .branch((_, v) => v % 3 == 0)
      .defaultBranch()

    m("_0").to(sinkTopic(0))
    m("_1").to(sinkTopic(1))
    m("_2").to(sinkTopic(2))

    val testDriver = createTestDriver(builder)
    val testInput = testDriver.createInput[Integer, Integer](sourceTopic)
    val testOutput = sinkTopic.map(name => testDriver.createOutput[Integer, Integer](name))

    testInput.pipeValueList(
      List(1, 2, 3, 4, 5)
        .map(Integer.valueOf)
        .asJava
    )

    testOutput(0).readValuesToList().asScala shouldBe List(1, 5)
    testOutput(1).readValuesToList().asScala shouldBe List(2, 4)
    testOutput(2).readValuesToList().asScala shouldBe List(3)

    testDriver.close()
  }

  "split" should "route messages to consumers" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"

    val m = builder
      .stream[Integer, Integer](sourceTopic)
      .split(Named.as("_"))
      .branch((_, v) => v % 2 == 0, Branched.withConsumer(ks => ks.to("even"), "consumedEvens"))
      .branch((_, v) => v % 3 == 0, Branched.withFunction(ks => ks.mapValues(x => x * x), "mapped"))
      .noDefaultBranch()

    m("_mapped").to("mapped")

    val testDriver = createTestDriver(builder)
    val testInput = testDriver.createInput[Integer, Integer](sourceTopic)
    testInput.pipeValueList(
      List(1, 2, 3, 4, 5, 9)
        .map(Integer.valueOf)
        .asJava
    )

    val even = testDriver.createOutput[Integer, Integer]("even")
    val mapped = testDriver.createOutput[Integer, Integer]("mapped")

    even.readValuesToList().asScala shouldBe List(2, 4)
    mapped.readValuesToList().asScala shouldBe List(9, 81)

    testDriver.close()
  }

  "split" should "route messages to anonymous consumers" in {
    val builder = new StreamsBuilder()
    val sourceTopic = "source"

    val m = builder
      .stream[Integer, Integer](sourceTopic)
      .split(Named.as("_"))
      .branch((_, v) => v % 2 == 0, Branched.withConsumer(ks => ks.to("even")))
      .branch((_, v) => v % 3 == 0, Branched.withFunction(ks => ks.mapValues(x => x * x)))
      .noDefaultBranch()

    m("_2").to("mapped")

    val testDriver = createTestDriver(builder)
    val testInput = testDriver.createInput[Integer, Integer](sourceTopic)
    testInput.pipeValueList(
      List(1, 2, 3, 4, 5, 9)
        .map(Integer.valueOf)
        .asJava
    )

    val even = testDriver.createOutput[Integer, Integer]("even")
    val mapped = testDriver.createOutput[Integer, Integer]("mapped")

    even.readValuesToList().asScala shouldBe List(2, 4)
    mapped.readValuesToList().asScala shouldBe List(9, 81)

    testDriver.close()
  }
}
