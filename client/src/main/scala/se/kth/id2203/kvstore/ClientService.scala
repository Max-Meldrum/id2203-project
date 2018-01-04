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
package se.kth.id2203.kvstore;

import java.util.UUID;
import se.kth.id2203.networking._;

import se.kth.id2203.overlay._;
import se.sics.kompics.sl._;
import se.sics.kompics.{ Start, Kompics, KompicsEvent };
import se.sics.kompics.network.Network;
import se.sics.kompics.timer._;
import collection.mutable;
import concurrent.{ Promise, Future };

case class ConnectTimeout(spt: ScheduleTimeout) extends Timeout(spt);
case class OpWithPromise(op: Operation, promise: Promise[OpResponse] = Promise()) extends KompicsEvent;

class ClientService extends ComponentDefinition {

  //******* Ports ******
  val timer = requires[Timer];
  val net = requires[Network];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address");
  private var connected: Option[ConnectAck] = None;
  private var timeoutId: Option[UUID] = None;
  private val pending = mutable.SortedMap.empty[UUID, Promise[OpResponse]];

  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      log.debug(s"Starting client on $self. Waiting to connect...");
      val timeout: Long = (cfg.getValue[Long]("id2203.project.keepAlivePeriod") * 2l);
      val st = new ScheduleTimeout(timeout);
      st.setTimeoutEvent(ConnectTimeout(st));
      trigger (st -> timer);
      timeoutId = Some(st.getTimeoutEvent().getTimeoutId());
      trigger(NetMessage(self, server, Connect(timeoutId.get)) -> net);
      trigger(st -> timer);
    }
  }

  net uponEvent {
    case NetMessage(header, ack @ ConnectAck(id, clusterSize)) => handle {
      log.info(s"Client connected to $server, cluster size is $clusterSize");
      if (id != timeoutId.get) {
        log.error("Received wrong response id! System may be inconsistent. Shutting down...");
        System.exit(1);
      }
      connected = Some(ack);
      val c = new ClientConsole(ClientService.this);
      val tc = new Thread(c);
      tc.start();
    }
    case NetMessage(header, or @ OpResponse(id, status)) => handle {
      log.debug(s"Got OpResponse: $or");
      pending.remove(id) match {
        case Some(promise) => promise.success(or);
        case None          => log.warn(s"ID $id was not pending! Ignoring response.");
      }
    }
  }

  timer uponEvent {
    case ConnectTimeout(_) => handle {
      connected match {
        case Some(ack) => // already connected
        case None => {
          log.error(s"Connection to server $server did not succeed. Shutting down...");
          Kompics.asyncShutdown();
        }
      }
    }
  }

  loopbck uponEvent {
    case OpWithPromise(op, promise) => handle {
      val rm = RouteMsg(op.key, op); // don't know which partition is responsible, so ask the bootstrap server to forward it
      trigger(NetMessage(self, server, rm) -> net);
      pending += (op.id -> promise);
    }
  }

  def op(key: String): Future[OpResponse] = {
    val op = Op(key);
    val owf = OpWithPromise(op);
    trigger(owf -> onSelf);
    owf.promise.future
  }
}
