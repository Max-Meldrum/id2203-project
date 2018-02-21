package se.kth.id2203.broadcast

import se.kth.id2203.broadcast.AtomicBroadcast.Proposal
import se.kth.id2203.broadcast.beb.{BestEffortBroadcastPort, BestEffortBroadcastRequest}
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent
import se.sics.kompics.sl.{ComponentDefinition, handle}

object AtomicBroadcast {
  case class Proposal(epoch: Int, commitNumber: Int) extends KompicsEvent with Serializable
  case class Ack(proposal: Proposal) extends KompicsEvent with Serializable
}

/**
  * Two-phase commit based component
  * 1. Proposals are sent in total order
  * 2. ACKs are received in order as well
  * 3. Commits are sent out after receiving a majority of ACKs
  */
class AtomicBroadcast extends ComponentDefinition {
  val ab = provides[AtomicBroadcastPort]
  // beb for now, later tob..
  val beb = requires[BestEffortBroadcastPort]

  // Incremented Integer that is packed with each proposal
  var proposalId = 0

  ab uponEvent {
    case request: AtomicBroadcastRequest => handle {
      val nodes = request.addresses
      val proposal = AtomicBroadcastProposal(Proposal(request.epoch, proposalId), request.event, nodes)
      proposalId+=1
      //trigger(TotalOrderBroadcast(self, proposal), tob)
      val quorum =  majority(nodes)
      /*
      var n = 0
      while (n < quorum) {
        case ack: AtomicBroadcastAck=> handle {
          n+=1
        }
      }
      trigger(AtomicBroadcastCommit(proposal, nodes), tob)
      */
    }
  }


  private def majority(nodes: List[NetAddress]): Int =
    (nodes.size/2)+1
}
