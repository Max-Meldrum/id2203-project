package se.kth.id2203.broadcast.tob

import se.kth.id2203.broadcast.rb.ReliableBroadcastPort
import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort}

class TotalOrderBroadcast extends ComponentDefinition{
  private val tob: NegativePort[TotalOrderBroadcastPort] = provides[TotalOrderBroadcastPort]
  private val rb : PositivePort[ReliableBroadcastPort] = requires[ReliableBroadcastPort]

}
