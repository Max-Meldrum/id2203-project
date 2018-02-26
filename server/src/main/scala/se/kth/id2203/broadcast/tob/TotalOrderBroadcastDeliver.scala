package se.kth.id2203.broadcast.tob

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class TotalOrderBroadcastDeliver(src: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable

