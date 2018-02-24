package se.kth.id2203.broadcast


import se.kth.id2203.broadcast.AtomicBroadcast.{ACKS, PROPOSAL}
import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.kvstore._
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.{RouteMsg, Routing, VSOverlayManager}
import se.kth.id2203.overlay.VSOverlayManager.{Backup, Inactive, Primary}
import se.sics.kompics.network.Network
import se.sics.kompics.{KompicsEvent}
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
  private val ab: NegativePort[AtomicBroadcastPort] = provides[AtomicBroadcastPort]
  // beb for now, later rb..
  private val beb: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]

  private val delivered = collection.mutable.Set[KompicsEvent]()
  private val unordered = collection.mutable.Set[KompicsEvent]()
  private val accepted = mutable.HashMap[PROPOSAL, ACKS]()

  private var status: VSOverlayManager.Status = Inactive

  // Incremented Integer that is packed with each proposal
  private var proposalId = 0
  // Current proposal that is committed
  private var currentCommit = 0

  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  private val store = new mutable.HashMap[String,String]
  // For testing purposes
  store.put("unit_test", "kth")

  //TODO: Make sure we have total order...
  ab uponEvent {
    case request: AtomicBroadcastRequest => handle {
      val nodes = request.addresses
      val cs = request.clientSrc

      status match {
        case Backup =>
          log.info(s"Backup $self forwarding proposal request to primary")
          val key = request.event match {case (op: Op) => op.key}
          trigger(NetMessage(cs, nodes.head, RouteMsg(key, request.event)) -> net)
        case Primary =>
          proposalId+=1
          log.info(s"ProposalID: $proposalId")
          val proposal = AtomicBroadcastProposal(cs, request.epoch, proposalId, request.event, nodes)
          trigger(BestEffortBroadcastRequest(proposal, nodes) -> beb)
        case Inactive =>
          log.info(s"$self has an inactive status")
      }
    }

    case s: AtomicBroadcastStatus => handle {
      // Update to Backup/Primary...
      status = s.status
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

          // not used currently
          currentCommit+=1
        }
      }
    }
    case BestEffortBroadcastDeliver(dest, AtomicBroadcastCommit(AtomicBroadcastProposal(cs, _, _, op@Op(_, _, _, _),  _))) => handle {
      log.info(s"$self committed op ${op}")
      delivered.add(op)
      unordered.remove(op)

      //TODO: Refactor
      // If Primary, commit result and return response to client
      // If Backup, only commit the value..
      status match {
        case Primary =>
          op.command match {
            case GET =>
              trigger(NetMessage(self, cs, op.response(store.getOrElse(op.key, ""), OpCode.Ok)) -> net)
            case PUT =>
              trigger(NetMessage(self, cs, op.response(store.put(op.key, op.value.getOrElse("")).getOrElse(""), OpCode.Ok)) -> net)
            case CAS =>
              trigger(NetMessage(self, cs, op.response(OpCode.NotImplemented)) -> net)
          }
        case Backup =>
          op.command match {
            case GET =>
            case PUT =>
              store.put(op.key, op.value.getOrElse(""))
            case CAS =>
          }
        case Inactive =>
          log.info("Something went very wrong, we shouldn't be here..")
      }
    }
  }

  private def majority(nodes: List[NetAddress]): Int =
    (nodes.size/2)+1

}
