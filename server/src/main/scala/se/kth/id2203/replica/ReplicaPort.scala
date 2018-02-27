package se.kth.id2203.replica

import java.util.UUID

import se.kth.id2203.networking.NetAddress
import se.kth.id2203.replica.Replica.Status
import se.sics.kompics.{KompicsEvent, PortType}


class ReplicaPort extends PortType {
  {
    request(classOf[AtomicBroadcastRequest])
    indication(classOf[AtomicBroadcastCommit])
    indication(classOf[AtomicBroadcastProposal])
    request(classOf[AtomicBroadcastAck])
    request(classOf[ReplicaStatus])
  }
}

case class AtomicBroadcastRequest(clientSrc: NetAddress, event: KompicsEvent, addresses: List[NetAddress])
  extends KompicsEvent with Serializable

case class AtomicBroadcastProposal(clientSrc: NetAddress, epoch: Int, proposalId: Int, event: KompicsEvent, addresses: List[NetAddress])
  extends KompicsEvent with Serializable {
  val uuid: UUID = UUID.randomUUID()
}

case class AtomicBroadcastAck(src: NetAddress, dest: NetAddress, event: KompicsEvent) extends KompicsEvent with Serializable
case class AtomicBroadcastCommit(payload: KompicsEvent) extends KompicsEvent with Serializable
case class ReplicaStatus(status: Status, primary: NetAddress) extends KompicsEvent with Serializable
