#### Name: Punit Malpani
#### email: pmalpa2@uic.edu

# Project Title: Graph Equivalence (using Hadoop Map-Reduce)
# Project Description: 
This project aim to develop a distributed program for efficiently processing large graphs generated by the NetGraphSim platform. The primary objective is to compute and analyze differences between original and perturbed graphs, with a focus on precision using YAML format. This project is built on top of NetGraphSimulator project developed by Prof. Mark Grechanik.

## Tasks:
### 1. Breaking graphs into parts
A pair of graph is generated from NetGameSim- 1)Original Graph (referenced as NGraph in code) 2)Perturbed Graph(referenced as PGraph in code). 
As first step of this project, the graph is broken into pieces. Say original graph is broken is broken into m pieces and perturbed graph is broken into n pieces, then a total of m*n pairs of these pieces are made. These pieces are saved into a text file, one pair in each line. 

### 2. Implementation of Graph comparison algorithm
This project uses the famous SimRank algorithm to deal give an approximation solution the graph comparison. The key idea behind SimRank is that nodes are similar if they are related to similar nodes.The algorithm quantifies similarity based on the co-occurrence of relationships between pairs of nodes. Link: [https://en.wikipedia.org/wiki/SimRank]. 

### 3. Implement Hadoop Map-Reduce for big data processing.
As the third step, two map-reduce jobs are created. The first map-reduce job compares each node of original graph in a piece read by its mapper to every node of perturbed piece using the graph comparison algorithm. It outputs file (in hdfs output directory) with a best matched perturbed node for every original node. If no good match is found, the original node is matched to a dummy NodeObject with id = -1, and all other properties of the node object = 0
e.g., NodeObject(-1,0,0,0,0,0,0,0,0). One additional computation is being done inside this mapper. It populates the DTL and ATL scores of traceability links. Since the mapper compares the graphs and output only 1 perturbed node in the output file, it is necessary to calculate the DTL inside the mapper. DTL includes every matched pair score for every node in the original graph. The DTL Map will ,at last, store correctly discarded traceability link of a original node with SimRank score greater than 0. DTL map is updated at a later stage where, all the  TLs that have a 0.0 SimRank score are discarded.   

Once the 1st map-reduce job is over, its output is processed along with data from the original graphs to get added/removed/modified/unperturbed nodes. This data is saved in a yaml file. All the edges associated with the removed nodes and added nodes, are also added to the yaml at this time.

After this, mapper2 input file is created, which contains all the modified/unperturbed edges of all the nodes present in the map-reduce job 1 output file. For every matched pair of nodes in this file, information of their incident edges and cost value is saved in the mapper2 input file; entry for edges related to one pair of related nodes in each line. 

At the end of this step, map-reduce2 job is run. It compares all the modified+unperturbed edges and outputs a text file with 3 divisions for predicted edge perturbation types- added, removed, modified. The edge pair is stored as "x:y", where x and y are ids that represent an edge pair of a node of the original graph.

### 4.Precision Evaluation:
Untill this stage, the YAML has been generated. In this final stage, golden set yaml and predicted yaml files are read from the input streams and are compared to generate matching graph component statistics ATL, DTL, CTL, and WTL are calculated separately for nodes and edges. ACC, BLTR, and VPR are calculated and the 3 overall ACC, BLTR, and VPR scores are generated by averaging the statistics nodes and edges statistics. These are printed on the command line, as well as saved as another yaml file containing scores.

# How to install and run the project
Requirements: [Java Development Toolkit (JDK)](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html) and [Simple Build Toolkit (SBT)](https://www.scala-sbt.org/1.x/docs/index.html)


(This project uses a jar file of [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) developed by Prof. Mark Grechanik. Assuming NetGraphSim is working on the system, this project takes 3 graphs files as input - 1) a .ngs binary file, 2) .ngs.perturbed binary file , and 3) .ngs.yaml file. All these files are generated from running the NetGameSim project.

## Installations Used
+ Install Simple Build Toolkit (SBT)[MSI Installer](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Windows.html)
+ Ensure you can create, compile and run Java and Scala programs.
+ Development Environment
+ Windows 11
+ Oracle OpenJDK 1.8 (also tested on Java 20.0.2)
+ Scala 3.2.2
+ sbt version - 1.8.3 (in project>build.properties)
+ Hadoop 3.3.4
+ Other dependencies exist in build.sbt
+ IntelliJ IDEA Ultimate


# How to use the project
a). Clone the project from gihub. The base directory should be "GraphEquivalence"<br>
b). [Check Step]: Check for /lib folder in the base directory for a netmodelsim jar file. If its missing, create a jar file of netmodelsim using "sbt clean compile assembly" from NetGameSim project. and add this jar file to the lib folder.<br> 

1. As first step, go to the application.conf file and set the configuration. This file contains all folder and file path settings required for this project.
All the configurations are set in the Utilities module >src>main>resources>application.conf file. 

- **NGSGraphDir** - path to the directory where all the graph files (.ngs, .ngs.perturbed, .ngs.yaml) are stored <br>
- **originalGraphFileName**- file name of the original graph (with .ngs extension) <br>
- **perturbedGraphFileName**- file name of the original graph (with .ngs.perturbed extension) <br>
- **mapReduce1Dir**-> path to map-reduce 1 directory. This will store input file for mapper 1. <br>
- **mapReduce1outputDirPath**- output folder path for mapreduce1 which will be created by hadoop. This output folder will be created inside MapReduce1Dir.<br>
- **mapReduce1outputFileName**- output file name of the map-reduce 1 job. default = "part-00000"<br>
- **mapReduce1InputFileName**- Input file name the mapper 1 will read from. e.g., mapper1Input.txt. This should be stored inside mapReduce1Dir path.<br>
- **mapReduce2Dir**-> path to map-reduce 2 directory. This will store input file for mapper 2. <br>
- **mapReduce2outputDirPath**- output folder path for mapreduce1 which will be created by hadoop. This output folder will be created inside MapReduce1Dir.<br>
- **mapReduce2outputFileName**- output file name of the map-reduce 2 job. default = "part-00000" <br>
- **mapReduce2InputFileName**- Input file name the mapper 1 will read from. e.g., mapper2Input.txt. This should be stored inside mapReduce1Dir path. <br>
-**hadoopFS** = This decides the hadoop file system. This should be set either = "hdfs://localhost:port" or "local". (This will decide invoke different functions inside the application, so set this carefully." <br>
- **predictedYamlFileDir**- directory where predicted yaml should be generated. e.g. "hdfs://localhost:9000/Yaml/" or "path/to/your/local/fs" <br>
- **predictedYamlFileName**-  filename of the  predicted yaml. e.g.,"predictedYaml.yaml" <br>
- **goldenYamlFileDir** = file name of the golden yaml file. This can be either - "hdfs://localhost:9000/somefolderpath" or "path/to/yout/local/fs". (can be kept same as NGSGraphDir. It is defined in case file needs to be saved on the hdfs system) <br>
- **scoresYamlFileName** = file name of the scores yaml file. This will be generated in the same folder as the predicted yaml file.

2. Create folders as set in the application.conf. For easiness, the following structure can be followed. In outputs directory, create 4 folders named: (i)Graph, (ii)MapReduce1, (iii)MapReduce2, (iv) Output. Configure the application.conf such that all the input graph files (from NetGameSim) are in the Graph folder, MapReduce1 is set for MR job1, MapReduce2 is set for MR job2, Output is set for final predicted yaml and scores yaml output files.

3. Save the 3 graph files with .ngs, .ngs.perturbed, and.ngs.yaml in the *NGSGraphDir* folder.

4. From the terminal, run "sbt compile". Then, run "sbt run". As all paths are configured in the application.conf, there's no requirement for command line arguments to be set.
> Output files can be seen in *predictedYamlFileDir* folder after termination. 

## Working Explanation
(Italics are used to reference application.conf parameters)
### 1. Main.scala
This is the Main scala file which contains the main() function. It basically calls helper functions to first cut the graph into pieces, pairs them up and saves these pairs in the *mapReduce1Dir* with the name *mapReduce1InputFileName*. After this, it configures a map reduce job to run SimRank on each pair of induced subgraphs nodes. The output file of map-reduce1 contains all the node matchings- indicating which original graph node is matched to which perturbed graph node. Then, a yaml file is created named *predictedYamlFileName* that contains categorizations nodes as modified / added / removed. A second map-reduce job is run to categorize unperturbed and modified edges into the 3 categories in the yaml file. Finally, golden set yaml and predicted yaml files are read, a prediction analysis is performed and results are saved in *scoresYamlFileName* yaml file.

### 2. HelperFunctions.scala
This file contains helper functions of all sort including cutting the graphs, serialization/deserialization, simRank algorithm, etc. which are integral to this application. 

### 2. MapReduceProgram.scala
This file contains the 2 Map-Reduce Jobs. It also calculates and generates a predicted yaml file. 
Assuming that hadoop is configured in the system, Start your local hadoop cluster using. e.g., for windows, use start-all.cmd 
<b>Second, run this file to generate node and edge matching file (as output of 2 map reduce jobs) and a predicted yaml file.</b> 

### 3. ModeAccuracyCheck.scala
This file enables reading the 2 yaml- golden and generated, and calculates statisitics based on these. It compares the traceability links and creates parameters similar to a confusion matrix.

  //DEFINITIONS and their comprehensions<br>
    /*RTL = BTL+ GTL<br>
      /*BTL = CTL + WTL<br>
        //CTL(false neg) = number of correct TLs that are mistakenly discarded by your algorithm<br>
        //WTL(false pos) = number of wrong TLs that the your algorithm accepts<br>
      GTL= ATL + DTL<br>
          //ATL(true pos) = number of correct TLs that are correctly accepted by your algorithm<br>
          //DTL(true neg) = number of wrong TLs that are correctly discarded by your algorithm<br>
    Quality measures<br>
      //ACC = ATL / RTL<br>
      //BLTR = WTL / RTL<br>
      //VPR =  (GTL - BTL)/(2×RTL) + 0.5<br>
    */<br>
    */<br>


## FOR running in hadoop locally using general file system

Steps to run:
1. set *hadoopFS* = "local" in application.conf. Set other paths (see description at the top) - remember - all paths should conform to the local FS. 
2. start hadoop and yarn from your system's terminal.
3. from the terminal go to the Main.scala path and run *"sbt compile", "sbt run"*. This should create a mapper input file in the mapper input directory as mentioned in the application.conf file. This will run the map-reduce jobs and create 2 yaml files with graph componeent categorization and scores.

## FOR running on Amazon EMR
Steps to run:
1. set hadoopFS = "s3://<your-bucket-name>, in application.conf. Set other paths (see description at the top). remember - all paths should conform to the amazon s3 file system and start with "s3".
2. From the terminal, go to the Main.scala path and run *"sbt clean compile assembly"*. This should create fat jar file named GraphEquivalency.jar in *basedirectory*/target/scala-3.2.2/ 
3. create 4 directories in the aws bucket- one for Graphs, second for MapReduce1, third for MapReduce2, and last for output yamls. 
4. Upload the graph files to the *Graph* in s3 bucket. Upload the jar file to the s3 root directory. (No specific need to create these exact folders, this is just one way to configure)
5. Now create a cluster in the amazon emr. Use custom jar in step and refer the custom jar kept in the bucket path. Now go Set command line argument = Main. This is the scala file whose main function we intend to run.

# ERRORS 
(lossely handled)
Following Error will be thrown for corresponding situations:
1. **NodeStr: $strObj doesn't have 9 fields!"**- If 9 fields couldn't be parsed out of a string object corresponding to a NodeObject that should have 9 fields.
    
2. **"best score element not found in Perturbed SubGraph Nodes!"** - sanity check to ensure atleast 1 best nodematch should be found for an original node.
  
3. **"Couldn't load original graph File at _** - when original netgraph couldn't be loaded

4. **"Couldn't load perturbed graph File at _** - when original perturbed couldn't be loaded 
    
5. **hadoopFS should be set to either on local path, hdfs localhost path or s3 bucket path** - if hadoopFS parameter in application.conf is not set among these three
    
6.  **YAML File $yamlFileName does not exist.** - when yaml file doesn't exists as mentioned in predictedYamlFileDir/predictedYamlFileName location as set in the application.conf

## Scope for improvement
1. SimRank algorithm can be definitely optimized.
2. Parallelization can be incorporated to increase efficient.

### YouTube link:
(old ver.)
[https://youtu.be/4fgGW0kkbjU]