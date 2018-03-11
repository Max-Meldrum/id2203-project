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
package se.kth.id2203.simulation.linearisability

import java.util.UUID

import se.kth.id2203.kvstore._
import se.kth.id2203.networking._
import se.kth.id2203.overlay.RouteMsg
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.sl._
import se.sics.kompics.sl.simulator.SimulationResult
import se.sics.kompics.timer.Timer

import scala.collection.mutable;

class LinTestClient extends ComponentDefinition {

  //******* Ports ******
  val net = requires[Network]
  val timer = requires[Timer]
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address")
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address")
  private val pending = mutable.Map.empty[UUID, String]
  val trace = mutable.Queue.empty[Op]
  var traceNo = 0;
  var oP : Op = new Op(C,"",Some(""))
  var qMsgID = oP.id
  ctrl uponEvent {
    case _: Start => handle {
      val messages = SimulationResult[Int]("messages")
      for (i <- 0 to messages) {
        val op = new Op(PUT, s"unit_test$i", Some(s"kth$i"))
        val routeMsg = RouteMsg(op.key, op) // don't know which partition is responsible, so ask the bootstrap server to forward it
        trigger(NetMessage(self, server, routeMsg) -> net)
        trace.enqueue(op)
        pending += (op.id -> op.key)
        logger.info("Sending {}", op)
        SimulationResult += (op.key -> "Sent")

        val opGet = new Op(GET, s"unit_test$i")
        val routeMsg1 = RouteMsg(opGet.key, opGet) // don't know which partition is responsible, so ask the bootstrap server to forward it
        trigger(NetMessage(self, server, routeMsg1) -> net)
        trace.enqueue(opGet)
        pending += (opGet.id -> opGet.key)
        logger.info("Sending {}", opGet)
        SimulationResult += (op.key -> "Sent")
      }
      val op = new Op(QUEUE,"",Some(""))
      qMsgID = op.id
      val routeMsg = RouteMsg(op.key, op)
      trigger(NetMessage(self, server, routeMsg) -> net)
    }
  }

  net uponEvent {
    case NetMessage(header, or @ OpResponse(res, id, status)) => handle {
      logger.debug(s"Got OpResponse: $or")
     var correctTrace = true
      if(id.equals(qMsgID)){
        val tempTrace = trace.clone()
        val nTrace = res.get.asInstanceOf[mutable.Queue[Op]]
        for(i <- 0 to SimulationResult[Int]("messages")*2){
          val opr = tempTrace.dequeue()
          while(!opr.id.equals(nTrace.dequeue().id)){
            if(nTrace.isEmpty){
              correctTrace = false
              println("Not Linearizable")
            }
          }
        }
        traceNo = traceNo + 1
        SimulationResult += (traceNo.toString + self.toString -> correctTrace)
      }
    }
  }
}
