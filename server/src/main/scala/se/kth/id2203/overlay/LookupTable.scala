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

  val partitions = TreeSetMultiMap.empty[Int, NetAddress]


  /*
  def lookup(key: String): Iterable[NetAddress] = {
    val keyHash = key.hashCode()
    val partition = partitions.floor(keyHash) match {
      case Some(k) => k
      case None => partitions.lastKey
    }
    partitions(partition)
  }
  */

  def lookup(key: String): Iterable[NetAddress] = {
    val keyHash = key.hashCode()
    val maxKey = 150
    val res = keyHash % maxKey

    val partition = res match {
      case x if x < 0 => getPartitionNumber(Math.abs(x))
      case x => getPartitionNumber(x)
    }
    println(s"PARTITION NUMBER: ${partition}")
    partitions(partition)
  }

  /** Currently static for 3 replication groups
    *
    * @param key number between 0-150 etc..
    * @return which partition. 0, 50, 100
    */
  private def getPartitionNumber(key: Int): Int = key match {
    case x if x >= 0 && x < 50 => 0
    case x if x >= 50 && x < 100 => 50
    case x if x >= 100 && x < 150 => 100
  }

  def getNodes(): Set[NetAddress] = partitions.foldLeft(Set.empty[NetAddress]) {
    case (acc, kv) => acc ++ kv._2
  }

  override def toString(): String = {
    val sb = new StringBuilder()
    sb.append("LookupTable(\n")
    sb.append(partitions.mkString(","))
    sb.append(")")
    sb.toString()
  }

}

object LookupTable {
  def generate(nodes: Set[NetAddress]): LookupTable = {
    val lut = new LookupTable()
    lut.partitions ++= (0 -> nodes)
    lut
  }

  /** Initial base case
    * 3 partitions -> 0-50, 51-101, 102-152
    * In each partition, 3 nodes, 1 primary, 2 backups
    * Majority quorum is utilised, so each partition is always replicated onto at least 2 nodes in a replication group.
    * @param keyRange Integer based key space range
    * @param replicationDegree to what degree we should replicate the partition data
    * @param nodes current nodes in the cluster
    * @return LookupTable
    */
  def generate(keyRange: Int, replicationDegree: Int, nodes: Set[NetAddress]): LookupTable = {
    val lut = new LookupTable()
    val partitionMax = replicationDegree * 2 - 1 // initial case: 3 for each partition

    var currentPartition = 0
    var counter = 0
    nodes.foreach {node =>
      lut.partitions.put(currentPartition, node)
      counter += 1
      if (counter == partitionMax) {
        counter = 0
        currentPartition += keyRange
      }
    }
    lut
  }

}
