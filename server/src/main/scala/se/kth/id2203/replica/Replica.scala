package se.kth.id2203.replica

import java.util.UUID

import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.kvstore._
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.kth.id2203.overlay.RouteMsg
import se.kth.id2203.replica.Replica._
import se.sics.kompics.{KompicsEvent, Start}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}
import se.sics.kompics.timer.{CancelPeriodicTimeout, SchedulePeriodicTimeout, Timeout, Timer}

import scala.collection.mutable

object Replica {
  type Ack = Int
  type Proposal = Int
  type Key = String
  type Value = String

  sealed trait Status
  case object Primary extends Status
  case object Backup extends Status
  case object Inactive extends Status

  case object Heartbeat extends KompicsEvent
  case class PrimaryValidation(spt: SchedulePeriodicTimeout) extends Timeout(spt)
  case class NotifyPrimary(spt: SchedulePeriodicTimeout) extends Timeout(spt)
}

class Replica extends ComponentDefinition {
  //******* Ports ******
  private val net: PositivePort[Network] = requires[Network]
  private val replica: NegativePort[ReplicaPort] = provides[ReplicaPort]
  // beb for now, later tob..
  private val beb: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]
  private val timer = requires[Timer]

  //******* Fields ******
  // Our kv-store
  private val store = mutable.HashMap[Key, Value]()
  // map that keeps track of number of ACKs for each proposal..
  private val accepted = mutable.HashMap[Proposal, Ack]()
  // Primary/Backup/Inactive
  private var status: Status = Inactive
  // Incremented Integer that is packed with each proposal
  private var proposalId = 0
  // This replicas NetAddress
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  // Current primary
  private var primary: Option[NetAddress] = None
  // Replicas currently synced to the Primary..
  private val synced = mutable.HashSet.empty[NetAddress]

  // Timer ids
  private var primaryTimerId: Option[UUID] = None
  private var backupTimerId: Option[UUID] = None

  ctrl uponEvent {
    case _: Start => handle {
      // For testing purposes
      store.put("unit_test", "kth")
    }
  }


  timer uponEvent {
    case PrimaryValidation(_) => handle {
      synced += self
      val quorum = majority(synced.toList)
      val conn = synced.size
      if (conn >= quorum) {
        log.info(s"$self has a quorum of backups connected to it...")
      } else {
        log.info(s"$self lost connection with a quorum of it's cluster...")
        // Do some form of leader election here
      }

      // Reset
      synced.clear()
    }
    case NotifyPrimary(_) => handle {
      if (primary.isDefined)
        trigger(NetMessage(self, primary.get, Heartbeat)-> net)
    }
  }


  net uponEvent {
    case NetMessage(header, Heartbeat) => handle {
      synced += header.src
    }
  }


  replica uponEvent {
    case s: ReplicaStatus => handle {
      // Update to Backup/Primary...
      status = s.status
      // Set primary of the replication group
      primary = Some(s.primary)

      // Enable/Disable replica timers..
      status match {
        case Primary =>
          backupTimerId.map(new CancelPeriodicTimeout(_) -> timer)
          backupTimerId = None
          enablePrimaryTimeout()
        case Backup =>
          primaryTimerId.map(new CancelPeriodicTimeout(_) -> timer)
          primaryTimerId = None
          enableBackupTimer()
        case Inactive =>

      }
    }

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

  private def enablePrimaryTimeout(): Unit = {
    val timeout: Long = cfg.getValue[Long]("id2203.project.primaryValidation") * 2l
    val spt = new SchedulePeriodicTimeout(timeout, timeout)
    spt.setTimeoutEvent(PrimaryValidation(spt))
    trigger(spt -> timer)
    primaryTimerId = Some(spt.getTimeoutEvent.getTimeoutId)
  }

  private def enableBackupTimer(): Unit = {
    val timeout: Long = cfg.getValue[Long]("id2203.project.heartbeatsTimer") * 2l
    val spt = new SchedulePeriodicTimeout(timeout, timeout)
    spt.setTimeoutEvent(NotifyPrimary(spt))
    trigger(spt -> timer)
    backupTimerId = Some(spt.getTimeoutEvent.getTimeoutId)
  }
}
