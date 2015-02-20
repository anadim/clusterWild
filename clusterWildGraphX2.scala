import org.apache.spark.graphx._
import org.apache.spark.graphx.util._
import org.apache.spark.graphx.lib._
import org.apache.spark.rdd.RDD
import scala.util.Random
import scala.util.control.Breaks._


import org.apache.log4j.Logger
import org.apache.log4j.Level

Logger.getLogger("org").setLevel(Level.WARN)
Logger.getLogger("akka").setLevel(Level.WARN)


var graph: Graph[Int, Int] = GraphGenerators.rmatGraph(sc, requestedNumVertices = 1e6.toInt, numEdges = 2e6.toInt).mapVertices( (id, _) => -100.toInt )
var unclusterGraph: Graph[(Int), Int] = graph
val epsilon: Double = 1
var vertexRDDs = graph.vertices

var x: Int = 1
var prevRankGraph1: Graph[Int, Int] = null
var prevRankGraph2: Graph[Int, Int] = null
var maxDegree = graph.vertices.sample(false, 1, 1)
var clusterUpdates = graph.vertices.sample(false, 1, 1)
var maxDeg: Int = 0
var randomSet = graph.vertices.sample(false, 1, 1)
var newVertices = graph.vertices.sample(false, 1, 1)

var clusterIDs : RDD[(VertexId, Int)] = null

while (unclusterGraph.vertices.count()>0) {

    maxDegree = unclusterGraph.degrees
    maxDeg = if (maxDegree.count == 0) 1 else maxDegree.toArray.map( x => x._2).max
    randomSet = unclusterGraph.vertices.sample(false, math.min(epsilon/maxDeg,1), scala.util.Random.nextInt(1000))
    while(randomSet.count==0){
        randomSet = unclusterGraph.vertices.sample(false, math.min(epsilon/maxDeg,1), scala.util.Random.nextInt(1000))
    }
    unclusterGraph = unclusterGraph.joinVertices(randomSet)((vId, attr, active) => -1)
    clusterUpdates = unclusterGraph.aggregateMessages[Int](
        triplet => {
            if ( triplet.dstAttr == -100 // if not clustered
                & triplet.srcAttr == -1 // the source is an acti    ve node
                ){ triplet.sendToDst(triplet.srcId.toInt) }
            }, math.min(_ , _)
    )
    newVertices = unclusterGraph.vertices.leftJoin(clusterUpdates) {
      (id, oldValue, newValue) =>
      newValue match {
          case Some(x:Int) => x
          case None => {if (oldValue == -1) -10; else oldValue;}
         }
    }

    unclusterGraph = unclusterGraph.joinVertices(newVertices)((vId, oldAttr, newAttr) => newAttr).subgraph(vpred = (id, attr) => attr == -100).cache()

    newVertices = newVertices.filter(vID_attr => vID_attr._2 != -100)
    if (clusterIDs == null){
        clusterIDs = newVertices
    }else{
        clusterIDs = clusterIDs.union(newVertices)
    }

    System.out.println(s"ClusterWild finished iteration $x.")
    System.out.println(s"MaxDegree $maxDeg.")
    x = x+1
}
