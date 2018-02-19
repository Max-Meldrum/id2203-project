package se.kth.id2203.broadcast.beb

import se.sics.kompics.PortType

class BestEffortBroadcastPort extends PortType {
  {
    request(classOf[BestEffortBroadcastRequest])
    indication(classOf[BestEffortBroadcastDeliver])
  }
}
