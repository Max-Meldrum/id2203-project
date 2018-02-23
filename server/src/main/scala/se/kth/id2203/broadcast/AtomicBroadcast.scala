package se.kth.id2203.broadcast

import java.util.UUID

import se.kth.id2203.broadcast.AtomicBroadcast.{ACKS, PROPOSAL}
import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.kvstore.Op
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.network.Network
import se.sics.kompics.{Channel, Kompics, KompicsEvent}
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}

import scala.collection.mutable

object AtomicBroadcast {
  type ACKS = Int
  type PROPOSAL = Int
}

/**
  * Two-phase commit based component
  * 1. Proposals are sent in total order
  * 2. ACKs are received in order as well
  * 3. Commits are sent out after receiving a majority of ACKs
  */
class AtomicBroadcast extends ComponentDefinition {
  private val net: PositivePort[Network] = requires[Network]
  private val abRequire: PositivePort[AtomicBroadcastPort] = requires[AtomicBroadcastPort]
  private val ab: NegativePort[AtomicBroadcastPort] = provides[AtomicBroadcastPort]
  // beb for now, later tob..
  private val beb: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]
  //val rb: PositivePort[ReliableBroadcastPort] = requires[ReliableBroadcastPort]

  private val self = config.getValue("id2203.project.address", classOf[NetAddress])

  private val delivered = collection.mutable.Set[KompicsEvent]()
  private val unordered = collection.mutable.Set[KompicsEvent]()
  private var accepted = mutable.HashMap[PROPOSAL, ACKS]()

  // Incremented Integer that is packed with each proposal
  var proposalId = 0

  //TODO: Finish Two-phase commit
  ab uponEvent {
    case request: AtomicBroadcastRequest => handle {
      val nodes = request.addresses
      proposalId+=1
      log.info(s"ProposalID: $proposalId")
      val proposal = AtomicBroadcastProposal(request.epoch, proposalId, request.event, nodes)
      trigger(BestEffortBroadcastRequest(proposal, nodes) -> beb)
    }
  }

  beb uponEvent {
    case BestEffortBroadcastDeliver(dest, prop@AtomicBroadcastProposal(e,p,event,nodes)) => handle {
      if (!delivered.contains(event)) {
        unordered.add(event)
        trigger(BestEffortBroadcastRequest(AtomicBroadcastAck(dest, prop), List(dest)) -> beb)
      }
    }
    case BestEffortBroadcastDeliver(dest, AtomicBroadcastAck(src, prop@AtomicBroadcastProposal(e,p,event, nodes))) => handle {
      val ack = accepted.getOrElse(p, 0)
      val quorum = majority(nodes)
      if (!(ack >= quorum)) {
        val newAck = ack + 1
        accepted.put(p, newAck)
        if (newAck >= quorum) {
          trigger(BestEffortBroadcastRequest(AtomicBroadcastCommit(prop), nodes) -> beb)
        }
      }
    }
    case BestEffortBroadcastDeliver(dest, commit@AtomicBroadcastCommit(AtomicBroadcastProposal(e, p, op@Op(c, k, v, id),  _))) => handle {
      delivered.add(op)
      unordered.remove(op)
      log.info(s"$self committed op ${op}")
    }
  }

  private def majority(nodes: List[NetAddress]): Int =
    (nodes.size/2)+1

}
