package se.kth.id2203.bootstrapping

import se.sics.kompics.KompicsEvent
import se.sics.kompics.sl._;
import se.kth.id2203.networking.NetAddress;

object Bootstrapping extends Port {
  indication[GetInitialAssignments];
  indication[Booted];
  request[InitialAssignments];
}

case class GetInitialAssignments(nodes: Set[NetAddress]) extends KompicsEvent;
case class Booted(assignment: NodeAssignment) extends KompicsEvent;
case class InitialAssignments(assignment: NodeAssignment) extends KompicsEvent;