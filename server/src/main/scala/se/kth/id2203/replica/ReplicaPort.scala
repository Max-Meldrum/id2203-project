package se.kth.id2203.replica

import java.util.UUID

import se.kth.id2203.networking.NetAddress
import se.kth.id2203.replica.Replica.Status
import se.sics.kompics.{KompicsEvent, PortType}


class ReplicaPort extends PortType {
  {
    request(classOf[AtomicBroadcastRequest])
    indication(classOf[AtomicBroadcastCommit])
    indication(classOf[NewLeaderCommit])
    indication(classOf[AtomicBroadcastProposal])
    indication(classOf[NewLeaderProposal])
    request(classOf[AtomicBroadcastAck])
    request(classOf[NewLeaderAck])
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

case class ReplicaStatus(status: Status, primary: NetAddress, group: Set[NetAddress]) extends KompicsEvent with Serializable
case class NewLeaderProposal(src: NetAddress, epoch: Int, lastCommit: Int, group: Set[NetAddress]) extends KompicsEvent with Serializable
case class NewLeaderAck(src: NetAddress, dest: NetAddress, event: KompicsEvent)
  extends KompicsEvent with Serializable
case class NewLeaderCommit(payload: KompicsEvent) extends KompicsEvent with Serializable

