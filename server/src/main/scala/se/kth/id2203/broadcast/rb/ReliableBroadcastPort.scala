package se.kth.id2203.broadcast.rb
import se.sics.kompics.PortType

class ReliableBroadcastPort extends PortType {
  {
    request(classOf[ReliableBroadcastRequest])
    indication(classOf[ReliableBroadcastDeliver])
  }
}
