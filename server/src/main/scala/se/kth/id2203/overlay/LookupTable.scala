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
package se.kth.id2203.overlay;

import com.larskroll.common.collections._;
import java.util.Collection;
import se.kth.id2203.bootstrapping.NodeAssignment;
import se.kth.id2203.networking.NetAddress;

@SerialVersionUID(0x57bdfad1eceeeaaeL)
class LookupTable extends NodeAssignment with Serializable {

  val partitions = TreeSetMultiMap.empty[Int, NetAddress];

  def lookup(key: String): Iterable[NetAddress] = {
    val keyHash = key.hashCode();
    val partition = partitions.floor(keyHash) match {
      case Some(k) => k
      case None    => partitions.lastKey
    }
    return partitions(partition);
  }

  def getNodes(): Set[NetAddress] = partitions.foldLeft(Set.empty[NetAddress]) {
    case (acc, kv) => acc ++ kv._2
  }

  override def toString(): String = {
    val sb = new StringBuilder();
    sb.append("LookupTable(\n");
    sb.append(partitions.mkString(","));
    sb.append(")");
    return sb.toString();
  }

}

object LookupTable {
  def generate(nodes: Set[NetAddress]): LookupTable = {
    val lut = new LookupTable();
    lut.partitions ++= (0 -> nodes);
    lut
  }
}
