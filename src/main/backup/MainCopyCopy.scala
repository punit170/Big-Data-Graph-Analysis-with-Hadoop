/*
object MainCopyCopy:

        val logger:Logger=CreateLogger(classOf[Main.type])

        def runMainCopy(args:Array[String]):Unit={
import scala.jdk.CollectionConverters.*
    val outGraphFileName=if args.isEmpty then NGSConstants.OUTPUTFILENAME else args(0).concat(NGSConstants.DEFOUTFILEEXT)
            val perturbedOutGraphFileName=outGraphFileName.concat(".perturbed")
            logger.info(s"Output graph file is $outputDirectory$outGraphFileName and its perturbed counterpart is $outputDirectory$perturbedOutGraphFileName")

            val existingGraph=java.io.File(s"$outputDirectory$outGraphFileName").exists
            val g:Option[NetGraph]=if existingGraph then
            logger.warn(s"File $outputDirectory$outGraphFileName is located, loading it up. If you want a new generated graph please delete the existing file or change the file name.")
            NetGraph.load(fileName=s"$outputDirectory$outGraphFileName")
            else
            val config=ConfigFactory.load()
            logger.info("for the main entry")
            config.getConfig("NGSimulator").entrySet().forEach(e=>logger.info(s"key: ${e.getKey} value: ${e.getValue.unwrapped()}"))
            logger.info("for the NetModel entry")
            config.getConfig("NGSimulator").getConfig("NetModel").entrySet().forEach(e=>logger.info(s"key: ${e.getKey} value: ${e.getValue.unwrapped()}"))
            NetModelAlgebra()

            if g.isEmpty then logger.error("Failed to generate a graph. Exiting...")
            else
            logger.info(s"The original graph contains ${g.get.totalNodes} nodes and ${g.get.sm.edges().size()} edges; the configuration parameter specified ${NetModelAlgebra.statesTotal} nodes.")
            if!existingGraph then
            g.get.persist(fileName=outGraphFileName)
            logger.info(s"Generating DOT file for graph with ${g.get.totalNodes} nodes for visualization as $outputDirectory$outGraphFileName.dot")
            g.get.toDotVizFormat(name=s"Net Graph with ${g.get.totalNodes} nodes",dir=outputDirectory,fileName=outGraphFileName,outputImageFormat=Format.DOT)
            logger.info(s"A graph image file can be generated using the following command: sfdp -x -Goverlap=scale -Tpng $outputDirectory$outGraphFileName.dot > $outputDirectory$outGraphFileName.png")
            end if
            logger.info("Perturbing the original graph to create its modified counterpart...")
            val perturbation:GraphPerturbationAlgebra#GraphPerturbationTuple=GraphPerturbationAlgebra(g.get.copy)
            perturbation._1.persist(fileName=perturbedOutGraphFileName)

            logger.info(s"Generating DOT file for graph with ${perturbation._1.totalNodes} nodes for visualization as $outputDirectory$perturbedOutGraphFileName.dot")
            perturbation._1.toDotVizFormat(name=s"Perturbed Net Graph with ${perturbation._1.totalNodes} nodes",dir=outputDirectory,fileName=perturbedOutGraphFileName,outputImageFormat=Format.DOT)
            logger.info(s"A graph image file for the perturbed graph can be generated using the following command: sfdp -x -Goverlap=scale -Tpng $outputDirectory$perturbedOutGraphFileName.dot > $outputDirectory$perturbedOutGraphFileName.png")

            val modifications:ModificationRecord=perturbation._2
            GraphPerturbationAlgebra.persist(modifications,outputDirectory.concat(outGraphFileName.concat(".yaml")))match
            case Left(value)=>logger.error(s"Failed to save modifications in ${outputDirectory.concat(outGraphFileName.concat(".yaml"))} for reason $value")
            case Right(value)=>
            logger.info(s"Diff yaml file ${outputDirectory.concat(outGraphFileName.concat(".yaml"))} contains the delta between the original and the perturbed graphs.")
            logger.info(s"Done! Please check the content of the output directory $outputDirectory")

            val inNetGraphFileName:String=outGraphFileName
            val perturbedGraphFileName:String=perturbedOutGraphFileName
            val inputDirectory=outputDirectory

            val loadedNetGraph:Option[NetGraph]=NetGraph.load(inNetGraphFileName,inputDirectory)
            val loadedPerturbedGraph:Option[NetGraph]=NetGraph.load(perturbedGraphFileName,inputDirectory)

            //loadedNetGraph.compare(loadedPerturbedGraph)
            val matchMap=Map[(Int,Int),Float]()

            def SimRank(NG:NetGraph,PG:NetGraph):(Array[NodeObject],Array[NodeObject],Array[Array[Float]])={
            val NGsm=NG._1
            val PGsm=PG._1

            val NGNodes=NGsm.nodes().asScala.toArray
            val PGNodes=PGsm.nodes().asScala.toArray

            NGNodes.foreach(u=>PGNodes.foreach(v=>matchMap+((u.id,v.id)->0.0)))

            val NGNodesMap=NGNodes.zipWithIndex.map{
            case(node,index)=>
            node->index
            }.toMap

            val PGNodesMap=PGNodes.zipWithIndex.map{
            case(node,index)=>
            node->index
            }.toMap

            val SRMatrix:Array[Array[Float]]=Array.ofDim[Float](NGNodes.length,PGNodes.length)

            NGNodes.indices.foreach(i=>
            PGNodes.indices.foreach(j=>
            {
            if NGNodes(i)==PGNodes(j)then{SRMatrix(i)(j)=1.0f;matchMap((NGNodes(i).id,PGNodes(j).id))=1.0f}
            else{SRMatrix(i)(j)=0.0f;matchMap((NGNodes(i).id,PGNodes(j).id))=0.0f}
            }
            //else if NGsm.predecessors(NGNodes(i)).size == 0 || PGsm.predecessors(PGNodes(j)).size == 0 then SRMatrix(i)(j) = 1
            )
            )
            NGNodes.indices.foreach(i=>
            PGNodes.indices.foreach(j=>
            val nodeU=NGNodes(i)
            val nodeV=PGNodes(j)

            if(nodeU!=nodeV)
            {
            val inNodesU=NGsm.predecessors(nodeU).asScala.toArray
            val inNodesV=PGsm.predecessors(nodeV).asScala.toArray

            if(inNodesU.length==0||inNodesV.length==0)then{
            SRMatrix(i)(j)=0
            matchMap((NGNodes(i).id,PGNodes(j).id))=0.0f
            }
            else{
            val coeffPart:Float=1.0f/(inNodesU.length*inNodesV.length)

            val combos=ListBuffer[(NodeObject,NodeObject)]()
            inNodesU.foreach(u=>inNodesV.foreach(v=>combos+=((u,v))))

            val sumPart=combos.foldLeft(0.0f){case(acc,(u,v))=>
            acc+SRMatrix(NGNodesMap(u))(PGNodesMap(v))
            }
            SRMatrix(i)(j)=coeffPart*sumPart
            matchMap((NGNodes(i).id,PGNodes(j).id))=SRMatrix(i)(j)
            }
            }
            )
            )
            (NGNodes,PGNodes,SRMatrix)
            }

            def findMaxIndices(arr:Array[Float]):List[Int]={
            val maxIndices=arr.zipWithIndex.foldLeft(List.empty[Int]){
            case(acc,(value,index))if(value!=0)&&(acc.isEmpty||value>arr(acc.head))=>
            List(index)
            case(acc,(value,index))if(value!=0)&&value==arr(acc.head)=>
            index::acc
            case(acc,_)=>acc
            }
            return maxIndices
            }

            def findBestMatch(u:NodeObject,matchingIndices:List[Int],PGNodes:Array[NodeObject]):Int={
            //children, props,maxDepth, maxProperties
            for(j<-matchingIndices.indices){
        val v=PGNodes(matchingIndices(j))
        if(u==v)then return matchingIndices(j)
        }

        val scores:Array[Int]=Array.fill(matchingIndices.length)(0)

        for(j<-matchingIndices.indices){
        val v=PGNodes(matchingIndices(j))

        if(u.children==v.children)then scores(j)+=1
        if(u.props==v.props)then scores(j)+=1
        if(u.maxDepth==v.maxDepth)then scores(j)+=1
        if(u.maxProperties==v.maxProperties)then scores(j)+=1
        }

        val maxIndex=scores.zipWithIndex.reduceLeft{(a,b)=>
        if(a._1>b._1)a else b
        }._2
        return matchingIndices(maxIndex)
        }

        def setRowToZero(matrix:Array[Array[Float]],rowNumber:Int):Unit={
        if(rowNumber>=0&&rowNumber<matrix.length){
        for(col<-0until matrix(rowNumber).length){
        matrix(rowNumber)(col)=0.0
        }
        }else{
        throw new IllegalArgumentException("Invalid row number")
        }
        }

        def setColumnToZero(matrix:Array[Array[Float]],colNumber:Int):Unit={
        if(colNumber>=0&&matrix.nonEmpty&&colNumber<matrix.head.length){
        for(row<-matrix.indices){
        matrix(row)(colNumber)=0.0
        }
        }else{
        throw new IllegalArgumentException("Invalid column number")
        }
        }

    /*def decipherSRMatrix(NGNodes: Array[NodeObject], PGNodes: Array[NodeObject], srmCopy: Array[Array[Float]]) = {

      val removedNodes = ListBuffer[Int]()
      val matchNodesMap = Map[Int, Int]()
      val modifiedNodes = ListBuffer[Int]()
      val addedNodes = ListBuffer[Int]()

      for (i <- NGNodes.indices) {
        val row = srmCopy(i)
        val maxIndices = findMaxIndices(row)
        if maxIndices.isEmpty then removedNodes += NGNodes(i).id
        else
        {
          val bestIndex = findBestMatch(NGNodes(i), maxIndices, PGNodes)
          if (NGNodes(i) == PGNodes(bestIndex)) then
            matchNodesMap += NGNodes(i).id -> PGNodes(bestIndex).id
            setColumnToZero(srmCopy, bestIndex)
          else
           {
              val tenousColumn = NGNodes.indices.foldLeft(Array[Float]())((acc, i) => acc :+ srmCopy(i)(bestIndex))
              val tenousColBestIndex = findBestMatch(PGNodes(bestIndex), findMaxIndices(tenousColumn), NGNodes)
              if i != tenousColBestIndex then
                removedNodes += NGNodes(i).id
              else
                modifiedNodes += NGNodes(i).id
           }

          setRowToZero(srmCopy, i)
        }

      }

      val matchedPGNodesIds = matchNodesMap.values.toList

      for (j <- PGNodes.indices) {
        if (!matchedPGNodesIds.contains(PGNodes(j).id) && !modifiedNodes.contains(PGNodes(j).id)) then addedNodes += PGNodes(j).id

      }

      val removedAndAdded = removedNodes.intersect(addedNodes)
      val updatedRemovedNodes = removedNodes.diff(removedAndAdded)
      val updatedAddedNodes = addedNodes.diff(removedAndAdded)
      val updatedModifiedNodes = (modifiedNodes ++ removedAndAdded).distinct

      println("RemovedNodes:")
      println(updatedRemovedNodes)

      println("MatchedNodesMap:")
      println(matchNodesMap)

      println("AddedNodes:")
      println(updatedAddedNodes)

      println("modifiedNodes:")
      println(updatedModifiedNodes)
    }*/

        def decipherSRMatrix(NG:NetGraph,PG:NetGraph,NGNodes:Array[NodeObject],PGNodes:Array[NodeObject],srmCopy:Array[Array[Float]])={
        val NG_sm=NG.sm
        val PG_sm=PG.sm

        val removedNodes=ListBuffer[NodeObject]()
        val matchNodesMap=Map[NodeObject,NodeObject]()
        val modifiedNodesMap=Map[NodeObject,NodeObject]()
        val addedNodes=ListBuffer[NodeObject]()

        for(i<-NGNodes.indices){
        val row=srmCopy(i)
        val maxIndices=findMaxIndices(row)
        if maxIndices.isEmpty then removedNodes+=NGNodes(i)
        else{
        val bestIndex=findBestMatch(NGNodes(i),maxIndices,PGNodes)
        if(NGNodes(i)==PGNodes(bestIndex))then
        matchNodesMap+=NGNodes(i)->PGNodes(bestIndex)
        setColumnToZero(srmCopy,bestIndex)
        else{
        val tenousColumn=NGNodes.indices.foldLeft(Array[Float]())((acc,i)=>acc:+srmCopy(i)(bestIndex))
        val tenousColBestIndex=findBestMatch(PGNodes(bestIndex),findMaxIndices(tenousColumn),NGNodes)
        if i!=tenousColBestIndex then
        removedNodes+=NGNodes(i)
        else
        modifiedNodesMap+=NGNodes(i)->PGNodes(bestIndex)
        }

        setRowToZero(srmCopy,i)
        }

        }

        val matchedPGNodes=matchNodesMap.values.toList
        val modifiedPGNodes=modifiedNodesMap.values.toList

        for(j<-PGNodes.indices){
        if(!matchedPGNodes.contains(PGNodes(j))&&!modifiedPGNodes.contains(PGNodes(j)))then addedNodes+=PGNodes(j)

        }

        val removedEdges=ListBuffer[(NodeObject,NodeObject)]()
        val addedEdges=ListBuffer[(NodeObject,NodeObject)]()
        val modifiedEdges=ListBuffer[(NodeObject,NodeObject)]()

        for(rn<-removedNodes){
        val associatedEdges=NG_sm.incidentEdges(rn)
        associatedEdges.asScala.toList.foreach(ae=>removedEdges+=ae.source()->ae.target())
        }

        for(an<-addedNodes){
        val associatedEdges=PG_sm.incidentEdges(an)
        associatedEdges.asScala.toList.foreach(ae=>addedEdges+=ae.source()->ae.target())
        }

        val otherNodesMap=matchNodesMap++modifiedNodesMap

        for(on<-otherNodesMap){
        val uAssEdges=NG_sm.incidentEdges(on._1).asScala.toList
        val vAssEdges=PG_sm.incidentEdges(on._2).asScala.toList

        for(ue<-uAssEdges){
        if!(removedNodes.contains(ue.source())||removedNodes.contains(ue.target()))then
        //          val veCheck = vAssEdges.find(v => ue == v)
        val ueSourceMatchNode=otherNodesMap(ue.source())
        val ueTargetMatchNode=otherNodesMap(ue.target())
        val matchPair=PG_sm.hasEdgeConnecting(ueSourceMatchNode,ueTargetMatchNode)
        matchPair match{
        case true=>
        if(NG_sm.edgeValue(ue.source(),ue.target())!=PG_sm.edgeValue(ueSourceMatchNode,ueTargetMatchNode))then
        modifiedEdges+=ue.source()->ue.target()
        addedEdges+=ue.source()->ue.target()
        removedEdges+=ue.source()->ue.target()
        else{}
        case false=>
        removedEdges+=ue.source()->ue.target()
        }
        }
        for(ve<-vAssEdges){
        if!(addedNodes.contains(ve.source())||addedNodes.contains(ve.target()))then
        val veSourceMatch=otherNodesMap.find{case(_,v)=>v==ve.source()}.map(_._1)
        val veTargetMatch=otherNodesMap.find{case(_,v)=>v==ve.target()}.map(_._1)
        veSourceMatch match{
        case Some(veSourceMatchNode)=>
        veTargetMatch match{
        case Some(veTargetMatchNode)=>
        if!(NG_sm.hasEdgeConnecting(veSourceMatchNode,veTargetMatchNode))then
        addedEdges+=ve.source()->ve.target()
        case None=>{}
        }
        case None=>{}
        }
        }
        }


        println("MatchedNodesMap:")
        matchNodesMap.map((n1,n2)=>print(s"${n1.id} -> ${n2.id},"))

        println("\nmodifiedNodes:")
        modifiedNodesMap.map((n1,_)=>print(s"${n1.id}, "))

        println("\nRemovedNodes:")
        removedNodes.map(n=>print(s"${n.id}, "))

        println("\nAddedNodes:")
        addedNodes.map(n=>print(s"${n.id}, "))

        println("\n\nmodifiedEdges:")
        modifiedEdges.toSet.map((n1,n2)=>print(s"${n1.id}:${n2.id}, "))

        println("\nAddedEdges:")
        addedEdges.toSet.map((n1,n2)=>print(s"${n1.id}:${n2.id}, "))

        println("\nRemovedEdges:")
        removedEdges.toSet.map((n1,n2)=>print(s"${n1.id}:${n2.id}, "))
        }

        def cutGraph(graph:NetGraph,numOfParts:Int):List[NetGraph]={

        def getDepthMap(graph:NetGraph):Map[NodeObject,Int]={
        val allNodes=graph.sm.nodes().asScala.toSet
        val visitedNodes=Set[NodeObject]()
        val depthMap=Map[NodeObject,Int]()

        def calculateDepthMap(currNode:NodeObject,currDepth:Int):Unit={
        // Process the current node here
        if!(visitedNodes.contains(currNode))then{
        visitedNodes+=currNode
        depthMap+=(currNode->currDepth)

        // Get successors and recursively process them
        val successors=graph.sm.successors(currNode).asScala
        successors.foreach(successor=>calculateDepthMap(successor,currDepth+1))
        }
        }

        val node0=allNodes.find(_.id==0).get
        calculateDepthMap(node0,0)
        allNodes.foreach(n=>if!(visitedNodes.contains(n))then calculateDepthMap(n,0))

        return depthMap
        }

        val graphSm=graph.sm
        val capacityPerSubgraph:Int=graph.sm.nodes().size()/numOfParts

        val subgraphs=ListBuffer[NetGraph]()
        val visitedNodes=Set[NodeObject]()
        val bfsQueue=Queue[NodeObject]()
        val graphDepthMap=getDepthMap(graph)

        // Define a BFS traversal function
        def bfsTraversal(startNode:NodeObject):NetGraph={
        val subgraphNodes=ListBuffer[NodeObject]()
        //NGsm.predecessors(startNode).asScala.toList.foreach(pn => subgraphNodes += pn)
        bfsQueue.enqueue(startNode)
        visitedNodes+=startNode

        while(bfsQueue.nonEmpty&&subgraphNodes.size<capacityPerSubgraph){
        val currentNode=bfsQueue.dequeue()
        subgraphNodes+=currentNode

        val currNodeSuccessors=graphSm.successors(currentNode).asScala.toList

        // Add neighboring nodes to the BFS queue
        for(neighbor<-currNodeSuccessors){
        if!visitedNodes.contains(neighbor)then{
        bfsQueue.enqueue(neighbor)
        visitedNodes+=neighbor
        }
        }

        // Continue BFS traversal from unvisited nodes
        if(bfsQueue.isEmpty)then{
        val remainingUnvisitedNodes=graphSm.nodes().asScala.filterNot(visitedNodes.contains)
        if(remainingUnvisitedNodes.nonEmpty){
        val unvisitedDepthMap=graphDepthMap.filter((n,_)=>remainingUnvisitedNodes.contains(n))
        val minDepth=unvisitedDepthMap.values.min
        val nodesWithMinDepth=unvisitedDepthMap.filter{case(_,depth)=>depth==minDepth}.keys.toList
        val nextMinDepthNode=nodesWithMinDepth.head
        bfsQueue.enqueue(nextMinDepthNode)
        visitedNodes+=nextMinDepthNode
        }
        }
        }

        //bfsQueue.foreach(n => NGsm.predecessors(n).asScala.toList.foreach(pn => subgraphNodes += pn))
        // Create an induced subgraph from the shardBuilder
        if(subgraphs.nonEmpty)then
        val lastSubgraphNodes=subgraphs.last.sm.nodes().asScala.toList
        val lastSubgraphSuccessors=lastSubgraphNodes.foldLeft(Set[NodeObject]())((acc,n)=>acc++graphSm.successors(n).asScala.toSet)
        val currSubgraphPredecessors=subgraphNodes.foldLeft(Set[NodeObject]())((acc,n)=>acc++graphSm.predecessors(n).asScala.toSet)
        val commonNodes=lastSubgraphSuccessors.intersect(currSubgraphPredecessors).to(ListBuffer)

        val finalSubgraphNodes=commonNodes++subgraphNodes
        val subgraph=Graphs.inducedSubgraph(graphSm,(finalSubgraphNodes+=graphSm.nodes().asScala.toList.find(_.id==0).get).asJava)
        val Netsubgraph=NetGraph(subgraph,startNode)
        return Netsubgraph
        else
        val subgraph=Graphs.inducedSubgraph(graphSm,(subgraphNodes+=graphSm.nodes().asScala.toList.find(_.id==0).get).asJava)
        val Netsubgraph=NetGraph(subgraph,startNode)
        return Netsubgraph
        }

        //val loop = new Breaks()
        bfsQueue.enqueue(graph.initState)
        //NGsm.successors(graph.initState).asScala.toList.foreach(cn => bfsQueue.enqueue(cn))

        while(bfsQueue.nonEmpty){
        val currStartNode=bfsQueue.dequeue()
        val netsubgraph=bfsTraversal(currStartNode)
        subgraphs+=netsubgraph
        }

        println("DepthMap:")
        getDepthMap(graph).foreach(pair=>println(s"${pair._1.id} -> ${pair._2}"))
        println()
        return subgraphs.toList
        }

        case

class ShardContanier(originalGraph:List[NetGraphComponent], perturbedGraph:List[NetGraphComponent])

    def serializeShard(nGraph:NetGraph,pGraph:NetGraph,dir:String=outputDirectory,fileName:String=NGSConstants.OUTPUTFILENAME):Unit={
        import java.io.*
        import java.nio.charset.StandardCharsets.UTF_8
        import java.util.Base64

        val ngsm=nGraph.sm
        val pgsm=pGraph.sm

        val fullnGraphAsList:List[NetGraphComponent]=ngsm.nodes().asScala.toList:::ngsm.edges().asScala.toList.map{edge=>
        ngsm.edgeValue(edge.source(),edge.target()).get
        }
        val fullpGraphAsList:List[NetGraphComponent]=pgsm.nodes().asScala.toList:::pgsm.edges().asScala.toList.map{edge=>
        pgsm.edgeValue(edge.source(),edge.target()).get
        }

        val oneShard=ShardContanier(fullnGraphAsList,fullpGraphAsList)

        Try(new FileOutputStream(s"$dir$fileName",false)).map(fos=>new ObjectOutputStream(fos)).map{oos=>
        oos.writeObject(oneShard)
        oos.flush()
        oos.close()
        }.map(_=>NetGraph.logger.info(s"Successfully persisted the graph to $dir$fileName"))
        .recover{case e=>NetGraph.logger.error(s"Failed to persist the graph to $dir$fileName : ",e)}
        }

        def deserializeShard(fileName:String,dir:String=outputDirectory):(Option[NetGraph],Option[NetGraph])={
        logger.info(s"Loading the NetGraph from $dir$fileName")
        import java.io.*

        val nGraph=Try(new FileInputStream(s"$dir$fileName")).map(fis=>(fis,new ObjectInputStream(fis))).map{(fis,ois)=>
        val oneShard=ois.readObject.asInstanceOf[ShardContanier]
        ois.close()
        fis.close()
        oneShard._1
        }.toOption.flatMap{
        lstOfNetComponents=>
        val nodes=lstOfNetComponents.collect{case node:NodeObject=>node}
        val edges=lstOfNetComponents.collect{case edge:Action=>edge}
        NetModelAlgebra(nodes,edges)
        }
        val pGraph=Try(new FileInputStream(s"$dir$fileName")).map(fis=>(fis,new ObjectInputStream(fis))).map{(fis,ois)=>
        val oneShard=ois.readObject.asInstanceOf[ShardContanier]
        ois.close()
        fis.close()
        oneShard._2
        }.toOption.flatMap{
        lstOfNetComponents=>
        val nodes=lstOfNetComponents.collect{case node:NodeObject=>node}
        val edges=lstOfNetComponents.collect{case edge:Action=>edge}
        NetModelAlgebra(nodes,edges)
        }

        (nGraph,pGraph)
        }

        def serializeShardAsString(nGraphs:List[NetGraph],pGraphs:List[NetGraph],dir:String=outputDirectory,fileName:String=NGSConstants.OUTPUTFILENAME):Unit={
        import java.io.*
        import java.nio.charset.StandardCharsets.UTF_8
        import java.util.Base64

        val FOS=new FileOutputStream(s"$dir$fileName",false)
        val OOS=new ObjectOutputStream(FOS)

        for(nGraph<-nGraphs){
        for(pGraph<-pGraphs){
        val ngsm=nGraph.sm
        val pgsm=pGraph.sm

        val allNGraphNodesAsString:String=ngsm.nodes().asScala.toList.toString()
        val allNGraphEdgesAsString=ngsm.edges().asScala.toList.map(e=>e.toString)
        val allPGraphNodesAsString:String=pgsm.nodes().asScala.toList.toString()
        val allPGraphEdgesAsString=pgsm.edges().asScala.toList.map(e=>e.toString)

        val nodesEdgesDelimiter=":"
        val ngpgDelimiter=";"

        val oneCombinedString=allNGraphNodesAsString+nodesEdgesDelimiter+allNGraphEdgesAsString+ngpgDelimiter+allPGraphNodesAsString+nodesEdgesDelimiter+allPGraphEdgesAsString+'\n'
        //val oneShard = ShardContanier(fullnGraphAsList, fullpGraphAsList)

        OOS.writeBytes(oneCombinedString)
        OOS.flush()
        }
        }
        OOS.close()
        }

        case

class Shard(allNnodes:List[NodeObject], allN_ParentMap:immutable.Map[NodeObject, List[NodeObject]],
            allPnodes:List[NodeObject], allP_ParentMap:immutable.Map[NodeObject, List[NodeObject]])

    def deserializeStringShard(shard_string:String):(Shard)={

        def stringToNodeObject(strObj:String):NodeObject={
        val regexPattern="""\d+(\.\d+)?""".r
        val nodeFields=regexPattern.findAllIn(strObj).toArray
        assert(nodeFields.length==9)
        //NodeObject(id: Int, children: Int, props: Int, currentDepth: Int = 1, propValueRange:Int,
        // maxDepth:Int, maxBranchingFactor:Int, maxProperties:Int, storedValue: Double)

        val id=nodeFields(0).toInt
        val children=nodeFields(1).toInt
        val props=nodeFields(2).toInt
        val currentDepth=nodeFields(3).toInt
        val propValueRange=nodeFields(4).toInt
        val maxDepth=nodeFields(5).toInt
        val maxBranchingFactor=nodeFields(6).toInt
        val maxProperties=nodeFields(7).toInt
        val storedValue=nodeFields(8).toDouble

        NodeObject(id,children,props,currentDepth,propValueRange,maxDepth,maxBranchingFactor,maxProperties,storedValue)
        }

      /*
            logger.info(s"Loading the NetGraph from $dir$fileName")
            import java.io.{BufferedReader, BufferedWriter, ByteArrayInputStream, File, FileInputStream, FileReader, FileWriter, ObjectInputStream}
            import java.io.DataInput
      
            val FIL = new FileInputStream(s"$dir$fileName")
            val OIS = new ObjectInputStream(FIL)
      
            val shard_string = OIS.readLine()
      */

        val tempStrArr=shard_string.split(";").map(substr=>substr.split(":"))

        val allNGraphNodesAsString=tempStrArr(0)(0).substring(5,tempStrArr(0)(0).length-1)
        val allNGraphEdgesAsString=tempStrArr(0)(1).substring(5,tempStrArr(0)(1).length-1)
        val allPGraphNodesAsString=tempStrArr(1)(0).substring(5,tempStrArr(1)(0).length-1)
        val allPGraphEdgesAsString=tempStrArr(1)(1).substring(5,tempStrArr(1)(1).length-1)

        val allNGraphNodesAsStringList="""NodeObject\([^)]+\)""".r.findAllIn(allNGraphNodesAsString).toList
        val allNGraphEdgesAsStringList="""NodeObject\([0-9.,]+\) -> NodeObject\([0-9.,]+\)""".r.findAllIn(allNGraphEdgesAsString).toList
        val allPGraphNodesAsStringList="""NodeObject\([^)]+\)""".r.findAllIn(allPGraphNodesAsString).toList
        val allPGraphEdgesAsStringList="""NodeObject\([0-9.,]+\) -> NodeObject\([0-9.,]+\)""".r.findAllIn(allPGraphEdgesAsString).toList


        val tempNGraphAdjacencyMap=Map[String,ListBuffer[String]]()
        allNGraphEdgesAsStringList.foreach{str=>
        val Array(parent,child)=str.split("->")
        tempNGraphAdjacencyMap.getOrElseUpdate(child,mutable.ListBuffer.empty)+=parent
        }
        val NGraphAdjacencyMap:immutable.Map[String,List[String]]=tempNGraphAdjacencyMap.foldLeft(immutable.Map[String,List[String]]())((acc,m)=>acc+(m._1->m._2.toList))

        val tempPGraphAdjacencyMap=Map[String,ListBuffer[String]]()
        allPGraphEdgesAsStringList.foreach{str=>
        val Array(parent,child)=str.split("->")
        tempPGraphAdjacencyMap.getOrElseUpdate(child,mutable.ListBuffer.empty)+=parent
        }
        val PGraphAdjacencyMap:immutable.Map[String,List[String]]=tempPGraphAdjacencyMap.foldLeft(immutable.Map[String,List[String]]())((acc,m)=>acc+(m._1->m._2.toList))


        val allNGraphNodes:List[NodeObject]=allNGraphNodesAsStringList.map(nodeStr=>stringToNodeObject(nodeStr))
        val allPGraphNodes:List[NodeObject]=allPGraphNodesAsStringList.map(nodeStr=>stringToNodeObject(nodeStr))
        val allNGraphParentMap:immutable.Map[NodeObject,List[NodeObject]]=NGraphAdjacencyMap.foldLeft(immutable.Map[NodeObject,List[NodeObject]]())((acc,m)=>acc+(stringToNodeObject(m._1)->m._2.map(nodeStr=>stringToNodeObject(nodeStr))))
        val allPGraphParentMap:immutable.Map[NodeObject,List[NodeObject]]=PGraphAdjacencyMap.foldLeft(immutable.Map[NodeObject,List[NodeObject]]())((acc,m)=>acc+(stringToNodeObject(m._1)->m._2.map(nodeStr=>stringToNodeObject(nodeStr))))

        Shard(allNGraphNodes,allNGraphParentMap,allPGraphNodes,allPGraphParentMap)

        }

        def SimRank_v2(NgNodes:List[NodeObject],NgParentMap:immutable.Map[NodeObject,List[NodeObject]],PgNodes:List[NodeObject],PgParentMap:immutable.Map[NodeObject,List[NodeObject]]):Map[(NodeObject,NodeObject),Float]={

        val SRMap=Map[(NodeObject,NodeObject),Float]()

        NgNodes.foreach(nNode=>
        PgNodes.foreach(pNode=>{
        if nNode==pNode then{
        SRMap+=(nNode,pNode)->1.0f
        }
        else{
        SRMap+=(nNode,pNode)->0.0f
        }
        }
        )
        )
        NgNodes.foreach(nNode=>
        PgNodes.foreach(pNode=>

        if(nNode!=pNode){
        val nParentNodes=NgParentMap.get(nNode)
        val pParentNodes=PgParentMap.get(pNode)

        nParentNodes match
        case Some(nParentList)=>
        pParentNodes match
        case Some(pParentList)=>
        val coeffPart:Float=1.0f/(nParentList.length*pParentList.length)

        val combos=ListBuffer[(NodeObject,NodeObject)]()
        nParentList.foreach(nParentNode=>pParentList.foreach(pParentNode=>combos+=((nParentNode,pParentNode))))

        val sumPart=combos.foldLeft(0.0f){case(acc,(nParentNode,pParentNode))=>
        acc+SRMap(nParentNode,pParentNode)
        }
        SRMap((nNode,pNode))=coeffPart*sumPart
        case None=>
        SRMap+=(nNode,pNode)->0.0f
        case None=>
        SRMap+=(nNode,pNode)->0.0f

        }
        )
        )
        SRMap
        }

        loadedNetGraph match
        case Some(netgraph:NetGraph)=>
        loadedPerturbedGraph match
        case Some(perturbedGraph:NetGraph)=>{
        val NGsm=netgraph._1
        val PGsm=perturbedGraph._1
        val nodeIdList:List[Int]=NGsm.nodes().asScala.toList.map(node=>node.id)
        println(nodeIdList)

        //              val FromNodesNGList: List[Int] = NGsm.edges().asScala.toList.map(ep => ep.source().id)
        //              val ToNodesNGList: List[Int] = NGsm.edges().asScala.toList.map(ep => ep.target().id)

        val NGEdges=NGsm.edges().asScala.toList
        val PGEdges=PGsm.edges().asScala.toList

        val NGNodes=NGsm.nodes().asScala.toList
        val PGNodes=PGsm.nodes().asScala.toList

        val NGNodeIds=NGNodes.map(n=>n.id)
        val PGNodeIds=PGNodes.map(n=>n.id)
        //              val uncommonEdges = (NGEdges union PGEdges) diff (NGEdges intersect PGEdges)
        //              println("Uncommon Edges:")
        //              println(uncommonEdges)

        //              val modifiedNodes = (NGNodes intersect PGNodes).
        val removedNodesIds=NGNodeIds.foldLeft(List[Int]())(
        (acc,nodeid)=>if!PGNodeIds.exists(_==nodeid)then acc:+nodeid else acc
        )
        println("RemovedNodeIds:")
        println(removedNodesIds)

        val addedNodes=PGNodeIds.foldLeft(List[Int]())(
        (acc,nodeid)=>if!NGNodeIds.exists(_==nodeid)then acc:+nodeid else acc
        )
        println("AddedNodeIds:")
        println(addedNodes)

        val modifiedNodes=NGNodes.foldLeft(List[Int]())(
        (acc,node)=>if PGNodeIds.exists(_==node.id)&&!PGNodes.exists(_==node)then acc:+node.id else acc
        )
        println("modifiedNodeIds:")
        println(modifiedNodes)

        val removedEdges=NGEdges.foldLeft(List[(Int,Int)]())((acc,NGep)=>
        val foundEp=PGEdges.find(PGep=>PGep.source().id==NGep.source().id&&PGep.target().id==NGep.target().id)
        if foundEp.isDefined then acc
        else acc:+(NGep.source().id,NGep.target().id)
        )
        println("RemovedEdges:")
        println(removedEdges)

        val addedEdges=PGEdges.foldLeft(List[(Int,Int)]())((acc,PGep)=>
        val foundEp=NGEdges.find(NGep=>NGep.source().id==PGep.source().id&&NGep.target().id==PGep.target().id)
        if foundEp.isDefined
        then acc
        else
        acc:+(PGep.source().id,PGep.target().id)
        )
        println("addedEdges:")
        println(addedEdges)

        val modifiedEdges=NGEdges.foldLeft(List[(Int,Int)]())((acc,NGep)=>
        val foundEp=PGEdges.find(PGep=>NGep.source()==PGep.source()&&
        NGep.target()==PGep.target())
        foundEp match{
        case Some(pGep)=>
        if foundEp.isDefined&&NGsm.edgeValue(NGep.source(),NGep.target())==PGsm.edgeValue(pGep.source(),pGep.target())
        then acc
        else
        acc:+(NGep.nodeU().id,NGep.nodeV().id)
        case None=>acc
        }
        )
        println("modifiedEdges:")
        println(modifiedEdges)
        println()

        val k=10

        val simRankResult=SimRank(netgraph,perturbedGraph)
        val SRMatrix=simRankResult._3
        val SRMatrixCopy=SRMatrix.map(_.clone())
        println(SRMatrix.foreach(row=>{println();row.foreach(ele=>print(s"$ele "))}))

        println("\nMatchMap")
        matchMap.foreach(t=>{
        if(t._1._1==1&&t._2==1)then
        print(t)
        }
        )

        println("\n\n")

        decipherSRMatrix(netgraph,perturbedGraph,simRankResult._1,simRankResult._2,SRMatrixCopy)
        println("\n\n")
            /*val n21 = NGsm.nodes.asScala.toList.find(n => n.id == 21)
            n21 match
              case Some(n) => println(NGsm.adjacentNodes(n).asScala.toList.map(n => n.id))
              case None => {}*/

        println("making net subgraphs:")
        val NGSubgraphs=cutGraph(netgraph,10)
        println("making perturbed subgraphs:")
        val PGSubgraphs=cutGraph(perturbedGraph,10)

        println("net subgraphs:")
        NGSubgraphs.foreach(NGsg=>println(NGsg.sm.nodes().asScala.toList.map(n=>n.id)))
        println("perturbed subgraphs:")
        PGSubgraphs.foreach(PGsg=>println(PGsg.sm.nodes().asScala.toList.map(n=>n.id)))

            /*println("Storing shards:")

            var shardCount = 0
            for(netsg <- NGSubgraphs){
              for(perturbedsg <- PGSubgraphs){
                serializeShard(netsg, perturbedsg, "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/", s"shard$shardCount")
                shardCount += 1
              }
            }
            println("serialization complete!")

            val loadedShards = ListBuffer[(Option[NetGraph], Option[NetGraph])]()
            (0 until shardCount).foreach(num => loadedShards += deserializeShard(fileName = s"shard$num", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/"))

            loadedShards.foreach(loadedshard =>
              loadedshard match {
              case (Some(netsubgraph), Some(perturbedsubgraph)) =>
                /*netsubgraph.toDotVizFormat(name="shard0 netsubgraph", dir="C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/", fileName="shard0- netsubgraph.ngs", outputImageFormat = Format.DOT)
                perturbedsubgraph.toDotVizFormat(name="shard0- perturbedsubgraph", dir="C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/", fileName="shard0- netperturbedgraph.ngs", outputImageFormat = Format.DOT)*/
        print(netsubgraph.sm.nodes().asScala.toList.map(n=>n.id))
        print("   ")
        println(perturbedsubgraph.sm.nodes().asScala.toList.map(n=>n.id))
        println()
        case(None,Some(perturbedsubgraph))=>println("netsubgraph failed to save")
        case(Some(netsubgraph),None)=>println("perturbedsubgraph failed to save")
        case(None,None)=>println("Both subgraphs failed to save")
        }
        )*/


        serializeShardAsString(NGSubgraphs,PGSubgraphs,dir="C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/",fileName="shardAsString0")
        println("shardAsString0 saved successfully")
            /*val shardStr0 = deserializeStringShard("shardAsString0", dir="C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/StringShards/")
            println(s"shard0 NGNodes:\n${shardStr0.allNnodes}\n\n shard0 NgParentMap:\n${shardStr0.allN_ParentMap}\n\n, shard0 PGNodes:\n${shardStr0.allPnodes},\n\nshard0 PGParentMap:\n${shardStr0.allP_ParentMap}\n")
*/
            /*
            println(s"shard ng0, shard pg0 - node matches: ${shardStr0.allNnodes intersect shardStr0.allPnodes}")

            println("SimRankv_2 ng0, pg0:")
            println(SimRank_v2(shardStr0.allNnodes, shardStr0.allN_ParentMap, shardStr0.allPnodes, shardStr0.allP_ParentMap))
            println("\nSimRankv_1 ng0, pg0:")

            matchMap.clear()
            SimRank(NGSubgraphs(0), PGSubgraphs(0))._3.foreach(row => {println();row.foreach(ele => print(s"$ele "))})
            println("\nMatchMap")
            matchMap.foreach(t =>
              print(t)
            )
            /*val NGallNodeIds = NGSubgraphs.foldLeft(Set[Int]())( (acc, NGsg) => acc ++ (NGsg.sm.nodes().asScala.toList.map(n => n.id)).toSet)
            NGallNodeIds.foreach(id => print(s"${id}, "))

            var NGsubgraphNo = 0
            NGSubgraphs.foreach(sg => {
              sg.toDotVizFormat(name = s"shard-$NGsubgraphNo", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/", fileName = s"Net Graph - shard$NGsubgraphNo.ngs", outputImageFormat = Format.DOT)
              NGsubgraphNo += 1
            })
*/

            /*PGSubgraphs.foreach(PGsg => println(PGsg.sm.nodes().asScala.toList.map(n => n.id)))
            val PGmergeSet = PGSubgraphs.foldLeft(Set[Int]())((acc, PGsg) => acc ++ (PGsg.sm.nodes().asScala.toList.map(n => n.id)).toSet)
            PGmergeSet.foreach(id => print(s"${id}, "))

            var PGsubgraphNo = 0
            PGSubgraphs.foreach(sh => {
              sh.toDotVizFormat(name = s"shard-$PGsubgraphNo", dir = "C:/UIC EDUCATION/CS441/HW/MapReduce - HW1/GraphEquivalence/outputs/shards/", fileName = s"Perturbed Graph - shard$PGsubgraphNo.ngs", outputImageFormat = Format.DOT)
              PGsubgraphNo += 1
            })*/
        */


        }
        case None=>println("Perturbed Graph Load failed!")
        case None=>println("Net Graph Load failed!")

        }
*/
