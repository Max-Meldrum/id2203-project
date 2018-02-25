package se.kth.id2203.replica

import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.kvstore._
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.RouteMsg
import se.kth.id2203.replica.Replica._
import se.sics.kompics.{Start}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}

import scala.collection.mutable

object Replica {
  type Ack = Int
  type Proposal = Int

  sealed trait Status
  case object Primary extends Status
  case object Backup extends Status
  case object Inactive extends Status
}

class Replica extends ComponentDefinition {
  //******* Ports ******
  private val net: PositivePort[Network] = requires[Network]
  private val replica: NegativePort[ReplicaPort] = provides[ReplicaPort]
  // beb for now, later tob..
  private val beb: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]

  //******* Fields ******
  private val accepted = mutable.HashMap[Proposal, Ack]()
  // Primary/Backup/Inactive
  private var status: Status = Inactive
  // Incremented Integer that is packed with each proposal
  private var proposalId = 0
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  private val store = new mutable.HashMap[String,String]

  ctrl uponEvent {
    case _: Start => handle {
      // For testing purposes
      store.put("unit_test", "kth")
    }
  }

  replica uponEvent {
    /**
      * <AtomicBroadcastRequest>
      * 1. Proposals are sent in total order
      * 2. ACKs are received in order as well
      * 3. Commits are sent out after receiving a majority of ACKs
      */
    //TODO: Make sure we have total order...
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
    case s: ReplicaStatus => handle {
      // Update to Backup/Primary...
      status = s.status
    }
  }

  beb uponEvent {
    case BestEffortBroadcastDeliver(dest, prop@AtomicBroadcastProposal(_, _, _,event, _)) => handle {
      trigger(BestEffortBroadcastRequest(AtomicBroadcastAck(dest, prop), List(dest)) -> beb)
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
