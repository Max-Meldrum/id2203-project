package se.kth.id2203.broadcast.beb

import java.util.UUID

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class BestEffortBroadcastRequest(event: KompicsEvent, addresses: List[NetAddress]) extends KompicsEvent with Serializable {
  val uuid: UUID = UUID.randomUUID()
}
