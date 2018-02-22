package se.kth.id2203.broadcast.rb

import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort}
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}
import se.kth.id2203.networking.{NetAddress}

class ReliableBroadcast extends ComponentDefinition{
  private val bestEffortBroadcast: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]
  private val reliableBroadcast: NegativePort[ReliableBroadcastPort] = provides[ReliableBroadcastPort]
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  private var seqNum = 0;
  private val delivered = collection.mutable.Set[ReliableBroadcastMessage]()


  reliableBroadcast uponEvent {
    case request: ReliableBroadcastRequest => handle {
      seqNum += 1
      val msg = ReliableBroadcastMessage(self, ReliableBroadcastDeliver(self, request.event), seqNum)
      trigger(bestEffortBroadcast(msg))
    }
  }

  bestEffortBroadcast uponEvent {
    case BestEffortBroadcastDeliver(src, payload: ReliableBroadcastMessage) => handle {
      if(!delivered.contains(payload)){
        delivered.add(payload)
        trigger(ReliableBroadcastDeliver(payload.src, payload.data), reliableBroadcast)
        trigger(bestEffortBroadcast(payload))
      }
    }
  }
}

