package se.kth.id2203.broadcast.rb

import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}
import se.sics.kompics.network.Network
import se.kth.id2203.networking.{NetAddress, NetMessage}


class ReliableBroadcast extends ComponentDefinition{
  private val reliableBroadcast: NegativePort[ReliableBroadcastPort] = provides[ReliableBroadcastPort]
  private val net: PositivePort[Network] = requires[Network]
  private val self = config.getValue("id2203.project.address", classOf[NetAddress])

  reliableBroadcast uponEvent {
    case request: ReliableBroadcastRequest => handle {
      request.addresses.foreach(address => trigger(NetMessage(self, address, ReliableBroadcastMessage(self, request.event)),net))
    }
  }

  net uponEvent {
    case NetMessage(src, payload: ReliableBroadcastMessage) => handle {
      trigger(ReliableBroadcastDeliver(payload.src, payload.event), reliableBroadcast)
    }
  }
}

