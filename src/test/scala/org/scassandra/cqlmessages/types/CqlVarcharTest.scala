/*
 * Copyright (C) 2014 Christopher Batey and Dogan Narinc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.scassandra.cqlmessages.types

import org.scalatest.{Matchers, FunSuite}
import akka.util.ByteString


class CqlVarcharTest extends FunSuite with Matchers {

  test("Serialisation of CqlVarchar") {
    CqlVarchar.writeValue("hello") should equal(Array[Byte](0, 0, 0, 5, 104, 101, 108, 108, 111))
    CqlVarchar.writeValueInCollection("hello") should equal(Array[Byte](0, 5, 104, 101, 108, 108, 111))
    CqlVarchar.writeValue("") should equal(Array[Byte](0, 0, 0, 0))
    CqlVarchar.writeValueInCollection("") should equal(Array[Byte](0, 0))
    CqlVarchar.writeValue(BigDecimal("123.67")) should equal(Array[Byte](0, 0, 0, 6, 49, 50, 51, 46, 54, 55))
    CqlVarchar.writeValueInCollection(BigDecimal("123.67")) should equal(Array[Byte](0, 6, 49, 50, 51, 46, 54, 55))
    CqlVarchar.writeValue(true) should equal(Array[Byte](0, 0, 0, 4, 116, 114, 117, 101))
    CqlVarchar.writeValueInCollection(true) should equal(Array[Byte](0, 4, 116, 114, 117, 101))

    intercept[IllegalArgumentException] {
      CqlVarchar.writeValue(List())
      CqlVarchar.writeValueInCollection(List())
    }
    intercept[IllegalArgumentException] {
      CqlVarchar.writeValue(Map())
      CqlVarchar.writeValueInCollection(Map())
    }
  }

  test("Reading null") {
    val bytes = ByteString(Array[Byte](-1,-1,-1,-1))

    val deserialisedValue = CqlVarchar.readValue(bytes.iterator)

    deserialisedValue should equal(None)
  }

  test("Reading value in collection") {
    val bytes = ByteString(Array[Byte](0, 2, 45, 43))

    val deserialisedValue = CqlVarchar.readValueInCollection(bytes.iterator)

    deserialisedValue should equal("-+")
  }
}
