package se.kth.id2203.replica

import java.util.UUID

import se.kth.id2203.broadcast.rb.{ReliableBroadcastDeliver, ReliableBroadcastPort, ReliableBroadcastRequest}
import se.kth.id2203.kvstore._
import se.kth.id2203.networking.{DropDead, NetAddress, NetMessage}
import se.kth.id2203.overlay.RouteMsg
import se.kth.id2203.replica.Replica._
import se.sics.kompics.{ KompicsEvent, Start}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}
import se.sics.kompics.timer.{CancelPeriodicTimeout, SchedulePeriodicTimeout, Timeout, Timer}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object Replica {
  type Ack = Int
  type Commit = Int
  type Proposal = Int
  type Key = String
  type Value = String

  sealed trait Status
  case object Primary extends Status
  case object Backup extends Status
  case object Election extends Status
  case object Inactive extends Status

  case object Heartbeat extends KompicsEvent
  case class PrimaryValidation(spt: SchedulePeriodicTimeout) extends Timeout(spt)
  case class NotifyPrimary(spt: SchedulePeriodicTimeout) extends Timeout(spt)
  case class NotifyBackups(spt: SchedulePeriodicTimeout) extends Timeout(spt)
  case class hasPrimary(spt: SchedulePeriodicTimeout) extends Timeout(spt)
}

class Replica extends ComponentDefinition {
  //******* Ports ******
  private val net: PositivePort[Network] = requires[Network]
  private val replica: NegativePort[ReplicaPort] = provides[ReplicaPort]
  // beb for now, later tob..
  private val rb: PositivePort[ReliableBroadcastPort] = requires[ReliableBroadcastPort]
  private val timer = requires[Timer]

  //******* Fields ******
  // Our kv-store
  private val store = mutable.HashMap[Key, Value]()
  // map that keeps track of number of ACKs for each proposal..
  private val accepted = mutable.HashMap[Proposal, Ack]()
  // Helper for our leader election
  private val electionAcks = mutable.HashMap[(NetAddress,Commit), Ack]()
  // Primary/Backup/Inactive/Election
  private var status: Status = Inactive
  // Current epoch, gets increased with new leader..
  private var epoch = 0
  // Known epoch
  private var knownEpoch = 0
  // If we have promised to honor a leader proposal
  private var promised = false
  // Incremented Integer that is packed with each proposal
  private var proposalId = 0
  // Current committed transaction
  private var commit = 0
  // This replicas NetAddress
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  // Current primary
  private var primary: Option[NetAddress] = None
  // Replicas currently synced to the Primary..
  private val synced = mutable.HashSet.empty[NetAddress]
  // Backups check if primary is in contact with it
  private val primarySync = mutable.HashSet.empty[NetAddress]


  // Number of replicas for a replication group is set through the config
  private val groupSize = config.getValue("id2203.project.replicationGroupSize", classOf[Int])
  // Current known replication group
  private var replicationGroup = mutable.Set.empty[NetAddress]

  // Timer ids
  private var primaryTimerIdIn: Option[UUID] = None
  private var primaryTimerIdOut: Option[UUID] = None
  private var backupTimerIdIn: Option[UUID] = None
  private var backupTimerIdOut: Option[UUID] = None

  ctrl uponEvent {
    case _: Start => handle {
      // For testing purposes
      store.put("unit_test","kth")
    }
  }

  timer uponEvent {
    case PrimaryValidation(_) => handle {
      synced += self
      val quorum = majority(groupSize)
      val conn = synced.size
      if (conn >= quorum) {
        log.info(s"$self has a quorum of backups connected to it $synced")
      } else {
        log.info(s"$self lost connection with a quorum of it's cluster...")
        log.info(s"$self is entering election phase...")
        cancelPrimaryTimer()
        status = Election
        val newLeader = NewLeaderProposal(self, epoch+1, commit, replicationGroup.toSet)
        trigger(ReliableBroadcastRequest(newLeader, replicationGroup.toList) -> rb)
      }

      // Reset
      synced.clear()
    }
    case NotifyPrimary(_) => handle {
      if (primary.isDefined)
        Try(trigger(NetMessage(self, primary.get, Heartbeat)-> net)) match {
          case Success(_) => // ignore..
          case Failure(e) =>  // ignore..
        }
    }
    case NotifyBackups(_) => handle {
      // Primary notifies backup's that it is still active and running..
      replicationGroup.foreach(r => trigger(NetMessage(self, r, Heartbeat)-> net))
    }
    case hasPrimary(_) => handle {
      if (primarySync.isEmpty) {
        // We have lost contact with our Primary,
        // enter Election phase
        primary = None
        log.info("WE LOST CONTACT WITH OUR PRIMARY")
        cancelBackupTimer()
        status = Election
        val newLeader = NewLeaderProposal(self, knownEpoch+1, commit, replicationGroup.toSet)
        trigger(ReliableBroadcastRequest(newLeader, replicationGroup.toList) -> rb)
      } else {
        primarySync.clear()
      }
    }
  }


  net uponEvent {
    case NetMessage(header, Heartbeat) => handle {
      status match {
        case Primary =>
          synced += header.src
        case Backup =>
          primarySync += header.src
        case Election =>
        case Inactive =>
      }
    }
    case NetMessage(_ , DropDead) => handle {
      log.info(s"Me, $self was asked to die..")
      suicide()
      System.exit(1) // Just to be extra safe ...
    }
  }


  replica uponEvent {
    // Initial bootup...
    case s: ReplicaStatus => handle {
      // Update to Backup/Primary...
      status = s.status
      // Set primary of the replication group
      primary = Some(s.primary)
      // Set the replication group currently known to the replica
      replicationGroup = mutable.Set(s.group.toArray:_*)

      // Enable/Disable replica timers..
      status match {
        case Primary =>
          epoch += 1
          cancelBackupTimer()
          enablePrimaryTimer()
        case Backup =>
          knownEpoch +=1
          cancelPrimaryTimer()
          enableBackupTimer()
        case Election =>

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
          trigger(NetMessage(cs, primary.getOrElse(nodes.head), RouteMsg(key, request.event)) -> net)
        case Primary =>
          proposalId+=1
          log.info(s"ProposalID: $proposalId")
          val proposal = AtomicBroadcastProposal(cs, epoch, proposalId, request.event, nodes)
          trigger(ReliableBroadcastRequest(proposal, nodes) -> rb)
        case Election =>
          log.info(s"$self is in election phase")
        case Inactive =>
          log.info(s"$self has an inactive status")
      }
    }
  }

  rb uponEvent {
    case ReliableBroadcastDeliver(dest, prop@AtomicBroadcastProposal(_, _, _,event, _)) => handle {
      trigger(ReliableBroadcastRequest(AtomicBroadcastAck(self, dest, prop), List(dest)) -> rb)
    }
    case ReliableBroadcastDeliver(src , AtomicBroadcastAck(_, _, prop@AtomicBroadcastProposal(_, _ ,proposal, _, nodes))) => handle {
      val ack = accepted.getOrElse(proposal, 0)
      val quorum = majority(groupSize)
      if (!(ack >= quorum)) {
        val newAck = ack + 1
        accepted.put(proposal, newAck)
        if (newAck >= quorum) {
          trigger(ReliableBroadcastRequest(AtomicBroadcastCommit(prop), nodes) -> rb)
        }
      }
    }
    case ReliableBroadcastDeliver(dest, AtomicBroadcastCommit(AtomicBroadcastProposal(cs, _, _, op@Op(_, _, _, _, _),  _))) => handle {
      // Latest commit id that the replica knows of
      commit += 1

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
              (op.value, op.refValue) match {
                case (Some(nV), Some(rV)) =>
                  isSame(op.key, nV, rV) match {
                    case true => trigger(NetMessage(self, cs, op.response(store.put(op.key, op.value.getOrElse("")).getOrElse(""), OpCode.Ok)) -> net)
                    case false => trigger(NetMessage(self, cs, op.response(store.getOrElse(op.key, ""), OpCode.Ok)) -> net)
                  }
                case _ =>
                  trigger(NetMessage(self, cs, op.response(OpCode.Error)) -> net)
              }
          }
        case Backup =>
          op.command match {
            case GET =>
            case PUT =>
              store.put(op.key, op.value.getOrElse(""))
            case CAS =>
              (op.value, op.refValue) match {
                case (Some(nV), Some(rV)) =>
                  if (isSame(op.key, nV, rV))
                    store.put(op.key, nV) // Store newValue
                case _ =>
                  log.error("Something wrong happened...")
              }
          }
        case Inactive =>
          log.info("Something went very wrong, we shouldn't be here..")
        case Election =>
          log.info("In election phase, cannot serve requests...")
      }
    }
    case ReliableBroadcastDeliver(dest, newL@NewLeaderProposal(_, _, _, _)) => handle {
      if (!promised) {
        log.info(s"Received NewLeaderProposal $newL")
        if (newL.lastCommit >= commit && newL.epoch >= knownEpoch) {
          promised = true // This replica promises to not accept another leader proposal with given zxid..
          trigger(ReliableBroadcastRequest(NewLeaderAck(self, dest, newL), List(dest)) -> rb)
        }
      }
    }
    case ReliableBroadcastDeliver(dest, nL@NewLeaderAck(_, _, nP@NewLeaderProposal(_,_,_,_))) => handle {
      val ack = electionAcks.getOrElse((nP.src, nP.lastCommit), 0)
      val quorum = majority(groupSize)
      if (!(ack >= quorum)) {
        val newAck = ack + 1
        electionAcks.put((nP.src, nP.lastCommit), newAck)
        if (newAck >= quorum) {
          log.info(s"Commiting newLeaderProposal $nP")
          trigger(ReliableBroadcastRequest(NewLeaderCommit(nP), replicationGroup.toList) -> rb)
        }
      }
    }
    case ReliableBroadcastDeliver(dest, c@NewLeaderCommit(newProp@NewLeaderProposal(_,_,_,_))) => handle {
      primary = Some(newProp.src)

      if (newProp.src.sameHostAs(self)) {
        status = Primary
        epoch = newProp.epoch
        knownEpoch = newProp.epoch
        enablePrimaryTimer()
      } else {
        knownEpoch = newProp.epoch
        epoch += 1
        status = Backup
        enableBackupTimer()
      }

      // Reset...
      promised = false
    }
  }


  private def majority(nodes: Int): Int =
    (nodes/2)+1

  private def enablePrimaryTimer(): Unit = {
    val timeout: Long = cfg.getValue[Long]("id2203.project.primaryValidation") * 2l
    val spt = new SchedulePeriodicTimeout(timeout, timeout)
    spt.setTimeoutEvent(PrimaryValidation(spt))
    trigger(spt -> timer)
    primaryTimerIdIn = Some(spt.getTimeoutEvent.getTimeoutId)

    val notifyBackupsTime: Long = cfg.getValue[Long]("id2203.project.heartbeatsTimer") * 2l
    val sptTwo = new SchedulePeriodicTimeout(notifyBackupsTime, notifyBackupsTime)
    sptTwo.setTimeoutEvent(NotifyBackups(sptTwo))
    trigger(sptTwo -> timer)
    backupTimerIdOut= Some(sptTwo.getTimeoutEvent.getTimeoutId)

  }

  private def enableBackupTimer(): Unit = {
    val timeout: Long = cfg.getValue[Long]("id2203.project.heartbeatsTimer") * 2l
    val spt = new SchedulePeriodicTimeout(timeout, timeout)
    spt.setTimeoutEvent(NotifyPrimary(spt))
    trigger(spt -> timer)
    backupTimerIdOut = Some(spt.getTimeoutEvent.getTimeoutId)
    val validationTimeout: Long = cfg.getValue[Long]("id2203.project.primaryValidation") * 2l
    val sptTwo = new SchedulePeriodicTimeout(validationTimeout, validationTimeout)
    sptTwo.setTimeoutEvent(hasPrimary(sptTwo))
    trigger(sptTwo -> timer)
    backupTimerIdIn = Some(sptTwo.getTimeoutEvent.getTimeoutId)
  }

  // Can be improved...
  private def cancelBackupTimer(): Unit = {
    backupTimerIdIn match {
      case Some(id) =>
        trigger(new CancelPeriodicTimeout(id) -> timer)
        backupTimerIdIn = None
      case None =>
    }

    backupTimerIdOut match {
      case Some(id) =>
        trigger(new CancelPeriodicTimeout(id) -> timer)
        backupTimerIdOut = None
      case None =>
    }
  }

  // Can be improved..
  private def cancelPrimaryTimer(): Unit = {
    primaryTimerIdIn match {
      case Some(id) =>
        trigger(new CancelPeriodicTimeout(id) -> timer)
        primaryTimerIdIn = None
      case None =>
    }

    primaryTimerIdOut match {
      case Some(id) =>
        trigger(new CancelPeriodicTimeout(id) -> timer)
        primaryTimerIdOut = None
      case None =>
    }
  }

  private def isSame(key: String, nV: String, rV:String): Boolean =
    store.get(key)
      .map(_.equals(rV))
      .get
}
