package se.kth.id2203.broadcast.rb

import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort}
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class OriginatedData(src: NetAddress, payload: KompicsEvent) extends KompicsEvent

class ReliableBroadcast extends ComponentDefinition{
  private val bestEffortBroadcast: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]
  private val reliableBroadcast: NegativePort[ReliableBroadcastPort] = provides[ReliableBroadcastPort]
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  private val delivered = collection.mutable.Set[KompicsEvent]()


  reliableBroadcast uponEvent {
    case request: ReliableBroadcastRequest => handle {
      trigger(bestEffortBroadcast(self,(request.addresses, request.event)))
    }
  }

  bestEffortBroadcast uponEvent {
    case BestEffortBroadcastDeliver(_, data@OriginatedData(src,payload)) => handle {
      if(!delivered.contains(payload)){
        delivered.add(payload)
        trigger(ReliableBroadcastDeliver(src, payload), reliableBroadcast)
        trigger(bestEffortBroadcast(data.src,data.payload))
      }
    }
  }
}

