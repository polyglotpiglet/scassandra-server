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

import java.nio.ByteBuffer
import java.util

import akka.util.ByteIterator
import org.apache.cassandra.serializers.{ListSerializer, SetSerializer, AsciiSerializer}
import org.apache.cassandra.utils.ByteBufferUtil
import org.scassandra.cqlmessages.CqlProtocolHelper
import scala.collection.JavaConversions._
import CqlProtocolHelper._

import scala.collection.mutable

// only supports strings for now.
//todo change this to a types class
case class CqlSet[T](setType : ColumnType[T]) extends ColumnType[Set[_]](0x0022, s"set<${setType.stringRep}>") {
   override def readValue(byteIterator: ByteIterator): Option[Set[String]] = {
     CqlProtocolHelper.readVarcharSetValue(byteIterator)
   }

  def writeValue(value: Any) : Array[Byte] = {
    val setSerialiser: SetSerializer[T] = SetSerializer.getInstance(setType.serializer)
    val set: Set[T] = value match {
      case s: Set[T] =>
        s
      case _: List[T] =>
        value.asInstanceOf[List[T]].toSet
      case _: Seq[T] =>
        value.asInstanceOf[Seq[T]].toSet
      case _ =>
        throw new IllegalArgumentException(s"Can't serialise ${value} as Set of $setType")
    }

    val collectionType: util.Set[T] = setType.convertToCorrectCollectionTypeForSet(set)

    val serialised: util.List[ByteBuffer] = setSerialiser.serializeValues(collectionType)

    val setContents = serialised.foldLeft(new Array[Byte](0))((acc, byteBuffer) => {
      val current: mutable.ArrayOps[Byte] = ByteBufferUtil.getArray(byteBuffer)
      acc ++ serializeShort(current.size.toShort) ++ current
    })

    serializeInt(setContents.length + 2) ++ serializeShort(set.size.toShort) ++ setContents
  }
 }