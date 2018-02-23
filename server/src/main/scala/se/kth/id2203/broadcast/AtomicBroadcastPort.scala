package se.kth.id2203.broadcast

import java.util.UUID

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.{KompicsEvent, PortType}


class AtomicBroadcastPort extends PortType {
  {
    request(classOf[AtomicBroadcastRequest])
    indication(classOf[AtomicBroadcastCommit])
    indication(classOf[AtomicBroadcastProposal])
    request(classOf[AtomicBroadcastAck])
  }
}

case class AtomicBroadcastCommit(payload: KompicsEvent) extends KompicsEvent with Serializable
case class AtomicBroadcastRequest(epoch: Int, event: KompicsEvent, addresses: List[NetAddress])
  extends KompicsEvent with Serializable {
  val uuid: UUID = UUID.randomUUID()
}
case class AtomicBroadcastProposal(epoch: Int, proposalId: Int, event: KompicsEvent, addresses: List[NetAddress])
  extends KompicsEvent with Serializable

case class AtomicBroadcastAck(src: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable
