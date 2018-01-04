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
package se.kth.id2203.networking

import java.net.InetAddress;
import java.net.InetSocketAddress;
import se.sics.kompics.network.{ Address, Header, Msg, Transport };
import se.sics.kompics.KompicsEvent;

object NetAddress {
  def apply(ip: InetAddress, port: Int): NetAddress = {
    val sock = new InetSocketAddress(ip, port);
    return new NetAddress(sock);
  }
}

@SerialVersionUID(0x07d715483507e3f1L)
final case class NetAddress(isa: InetSocketAddress) extends Address with Serializable {
  override def asSocket(): InetSocketAddress = isa;
  override def getIp(): InetAddress = isa.getAddress;
  override def getPort(): Int = isa.getPort;
  override def sameHostAs(other: Address): Boolean = {
    this.isa.equals(other.asSocket());
  }
}

@SerialVersionUID(0x6370ad801a3ed0c7L)
final case class NetHeader(src: NetAddress, dst: NetAddress, proto: Transport) extends Header[NetAddress] with Serializable {
  override def getDestination(): NetAddress = dst;
  override def getProtocol(): Transport = proto;
  override def getSource(): NetAddress = src;
}

@SerialVersionUID(0x5c49aa68999b9d1dL)
final case class NetMessage[C <: KompicsEvent](header: NetHeader, payload: C) extends Msg[NetAddress, NetHeader] with Serializable {
  override def getDestination(): NetAddress = header.dst;
  override def getHeader(): NetHeader = header;
  override def getProtocol(): Transport = header.proto;
  override def getSource(): NetAddress = header.src;
}

object NetMessage {
  def apply[C <: KompicsEvent](src: NetAddress, dst: NetAddress, payload: C): NetMessage[C] = NetMessage(NetHeader(src, dst, Transport.TCP), payload);
  def apply[C <: KompicsEvent](src: NetAddress, dst: NetAddress, proto: Transport, payload: C): NetMessage[C] = NetMessage(NetHeader(src, dst, proto), payload);

}
