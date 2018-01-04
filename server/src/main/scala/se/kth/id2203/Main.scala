/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import se.kth.id2203.networking._;
import se.sics.kompics.Kompics;
import se.sics.kompics.config._;
import se.sics.kompics.network.netty.serialization.Serializers;
import org.rogach.scallop._

class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
  import ScallopConverters._;

  version("Project18 Scala Server v1.0");
  banner("Usage: <call jar> [OPTIONS]");
  footer("\n");

  val server = opt[NetAddress](descr = "Run in client mode and connect to bootstrap server in <arg> (ip:port)");
  val ip = opt[InetAddress](descr = "Change local ip to <arg> (default from config file)");
  val port = opt[Int](
    validate = (i => (0 < i) && (i < 65535)),
    descr = "Change local port to <arg> (default from config file)");
  verify()
}

object Main {

  Conversions.register(NetAddressConverter);
  Serializers.register(classOf[Serializable], "javaS");

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args);
    // avoid constant conversion of the address by converting once and reassigning
    // sorry Java API  only :(
    val c = Kompics.getConfig().asInstanceOf[Config.Impl];
    val configSelf = c.getValue("id2203.project.address", classOf[NetAddress]);
    assert(configSelf != null, { "No config provided!" }); // it would be in the reference.conf
    val configBuilder = c.modify(UUID.randomUUID());
    val self = (conf.ip.toOption, conf.port.toOption) match {
      case (None, None) => configSelf
      case (cip, cp)    => NetAddress(cip.getOrElse(configSelf.getIp()), cp.getOrElse(configSelf.getPort()))
    };
    configBuilder.setValue("id2203.project.address", self);
    if (conf.server.isSupplied) {
      configBuilder.setValue("id2203.project.bootstrap-address", conf.server());
    }
    val configUpdate = configBuilder.finalise();
    c.apply(configUpdate, ValueMerger.NONE);
    Kompics.createAndStart(classOf[HostComponent]);
    Kompics.logger.info("Kompics started.");
    Kompics.waitForTermination();
    Kompics.logger.info("Kompics was terminated. Exiting...");
  }
}
