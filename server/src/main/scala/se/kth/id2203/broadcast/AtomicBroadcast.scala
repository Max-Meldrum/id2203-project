package se.kth.id2203.broadcast

import java.util.UUID

import se.kth.id2203.broadcast.AtomicBroadcast.{ACKS, PROPOSAL}
import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.kvstore.{KVService, Op}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.Routing
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
  private val routing = provides(Routing)
  // beb for now, later rb..
  private val beb: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]

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
      val proposal = AtomicBroadcastProposal(request.clientSrc, request.epoch, proposalId, request.event, nodes)
      trigger(BestEffortBroadcastRequest(proposal, nodes) -> beb)
    }
  }

  beb uponEvent {
    case BestEffortBroadcastDeliver(dest, prop@AtomicBroadcastProposal(_, _, _,event, _)) => handle {
      if (!delivered.contains(event)) {
        unordered.add(event)
        trigger(BestEffortBroadcastRequest(AtomicBroadcastAck(dest, prop), List(dest)) -> beb)
      }
    }
    case BestEffortBroadcastDeliver(_, AtomicBroadcastAck(_, prop@AtomicBroadcastProposal(_, _ ,proposal, _, nodes))) => handle {
      val ack = accepted.getOrElse(proposal, 0)
      val quorum = majority(nodes)
      if (!(ack >= quorum)) {
        val newAck = ack + 1
        accepted.put(proposal, newAck)
        if (newAck >= quorum) {
          trigger(BestEffortBroadcastRequest(AtomicBroadcastCommit(prop), nodes) -> beb)
        }
      }
    }
    case BestEffortBroadcastDeliver(dest, AtomicBroadcastCommit(AtomicBroadcastProposal(cs, _, _, op@Op(_, _, _, _),  _))) => handle {
      log.info(s"$self committed op ${op}")
      delivered.add(op)
      unordered.remove(op)
      // Commit command to the KV-Store
      trigger(NetMessage(cs, self, op) -> net)
    }
  }

  private def majority(nodes: List[NetAddress]): Int =
    (nodes.size/2)+1

}
