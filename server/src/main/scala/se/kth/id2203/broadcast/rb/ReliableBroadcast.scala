package se.kth.id2203.broadcast.rb

import se.kth.id2203.broadcast.beb.{BestEffortBroadcastDeliver, BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent


class ReliableBroadcast extends ComponentDefinition {
  private val bestEffortBroadcast: PositivePort[BestEffortBroadcastPort] = requires[BestEffortBroadcastPort]
  private val reliableBroadcast: NegativePort[ReliableBroadcastPort] = provides[ReliableBroadcastPort]
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])
  private val delivered = collection.mutable.Set[KompicsEvent]()


  reliableBroadcast uponEvent {
    case request: ReliableBroadcastRequest => handle {
      trigger(BestEffortBroadcastRequest(ReliableBroadcastMessage(request.event, request.addresses), request.addresses) -> bestEffortBroadcast)
    }
  }

  bestEffortBroadcast uponEvent {
    case BestEffortBroadcastDeliver(src , ReliableBroadcastMessage(event, nodes)) => handle {
      if(!delivered.contains(event)) {
        delivered.add(event)
        trigger(ReliableBroadcastDeliver(src, event), reliableBroadcast)
        trigger(BestEffortBroadcastRequest(event, nodes) -> bestEffortBroadcast)
      }
    }
  }
}

