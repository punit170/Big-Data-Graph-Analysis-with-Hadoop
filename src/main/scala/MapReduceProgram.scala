import NetGraphAlgebraDefs.{Action, GraphPerturbationAlgebra, NetGraph, NetGraphComponent, NetModelAlgebra, NodeObject}
import com.lsc.HelperFunctions.{createYamlFileInHDFS, createYamlFileInLocal}
//import Utilz.CreateLogger
import LoggingUtil.GraphUtil.CreateLogger
import org.apache.hadoop.fs.Path
import org.apache.hadoop.conf.*
import org.apache.hadoop.io.*
import org.apache.hadoop.util.*
import org.apache.hadoop.mapred.*

import java.io.IOException
import java.util
import scala.collection.mutable
import scala.collection.mutable.{ListBuffer, Map}
import scala.jdk.CollectionConverters.*
import com.lsc.HelperFunctions.{ATLMap, DTLMap, EdgesShard, NodePerturbationInfo, Shard, SimRankv_2, appendToYamlSubsection, createYamlFileInHDFS, createYamlFileInLocal, decipherNodes, deserializeEdgeInfo, deserializeMapperOutputValue, deserializeStringShard, findBestNodeMatch, readEdgeInfoHDFSFile, readNodeMatchHDFSFile, serializeEdgeInfo, serializeShardsAsStrings, stringToNodeObject, updateDTLMap, logger}
import org.apache.hadoop.io.*

import java.io.DataInput
import java.io.DataOutput
import scala.collection.immutable
import Variables.GraphConfigReader.*
import org.slf4j.Logger

object MapReduceProgram {
  //val logger: Logger = CreateLogger(classOf[MapReduceProgram.type])

  //Map-Reduce Job1
  class Map extends MapReduceBase with Mapper[LongWritable, Text, Text, Text]:
    private val MapperOutputKey = new Text()
    private val MapperOutputValue = new Text()

    //mapper 1
    @throws[IOException]
    override def map(key: LongWritable, value: Text, output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
      val line: String = value.toString
      print(line)
      val curr_shard = deserializeStringShard(line)._1

      val currSRMap = SimRankv_2(curr_shard.allNnodes, curr_shard.allN_ParentMap, curr_shard.allPnodes, curr_shard.allP_ParentMap)

      currSRMap.foreach(kvpair =>
      {
        MapperOutputKey.set(kvpair._1.toString)
        MapperOutputValue.set(kvpair._2.toString)
        logger.info(s"mapper1 output[ids]:- key: ${kvpair._1.toString} ---val: ${kvpair._2.toString}\n")
        output.collect(MapperOutputKey, MapperOutputValue)
      })

      println()
    }


  //reducer1
  class Reduce extends MapReduceBase with Reducer[Text, Text, Text, Text]:
    private val reducerOutputVal = new Text()

    override def reduce(key: Text, values: util.Iterator[Text], output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
      val NodeU: NodeObject = stringToNodeObject(key.toString)

      val tempStringList = ListBuffer[String]()
      values.asScala.foreach( text =>
        tempStringList += text.toString
      )

      val mergedList: List[(NodeObject, Float)] = tempStringList.foldLeft(List[(NodeObject, Float)]())((acc, aStrList) =>
        acc ++ deserializeMapperOutputValue(aStrList)
      )
      if(mergedList.isEmpty)
        return

      val groupedByNode: immutable.Map[NodeObject, List[(NodeObject, Float)]] = mergedList.groupBy(_._1)

      val intermediateResult: List[(NodeObject, Float)] = groupedByNode.map {
        case (_, entries) =>
          val maxFloatValue = entries.map(_._2).max
          entries.find(_._2 == maxFloatValue).get
      }.toList.filterNot(ele => ele._2 == 0.0).sortBy(-_._2)

      val result1 = intermediateResult.filter(ele => ele._2 == intermediateResult.head._2)

      if(result1.isEmpty)
        reducerOutputVal.set("List(List(NodeObject(-1,0,0,0,0,0,0,0,0),-1.0))")
      else if(result1.length == 1)
        reducerOutputVal.set("List("+result1.head.toString+")")
      else if (result1.length > 1)
        reducerOutputVal.set("List("+findBestNodeMatch(NodeU, result1).toString+")")
      output.collect(key, reducerOutputVal)
      logger.info(s"reducer1 output: key:-${NodeU.toString} --- val: ${reducerOutputVal.toString}")
    }


  //map reduce job2  
  class Map2 extends MapReduceBase with Mapper[LongWritable, Text, Text, Text] :
    private val MapperOutputKey = new Text()
    private val MapperOutputValue = new Text()
    //private val matchMap: immutable.Map[NodeObject, NodeObject] = readNodeMatchHDFSFile(s"$mapReduce1Dir$mapReduce1outputDirPath$mapReduce1outputFileName")
    private val matchMap: immutable.Map[NodeObject, NodeObject] = readNodeMatchHDFSFile(s"$mapReduce1Dir$mapReduce1outputDirPath$mapReduce1outputFileName")

    @throws[IOException]
    //mapper 1
    override def map(key: LongWritable, value: Text, output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
      val line: String = value.toString
      //println(s"line: $line")
      val currEdgeShard: EdgesShard = deserializeEdgeInfo(line)
      //println(s"curredgeshard: $currEdgeShard")
      val allNEdgesList = currEdgeShard.allNEdgesInfo
      val allPEdgesList = currEdgeShard.allPEdgesInfo

      allNEdgesList.foreach{
        case ((sourceN, targetN), valN) =>
          val ueSourceMatchNode = matchMap(sourceN)
          val ueTargetMatchNode = matchMap(targetN)
          val matchPair = allPEdgesList.find{
            case ((sourceP, targetP), _) => sourceP == ueSourceMatchNode && targetP == ueTargetMatchNode
          }

          //Reducer Value is set; No we just need to decide, whether the edge was modified, removed, or added based on Action Cost
          val outputValue = s"${sourceN.id}:${targetN.id}"
          MapperOutputValue.set(outputValue)

          matchPair match {
            case Some(((_, _), valP)) =>

              if valN != valP then
                MapperOutputKey.set("Modified")
                output.collect(MapperOutputKey, MapperOutputValue)
                logger.info(s"mapper2Key: ${MapperOutputKey.toString} - mapper2Val: ${MapperOutputValue.toString}")

                MapperOutputKey.set("Removed")
                output.collect(MapperOutputKey, MapperOutputValue)
                logger.info(s"mapper2Key: ${MapperOutputKey.toString} - mapper2Val: ${MapperOutputValue.toString}")

                MapperOutputKey.set("Added")
                output.collect(MapperOutputKey, MapperOutputValue)
                logger.info(s"mapper2Key: ${MapperOutputKey.toString} <-> mapper2Val: ${MapperOutputValue.toString}")
              else {
                MapperOutputKey.set("Unperturbed")
                output.collect(MapperOutputKey, MapperOutputValue)
                logger.info(s"mapper2Key: ${MapperOutputKey.toString} - mapper2Val: ${MapperOutputValue.toString}")
              }
            case None =>
              MapperOutputKey.set("Removed")
              output.collect(MapperOutputKey, MapperOutputValue)
              logger.info(s"mapper2Key: ${MapperOutputKey.toString} - mapper2Val: ${MapperOutputValue.toString}")
          }
      }

      allPEdgesList.foreach {
        case ((sourceP, targetP), _) =>
          //          if sourceP.id == 47 && targetP.id == 37 then
          //            println("node 47 -----------37")
          val veSourceMatch = matchMap.find { case (_, v) => v == sourceP }.map(_._1)
          val veTargetMatch = matchMap.find { case (_, v) => v == targetP }.map(_._1)

          veSourceMatch match {
            case Some(veSourceMatchNode) =>
              veTargetMatch match {
                case Some(veTargetMatchNode) =>
                  val matchPair = allNEdgesList.find(edgeE => edgeE._1._1 == veSourceMatchNode && edgeE._1._2 == veTargetMatchNode)

                  matchPair match {
                    case Some (((_, _), _) ) =>
                    case None =>
                      val outputValue = s"${sourceP.id}:${targetP.id}"
                      MapperOutputValue.set(outputValue)
                      MapperOutputKey.set("Added")
                      output.collect(MapperOutputKey, MapperOutputValue)
                      logger.info(s"mapper2Key: ${MapperOutputKey.toString} - mapper2Val: ${MapperOutputValue.toString}")
                  }
                case None =>
              }
            case None =>
          }
      }

    }

  //reducer2
  class Reduce2 extends MapReduceBase with Reducer[Text, Text, Text, Text] :
    private val reducerOutputVal = new Text()

    override def reduce(key: Text, values: util.Iterator[Text], output: OutputCollector[Text, Text], reporter: Reporter): Unit = {
      val tempStringList = ListBuffer[String]()
      values.asScala.foreach(text =>
        tempStringList += text.toString
      )
      val mergedList: List[String] = tempStringList.distinct.toList
//      println(s"\n\nmergedList: $mergedList")

      if (mergedList.isEmpty)
//        println("Reducer2- merged list is empty!")
        return
      else
        reducerOutputVal.set(mergedList.toString())
      output.collect(key, reducerOutputVal)
      logger.info(s"\nreducer2Key: $key -- reducer2Val: ${reducerOutputVal.toString}")
    }

}
