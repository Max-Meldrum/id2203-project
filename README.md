# ID2203 Project 2018 Starter Code for Kompics Scala

This project contains some code to get you started with the project.
You are encouraged to create your own forks and work on them, modifying everything and anything as you desire it.

## Overview

The project is split into 3 sub parts:

- A common library shared between servers and clients, containing mostly messages and similar shared types
- A server library that manages bootstrapping and membership
- A client library with a simple CLI to interact with a cluster of servers

The bootstrapping procedure for the servers, requires one server to be marked as a bootstrap server, which the other servers (bootstrap clients) check in with, before the system starts up. The bootstrap server also assigns initial partitions.

## Getting Started

`git clone` (your fork of) the repository to your local machine and `cd` into that folder.

Make sure you have [sbt](https://www.scala-sbt.org/) installed.

### Building

Start sbt with

```bash
sbt
```

In the sbt REPL build the project with

```bash
compile
```

You can run the test suite (which includes simulations) with

```bash
test
```

Before running the project you need to create assembly files for the server and client:

```bash
server/assembly
client/assembly
```

### Running

#### Bootstrap Server Node
To run a bootstrap server node execute:

```
java -jar server/target/scala-2.12/server.jar -p 45678
```

This will start the bootstrap server on localhost:45678.

#### Normal Server Node
After you started a bootstrap server on `<bsip>:<bsport>`, again from the `server` directory execute:

```
java -jar server/target/scala-2.12/server.jar -p 45679 -s <bsip>:<bsport>
```
This will start the bootstrap server on localhost:45679, and ask it to connect to the bootstrap server at `<bsip>:<bsport>`.
Make sure you start every node on a different port if they are all running directly on the local machine.

By default you need 3 nodes (including the bootstrap server), before the system will actually generate a lookup table and allow you to interact with it.
The number can be changed in the configuration file (cf. [Kompics docs](http://kompics.sics.se/current/tutorial/networking/basic/basic.html#cleanup-config-files-classmatchers-and-assembly) for background on Kompics configurations).

#### Clients
To start a client (after the cluster is properly running) execute:

```
java -jar client/target/scala-2.12/client.jar -p 56787 -b <bsip>:<bsport>
```

Again, make sure not to double allocate ports on the same machine.

The client will attempt to contact the bootstrap server and give you a small command promt if successful. Type `help` to see the available commands.

## Issues
If you find a bug please create an issue on git, or create a pull request with a fix.

If there are other questions, try to talk to the other students (e.g., in Canvas forums) and only if that doesn't help write me an email at <lkroll@kth.se>. Or, of course, ask at a lab session.