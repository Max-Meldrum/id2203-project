/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.overlay;

import se.kth.id2203.bootstrapping._
import se.kth.id2203.broadcast.{AtomicBroadcastPort, AtomicBroadcastRequest}
import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastMessage, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.kvstore._
import se.kth.id2203.networking._
import se.kth.id2203.overlay.VSOverlayManager.{Backup, Inactive, Primary, Status}
import se.sics.kompics.sl._
import se.sics.kompics.network.Network
import se.sics.kompics.timer.Timer

import scala.util.Random;

/**
 * The V(ery)S(imple)OverlayManager.
 * <p>
 * Keeps all nodes in a single partition in one replication group.
 * <p>
 * Note: This implementation does not fulfill the project task. You have to
 * support multiple partitions!
 * <p>
 * @author Lars Kroll <lkroll@kth.se>
 */
class VSOverlayManager extends ComponentDefinition {

  //******* Ports ******
  val route = provides(Routing)
  val boot = requires(Bootstrapping)
  val net = requires[Network]
  val timer = requires[Timer]
  val atomicBroadcast = requires[AtomicBroadcastPort]
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address")
  val replicationDegree = cfg.getValue[Int]("id2203.project.replicationDegree")
  val keyRange = cfg.getValue[Int]("id2203.project.keySpaceRange")

  // Set initial replica status to Inactive
  // Primary status will be set through LE
  private var status: Status = Inactive
  private var lut: Option[LookupTable] = None

  // Current epoch, gets increased with new leader..
  private var epoch = 0

  //******* Handlers ******
  boot uponEvent {
    case GetInitialAssignments(nodes) => handle {
      log.info("Generating LookupTable...")
      //val lut = LookupTable.generate(nodes)
      val lut = LookupTable.generate(keyRange, replicationDegree, nodes)
      logger.debug(s"Generated assignments:\n$lut")
      trigger (new InitialAssignments(lut) -> boot)
    }
    case Booted(assignment: LookupTable) => handle {
      log.info("Got NodeAssignment, overlay ready.")
      lut = Some(assignment)
      val group = lut.get.getReplicationGroup(self)
      val isLeader = group.headOption
        .map(_.sameHostAs(self))
        .head

      if (isLeader) {
        log.info(s"I am leader $self for group $group")
        status = Primary
      } else {
        status = Backup
      }
    }
  }

  net uponEvent {
    case NetMessage(header, RouteMsg(key, msg)) => handle {
      val nodes = lut.get
        .lookup(key)
        .toList

      assert(nodes.nonEmpty)
      val target = nodes.head

      logger.debug(s"Got nodes from lookup!:\n$nodes")

      if (nodes.contains(self)) {
        status match {
          case Primary =>
            log.info("Primary got the request...")
            msg match {
              case (op: Op) => {
                op.command match {
                  case GET =>
                    // We assume eventual consistency, read value without contacting quorum..
                    // send request directly to KVStore
                    trigger(AtomicBroadcastRequest(epoch, msg, nodes) -> atomicBroadcast)
                    trigger(NetMessage(header.src, self, msg) -> net)
                  case PUT =>
                    // Blast proposals
                    trigger(AtomicBroadcastRequest(epoch, msg, nodes) -> atomicBroadcast)
                  case CAS =>
                  // Not impl
                  case _ =>
                    log.error(s"Primary $self received unknown operation")
                }

              }
              case _ =>
                log.error(s"Primary $self received unknown NetMessage")
            }
          case Backup =>
            msg match {
              case (op: Op) => {
                op.command match {
                  case GET =>
                    // We assume eventual consistency, read value without contacting quorum..
                    // send request directly to KVStore
                    //trigger(NetMessage(header.src, self, msg) -> net)
                    // But for now forward to primary
                    trigger(NetMessage(header.src, target, RouteMsg(key, msg)) -> net)
                  case PUT =>
                    // Proposal request, forward it to the primary
                    log.info(s"Backup $self forwarding proposal request to primary")
                    trigger(NetMessage(header.src, target, RouteMsg(key, msg)) -> net)
                  case CAS =>
                  // Not impl
                  case _ =>
                    log.error(s"Backup $self received unknown operation")
                }
              }
              case _ =>
                log.error(s"Backup $self received unknown NetMessage")
            }
          case Inactive =>
            log.info(s"$self has an inactive status")
        }
      } else {
        log.info(s"Forwarding message for key $key to $target")
        trigger(NetMessage(header.src, target, msg) -> net)
      }
    }
    case NetMessage(header, msg: Connect) => handle {
      lut match {
        case Some(l) => {
          log.debug("Accepting connection request from ${header.src}")
          val size = l.getNodes().size;
          trigger (NetMessage(self, header.src, msg.ack(size)) -> net)
        }
        case None => log.info("Rejecting connection request from ${header.src}, as system is not ready, yet.")
      }
    }
  }

  route uponEvent {
    case RouteMsg(key, msg) => handle {
      val nodes = lut.get.lookup(key)
      assert(!nodes.isEmpty)
      val i = Random.nextInt(nodes.size)
      val target = nodes.drop(i).head
      log.info(s"Routing message for key $key to $target")
      trigger (NetMessage(self, target, msg) -> net)
    }
  }
}

object VSOverlayManager {
  sealed trait Status
  case object Primary extends Status
  case object Backup extends Status
  case object Inactive extends Status
}
