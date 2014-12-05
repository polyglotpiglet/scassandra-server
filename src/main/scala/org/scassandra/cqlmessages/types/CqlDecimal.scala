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

import akka.util.ByteIterator
import org.apache.cassandra.serializers.{DecimalSerializer, TypeSerializer}
import org.scassandra.cqlmessages.CqlProtocolHelper

case object CqlDecimal extends ColumnType[java.math.BigDecimal](0x0006, "decimal") {
   override def readValue(byteIterator: ByteIterator): Option[java.math.BigDecimal] = {
     CqlProtocolHelper.readDecimalValue(byteIterator).map(_.bigDecimal)
   }

   def writeValue(value: Any) = {
     CqlProtocolHelper.serializeDecimalValue(new java.math.BigDecimal(value.toString))
   }

  override def convertToCorrectCollectionTypeForList(list: Iterable[_]) : List[java.math.BigDecimal] = {
    list.map {
      case bd: BigDecimal => bd.bigDecimal
      case _ => throw new IllegalArgumentException("Expected list of BigDecimals")
    }.toList
  }

  override def serializer: TypeSerializer[java.math.BigDecimal] = DecimalSerializer.instance
 }