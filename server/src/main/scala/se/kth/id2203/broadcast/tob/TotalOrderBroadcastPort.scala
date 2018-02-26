package se.kth.id2203.broadcast.tob

import se.sics.kompics.PortType

class TotalOrderBroadcastPort extends PortType{
  {
    request(classOf[TotalOrderBroadcastRequest])
    indication(classOf[TotalOrderBroadcastDeliver])
  }
}
