package se.kth.id2203.broadcast.beb

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class BestEffortBroadcastDeliver(src: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable
