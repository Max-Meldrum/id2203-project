package se.kth.id2203.broadcast.tob

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class TotalOrderBroadcastRequest(event: KompicsEvent, addresses: List[NetAddress]) extends KompicsEvent with Serializable
