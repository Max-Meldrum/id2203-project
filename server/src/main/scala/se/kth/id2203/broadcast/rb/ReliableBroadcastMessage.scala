package se.kth.id2203.broadcast.rb

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class ReliableBroadcastMessage(src: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable
