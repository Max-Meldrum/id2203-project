package se.kth.id2203.broadcast.beb

class BestEffortBroadcast {
  import org.slf4j.LoggerFactory
  import se.kth.id2203.networking.{NetMessage,NetAddress}
  import se.sics.kompics.network.Network
  import se.sics.kompics.sl.{ComponentDefinition, NegativePort, PositivePort, handle}

  class BestEffortBroadcast extends ComponentDefinition {
    private val LOG = LoggerFactory.getLogger(classOf[BestEffortBroadcast])

    private val bestEffortBroadcast: NegativePort[BestEffortBroadcastPort] = provides[BestEffortBroadcastPort]
    private val net: PositivePort[Network] = requires[Network]
    private val self = config.getValue("id2203.project.address", classOf[NetAddress])

    bestEffortBroadcast uponEvent {
      case request: BestEffortBroadcastRequest => handle {
        request.addresses.foreach(address => trigger(NetMessage(self, address, BestEffortBroadcastMessage(self, request.event)), net))
      }
    }

    net uponEvent {
      case NetMessage(src, payload: BestEffortBroadcastMessage) => handle {
        trigger(BestEffortBroadcastDeliver(payload.src, payload.event), bestEffortBroadcast)
      }
    }
  }
}
