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
package org.scassandra.server.actors

import akka.actor.{ActorRef, ActorSystem}
import akka.io.Tcp.{Received, Write}
import akka.testkit._
import akka.util.ByteString
import org.scalatest._
import org.scassandra.server.actors.MessageHelper.SimpleQuery
import org.scassandra.server.actors.OptionsHandlerMessages.OptionsMessage
import org.scassandra.server.cqlmessages._
import org.scassandra.server.cqlmessages.response._
import org.scassandra.server.actors.QueryHandler.Query
import org.scassandra.server.RegisterHandlerMessages
import scala.language.postfixOps
import scala.concurrent.duration._

class ConnectionHandlerTest extends TestKit(ActorSystem("ConnectionHandlerTest")) with Matchers with FunSuiteLike with BeforeAndAfter {

  var testActorRef : TestActorRef[ConnectionHandler] = null

  var queryHandlerTestProbe : TestProbe = null
  var batchHandlerTestProbe : TestProbe = null
  var registerHandlerTestProbe : TestProbe = null
  var optionsHandlerTestProbe : TestProbe = null
  var prepareHandlerTestProbe : TestProbe = null
  var executeHandlerTestProbe : TestProbe = null

  var lastMsgFactoryUsedForQuery : CqlMessageFactory = null
  var lastMsgFactoryUsedForRegister : CqlMessageFactory = null
  var lastMsgFactoryUsedForPrepare : CqlMessageFactory = null

  before {
    queryHandlerTestProbe = TestProbe()
    registerHandlerTestProbe = TestProbe()
    prepareHandlerTestProbe = TestProbe()
    executeHandlerTestProbe = TestProbe()
    optionsHandlerTestProbe = TestProbe()
    batchHandlerTestProbe = TestProbe()
    testActorRef = TestActorRef(new ConnectionHandler(
      (_,_,msgFactory) => {
        lastMsgFactoryUsedForQuery = msgFactory
        queryHandlerTestProbe.ref
      },
      (_,_,msgFactory, _) => {
        lastMsgFactoryUsedForQuery = msgFactory
        batchHandlerTestProbe.ref
      },
      (_,_,msgFactory) => {
        lastMsgFactoryUsedForRegister = msgFactory
        registerHandlerTestProbe.ref
      },
      (_,_,msgFactory) => {
        optionsHandlerTestProbe.ref
      },
      prepareHandlerTestProbe.ref,
      executeHandlerTestProbe.ref,
      (_,actorRef) => {
        actorRef
      }
    ))

    lastMsgFactoryUsedForQuery = null
    clear()
  }

  test("Should do nothing if not a full message") {
    val partialMessage = ByteString(
      Array[Byte](
        ProtocolVersion.ServerProtocolVersionTwo, 0x0, 0x0, OpCodes.Query, // header
        0x0, 0x0, 0x0, 0x5,  // length
        0x0 // 4 bytes missing
      )
    )

    testActorRef ! Received(partialMessage)

    queryHandlerTestProbe.expectNoMsg()
  }

  test("Should send ready message when startup message sent - version one") {
    implicit val protocolVersion = VersionOne
    val readyMessage = ByteString(
      Array[Byte](
        ProtocolVersion.ClientProtocolVersionOne, 0x0, 0x0, OpCodes.Startup, // header
        0x0, 0x0, 0x0, 0x0 // length
      )
    )

    val senderProbe = TestProbe()
    implicit val sender = senderProbe.ref

    testActorRef ! Received(readyMessage)

    senderProbe.expectMsg(Ready(0x0.toByte))
  }

  test("Should send ready message when startup message sent - version two") {
    implicit val protocolVersion = VersionTwo
    val readyMessage = ByteString(
      Array[Byte](
        ProtocolVersion.ClientProtocolVersionTwo, 0x0, 0x0, OpCodes.Startup, // header
        0x0, 0x0, 0x0, 0x0 // length
      )
    )

    val senderProbe = TestProbe()
    implicit val sender: ActorRef = senderProbe.ref

    testActorRef ! Received(readyMessage)

    senderProbe.expectMsg(Ready(0x0.toByte))
  }

  test("Should send back error if query before ready message") {
    implicit val protocolVersion = VersionTwo
    val queryMessage = ByteString(
      Array[Byte](
        protocolVersion.clientCode, 0x0, 0x0, OpCodes.Query, // header
        0x0, 0x0, 0x0, 0x0 // length
      )
    )

    val senderProbe = TestProbe()
    implicit val sender: ActorRef = senderProbe.ref

    testActorRef ! Received(queryMessage)

    senderProbe.expectMsg(Write(QueryBeforeReadyMessage().serialize()))
  }

  test("Should do nothing if an unrecognised opcode") {
    val unrecognisedOpCode = ByteString(
      Array[Byte](
        ProtocolVersion.ServerProtocolVersionTwo  , 0x0, 0x0, 0x56, // header
        0x0, 0x0, 0x0, 0x0 // length
      )
    )

    testActorRef ! Received(unrecognisedOpCode)

    expectNoMsg()
    queryHandlerTestProbe.expectNoMsg()
  }


  test("Should forward query to a new QueryHandler - version two of protocol") {
    sendStartupMessage()
    val stream : Byte = 0x04
    val query = "select * from people"
    val queryLength = Array[Byte](0x0, 0x0, 0x0, query.length.toByte)
    val queryOptions = Array[Byte](0,1,0)
    val queryWithLengthAndOptions = queryLength ++ query.getBytes ++ queryOptions
    val queryMessage = MessageHelper.createQueryMessage(query, stream, protocolVersion = ProtocolVersion.ClientProtocolVersionTwo)

    testActorRef ! Received(ByteString(queryMessage.toArray))

    queryHandlerTestProbe.expectMsg(Query(ByteString(queryWithLengthAndOptions), stream))
    lastMsgFactoryUsedForQuery should equal(VersionTwoMessageFactory)
  }

  test("Should forward query to a new QueryHandler - version one of protocol") {
    sendStartupMessage(VersionOne)
    val stream : Byte = 0x04
    val query = "select * from people"
    val queryLength = Array[Byte](0x0, 0x0, 0x0, query.length.toByte)
    val queryOptions = Array[Byte](0,1,0)
    val queryWithLengthAndOptions = queryLength ++ query.getBytes ++ queryOptions
    val queryMessage = MessageHelper.createQueryMessage(query, stream, protocolVersion = ProtocolVersion.ClientProtocolVersionOne)

    testActorRef ! Received(ByteString(queryMessage.toArray))

    queryHandlerTestProbe.expectMsg(Query(ByteString(queryWithLengthAndOptions), stream))
    lastMsgFactoryUsedForQuery should equal(VersionOneMessageFactory)
  }

  test("Should handle query message coming in two parts") {
    sendStartupMessage()
    val query = "select * from people"
    val stream : Byte = 0x05
    val queryLength = Array[Byte](0x0, 0x0, 0x0, query.length.toByte)
    val queryOptions = Array[Byte](0,1,0)
    val queryWithLengthAndOptions = queryLength ++ query.getBytes ++ queryOptions
    val queryMessage = MessageHelper.createQueryMessage(query, stream)
    
    val queryMessageFirstHalf = queryMessage take 5 toArray
    val queryMessageSecondHalf = queryMessage drop 5 toArray

    testActorRef ! Received(ByteString(queryMessageFirstHalf))
    queryHandlerTestProbe.expectNoMsg()
    
    testActorRef ! Received(ByteString(queryMessageSecondHalf))
    queryHandlerTestProbe.expectMsg(Query(ByteString(queryWithLengthAndOptions), stream))
  }

  test("Should forward register message to RegisterHandler - version two protocol") {
    sendStartupMessage()
    val stream : Byte = 1

    val registerMessage = MessageHelper.createRegisterMessage(ProtocolVersion.ClientProtocolVersionTwo, stream)

    testActorRef ! Received(ByteString(registerMessage.toArray))

    registerHandlerTestProbe.expectMsg(RegisterHandlerMessages.Register(ByteString(MessageHelper.dropHeaderAndLength(registerMessage.toArray)), stream))
    lastMsgFactoryUsedForRegister should equal(VersionTwoMessageFactory)
  }

  test("Should forward register message to RegisterHandler - version one protocol") {
    sendStartupMessage(VersionOne)
    val stream : Byte = 1

    val registerMessage = MessageHelper.createRegisterMessage(ProtocolVersion.ClientProtocolVersionOne, stream)

    testActorRef ! Received(ByteString(registerMessage.toArray))

    registerHandlerTestProbe.expectMsg(RegisterHandlerMessages.Register(ByteString(MessageHelper.dropHeaderAndLength(registerMessage.toArray)), stream))
    lastMsgFactoryUsedForRegister should equal(VersionOneMessageFactory)
  }

  test("Should forward options to OptionsHandler - version two protocol") {
    sendStartupMessage()

    implicit val protocolVersion = VersionTwo

    val stream : Byte = 0x04

    val optionsMessage = MessageHelper.createOptionsMessage(stream=stream)

    testActorRef ! Received(ByteString(optionsMessage.toArray))

    optionsHandlerTestProbe.expectMsg(OptionsMessage(stream))
  }

  test("Should forward options to OptionsHandler - version one protocol") {
    sendStartupMessage()

    implicit val protocolVersion = VersionOne

    val stream : Byte = 0x04

    val optionsMessage = MessageHelper.createOptionsMessage(ProtocolVersion.ClientProtocolVersionOne, stream=stream)

    testActorRef ! Received(ByteString(optionsMessage.toArray))

    optionsHandlerTestProbe.expectMsg(OptionsMessage(stream))
  }

  test("Should handle two cql messages in the same data message") {
    val startupMessage = MessageHelper.createStartupMessage()
    val stream : Byte = 0x04
    val query = "select * from people"
    val queryLength = Array[Byte](0x0, 0x0, 0x0, query.length.toByte)
    val queryOptions = Array[Byte](0,1,0)
    val queryWithLengthAndOptions = queryLength ++ query.getBytes ++ queryOptions
    val queryMessage = MessageHelper.createQueryMessage(query, stream)

    val twoMessages: List[Byte] = startupMessage ++ queryMessage

    implicit val sender = TestProbe().ref

    testActorRef ! Received(ByteString(twoMessages.toArray))

    queryHandlerTestProbe.expectMsg(Query(ByteString(queryWithLengthAndOptions), stream))
  }

  test("Should forward Prepare messages to the prepare handler") {
    sendStartupMessage()
    val streamId : Byte = 0x1
    val headerForPrepareMessage = new Header(ProtocolVersion.ClientProtocolVersionTwo,
                                             OpCodes.Prepare, streamId)
    val emptyPrepareMessage = headerForPrepareMessage.serialize() ++ Array[Byte](0,0,0,0)

    implicit val sender = TestProbe().ref

    testActorRef ! Received(ByteString(emptyPrepareMessage))

    prepareHandlerTestProbe.expectMsg(PrepareHandler.Prepare(ByteString(), streamId, VersionTwoMessageFactory, sender))
  }

  test("Should forward Execute messages to the execute handler") {
    sendStartupMessage()
    val streamId : Byte = 0x1
    val headerForPrepareMessage = new Header(ProtocolVersion.ClientProtocolVersionTwo,
      OpCodes.Execute, streamId)
    val messageBody = Array[Byte](5,6)
    val emptyPrepareMessage = headerForPrepareMessage.serialize() ++
      Array[Byte](0,0,0,messageBody.length.toByte) ++ messageBody

    implicit val sender = TestProbe().ref

    testActorRef ! Received(ByteString(emptyPrepareMessage))

    executeHandlerTestProbe.expectMsg(ExecuteHandler.Execute(ByteString(messageBody), streamId, VersionTwoMessageFactory, sender))
  }

  test("Should forward Batch messages to the batch handler") {
    sendStartupMessage()
    val streamId : Byte = 0x1
    val batchMessage: Array[Byte] = MessageHelper.createBatchMessage(
      List(SimpleQuery("insert into something"), SimpleQuery("insert into something else")), streamId)

    testActorRef ! Received(ByteString(batchMessage))

    batchHandlerTestProbe.expectMsg(BatchHandler.Execute(ByteString(MessageHelper.dropHeaderAndLength(batchMessage)), streamId))
  }

  test("Should send unsupported version if protocol 3+") {
    implicit val protocolVersion = VersionTwo
    val stream : Byte = 0x0 // hard coded for now
    val startupMessage = MessageHelper.createStartupMessage(VersionThree)

    val senderProbe = TestProbe()
    implicit val sender = senderProbe.ref

    testActorRef ! Received(ByteString(startupMessage.toArray))

    senderProbe.expectMsg(UnsupportedProtocolVersion(stream))

    case object VersionFive extends ProtocolVersion(0x5, (0x85 & 0xFF).toByte, 5)
    val v5StartupMessage = MessageHelper.createStartupMessage(VersionFive)

    testActorRef ! Received(ByteString(v5StartupMessage.toArray))

    senderProbe.expectMsg(UnsupportedProtocolVersion(stream))
  }

  private def sendStartupMessage(protocolVersion: ProtocolVersion = VersionTwo) = {
    val startupMessage = MessageHelper.createStartupMessage(protocolVersion)
    testActorRef ! Received(ByteString(startupMessage.toArray))
  }

  private def clear(): Unit = {
    receiveWhile(10 milliseconds) {
      case msg @ _ =>
    }
  }
}
