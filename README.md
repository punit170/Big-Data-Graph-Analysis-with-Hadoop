# Graph Equivalence: Big Data Graph Comparison Analysis with Hadoop

## Project Description

<div style="text-align: justify;">

This project aims to develop a distributed program for efficiently processing large graphs generated by the [NetGraphSim](https://github.com/0x1DOCD00D/NetGameSim) platform. The primary objective is to compute and analyze differences between original and perturbed graphs, with a focus on precision using the YAML format. This project builds on the [NetGraphSim](https://github.com/0x1DOCD00D/NetGameSim) (a platform to generate large-scale random graphs) developed by Dr. Mark Grechanik.

</div>

## Requirements

- [Java Development Toolkit (JDK)](https://www.oracle.com/java/technologies/javase/javase8-archive-downloads.html)
- [Simple Build Toolkit (SBT)](https://www.scala-sbt.org/1.x/docs/index.html)
- [a NetGameSim jar](https://github.com/punit170/Big-Data-Graph-Analysis-with-Hadoop/blob/main/lib)
- Install Simple Build Toolkit (SBT) using the [MSI Installer](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Windows.html)
- Ensure you can create, compile, and run Java and Scala programs.
- A development environment, e.g., IntelliJ IDEA
- Oracle OpenJDK 1.8 (also tested on Java 20.0.2)
- Scala 3.2.2
- SBT version 1.8.3 (specified in `project > build.properties`)
- Hadoop 3.3.4
- Other dependencies are listed in `build.sbt`

> [!NOTE]
> - This project uses a JAR file generated of the [NetGameSim](https://github.com/0x1DOCD00D/NetGameSim) project. Assuming NetGraphSim is working on the system, this project takes 3 graph files as input that are generated on running NetGameSim:
> -  1. A `.ngs` binary file
> -  2. A `.perturbed` binary file
> -  3. A `.ngs.yaml` YAML file
<br>

## How to use the project

1. Clone the project. The base directory should be "GraphEquivalence".

> [!IMPORTANT]
> **[Check Step]**: Check for the `lib` dir in the base directory for a `netmodelsim.jar` file. If it is missing, create a jar file of netmodelsim using `sbt clean compile assembly` from the NetGameSim project. Add this jar file to the `lib` dir.

2. Go to the `application.conf` file and set the configuration. This file contains all dir(s) and file path settings required for this project.

All the configurations are set in the `Utilities module > src > main > resources > application.conf` file.
Refer to the following block for `application.conf` parameters:
```md
   - `NGSGraphDir` - Path to the directory where all the graph files (`.ngs`, `.ngs.perturbed`, `.ngs.yaml`) are stored.
   - `originalGraphFileName` - File name of the original graph (with `.ngs` extension).
   - `perturbedGraphFileName` - File name of the perturbed graph (with `.ngs.perturbed` extension).
   - `mapReduce1Dir` - Path to map-reduce 1 directory. This will store the input file for mapper 1.
   - `mapReduce1outputDirPath` - Output dir path for map-reduce 1 which will be created by Hadoop. This output dir will be created inside `mapReduce1Dir`.
   - `mapReduce1outputFileName` - Output file name of the map-reduce 1 job. Default is `part-00000`.
   - `mapReduce1InputFileName` - Input file name the mapper 1 will read from, e.g., `mapper1Input.txt`. This should be stored inside the `mapReduce1Dir` path.
   - `mapReduce2Dir` - Path to map-reduce 2 directory. This will store the input file for mapper 2.
   - `mapReduce2outputDirPath` - Output dir path for map-reduce 2 which will be created by Hadoop. This output dir will be created inside `mapReduce2Dir`.
   - `mapReduce2outputFileName` - Output file name of the map-reduce 2 job. Default is `part-00000`.
   - `mapReduce2InputFileName` - Input file name the mapper 2 will read from, e.g., `mapper2Input.txt`. This should be stored inside the `mapReduce2Dir` path.
   - `hadoopFS` - This decides the Hadoop file system. This should be set to either `hdfs://localhost:port` or `local`. (This will decide which functions are invoked inside the application, so set this carefully.)
   - `predictedYamlFileDir` - Directory where the predicted YAML should be generated, e.g., `hdfs://localhost:9000/Yaml/` or `path/to/your/local/fs`.
   - `predictedYamlFileName` - Filename of the predicted YAML, e.g., `predictedYaml.yaml`.
   - `goldenYamlFileDir` - File name of the golden YAML file. This can be either `hdfs://localhost:9000/someDirectorypath` or `path/to/your/local/fs`. (This can be the same as `NGSGraphDir`. It is defined in case the file needs to be saved on the HDFS system.)
   - `scoresYamlFileName` - File name of the scores YAML file. This will be generated in the same dir as the predicted YAML file.
```
3. Create dir(s) as set in the `application.conf`. For ease, the following structure can be followed. In the `outputs` directory, create 4 dir(s) named: (i) `Graph`, (ii) `MapReduce1`, (iii) `MapReduce2`, (iv) `Output`. Configure the `application.conf` such that all the three input graph files (from NetGameSim) are in the `outputs/Graph/`, `outputs/MapReduce1/` is set for Map-Reduce job1, `outputs/MapReduce2` is set for Map-Reduce job2, and `outputs/Output` is set for resultant predicted YAML and scores YAML output files.

4. Save the 3 graph files with `.ngs`, `.ngs.perturbed`, and `.ngs.yaml` extensions in the path dir you've set for `'NGSGraphDir'` in the config file.

4. Save the 3 graph files with `.ngs`, `.ngs.perturbed`, and `.ngs.yaml` extensions in the path dir you've set for `'NGSGraphDir'` in the config file.

5. **Running Locally:**
   - **5.1.** Set `hadoopFS` config variable to **'local'** in `application.conf`. Set other paths (see the description at the top) - remember - all paths should conform to the local FS.
     
   - **5.2.** Start Hadoop and YARN from your system's terminal.
     
   - **5.3.** From the terminal, go to the `Main.scala` path and run `"sbt compile"`, `"sbt run"`. This should create a mapper input file in the mapper input directory as mentioned in the `application.conf` file. This will run the map-reduce jobs and create 2 YAML files with graph component categorization and scores.

6. **Running on Amazon EMR:**
   - **6.1.** Set `hadoopFS` config variable to **"s3://<your-s3-bucket-name>"** in `application.conf`. (Remember - all paths should conform to the Amazon S3 file system and start with `s3`.)
     
   - **6.2.** From the terminal, go to the `Main.scala` path and run `sbt clean compile assembly`. This should create a fat jar file named `GraphEquivalency.jar` in `basedirectory/target/scala-3.2.2/`.
     
   - **6.3.** Create 4 directories in the AWS bucket - one for Graphs, the second for MapReduce1, the third for MapReduce2, and the last for output YAMLs.
     
   - **6.4.** Upload the 3 graph files to the `Graph` directory in the S3 bucket. Upload the jar file to your S3 bucket's root directory. (No specific need to create these exact directories; this is just one way to configure it.)
     
   - **6.5.** Create a cluster in Amazon EMR. Use the custom jar in the step and refer to the custom jar kept in the bucket path. Set the command line argument before running the configured EMR to `Main`. (This is the Scala file whose main function we intend to run.) This will run the map-reduce jobs and create 2 YAML files with graph component categorization and scores in your S3 bucket in a directory indicated by the `predictedYamlFileDir` as set in the config file.<br>
   
## Understanding important code files
<table><tr><td>1.<ins>Main.scala</ins></td></tr></table>

Main.scala is the primary Scala file housing the `main()` function. Its responsibilities include:

- **Graph Partitioning**: Splits the graph into segments and pairs them up, saving these pairs in `mapReduce1Dir` with the name `mapReduce1InputFileName`.
- **Map-Reduce Job 1**: Configures a job to execute SimRank on each pair of induced subgraph nodes. The output file of Map-Reduce 1 contains node matchings, indicating how each original graph node maps to a perturbed graph node.
- **Predicted YAML Generation**: Creates a YAML file named `predictedYamlFileName` that categorizes nodes as modified, added, or removed.
- **Map-Reduce Job 2**: Runs a job to categorize unperturbed and modified edges into the three categories defined in the YAML file.
- **Prediction Analysis**: Reads both the golden set YAML and predicted YAML files, performs analysis, and saves results in `scoresYamlFileName`.

<table><tr><td>2. <ins>HelperFunctions.scala</ins></td></tr></table>

HelperFunctions.scala provides essential utility functions including:

- **Graph Operations**: Functions for cutting graphs into segments, handling serialization/deserialization operations, and implementing the SimRank algorithm.
- **Other Utilities**: Additional support functions for file management, data manipulation, and algorithmic tasks crucial to the application.

<table><tr><td>3. <ins>MapReduceProgram.scala</ins></td></tr></table>

MapReduceProgram.scala manages the two Map-Reduce jobs:

- **Job Execution**: Defines and executes Map-Reduce jobs to process and analyze graph data.
- **Node and Edge Matching**: Generates node and edge matching files as outputs from the Map-Reduce jobs.
- **Predicted YAML Generation**: Computes and outputs the predicted YAML file based on the results of the Map-Reduce jobs.

> [!NOTE]
> - Assuming Hadoop is configured on the system:
>     - 1. Start your local Hadoop cluster, e.g., using `start-all.cmd` (for Windows).
>     - 2. Execute MapReduceProgram.scala to generate node and edge matching files and the predicted YAML file.<br>

<table><tr><td>4. <ins>ModeAccuracyCheck.scala</ins></td></tr></table>

ModeAccuracyCheck.scala facilitates comparison and statistical analysis of two YAML files:

- **Comparison**: Reads the golden and generated YAML files and computes statistics similar to a confusion matrix based on traceability links.
- **Parameter Calculation**: Calculates and derives performance metrics to assess the accuracy and reliability of predictions.<br>

## Project Build-up Steps

#### 1. <ins>Sharding</ins>: Breaking graphs into parts

A pair of graphs is generated from NetGameSim:

- **Original Graph** (referenced as NGraph in code)
- **Perturbed Graph** (referenced as PGraph in code)

As the first step of this project, the graph is broken into pieces. If the original graph is divided into m pieces and the perturbed graph into n pieces, a total of m*n pairs of these pieces are created. Each pair is saved into a text file, with one pair per line.

---

#### 2. <ins>SimRank</ins>: Implementation of Graph comparison algorithm

This project utilizes the SimRank algorithm for graph comparison. SimRank quantifies similarity based on the co-occurrence of relationships between pairs of nodes. For more information on SimRank, refer to [SimRank Wikipedia](https://en.wikipedia.org/wiki/SimRank).

---

#### 3. <ins>Map-Reduce</ins>: Implementation of Hadoop Map-Reduce for big data processing

In this step, two Map-Reduce jobs are implemented:

(i) <ins>**Map-Reduce Job 1**</ins>:
- Compares each node of the original graph piece with every node of the corresponding perturbed graph piece using the SimRank algorithm.
- Outputs a file in the HDFS output directory with the best-matched perturbed node for every original node. If no good match is found, it assigns a dummy NodeObject with id = -1 and all other properties set to 0.
- Populates the DTL (Discarded Traceability Links) and ATL (Accepted Traceability Links) scores of traceability links during the comparison process.

(ii) <ins>**Map-Reduce Job 2**</ins>:
- Processes the output from Job 1 along with data from the original graphs to categorize nodes as added, removed, or modified in a YAML file.
- Compares modified and unperturbed edges and categorizes them into added, removed, or modified types in the output file.

---

#### 4. <ins>Precision Evaluation</ins>

Upon completion of the Map-Reduce jobs, the following steps are executed:

- **YAML Generation**: Golden set YAML and predicted YAML files are generated.
  
- **Statistical Analysis**: The project reads these YAML files and performs statistical analysis to evaluate precision.
  - Calculates ATL (Accepted Traceability Links), DTL (Discarded Traceability Links), CTL (Correctly Traced Links), and WTL (Wrongly Traced Links) separately for nodes and edges.
  - Computes overall accuracy (ACC), balanced traceability ratio (BLTR), and precision ratio (VPR) using these statistics.
  - Outputs these scores into a result YAML file.

> [!IMPORTANT]
> #### Understanding Resultant Graph Comparison Statistics
> - **Traceability Links**: Matches between graph components in the original and perturbed graphs.
> - **RTL (Recoverable Traceability Links)**: Sum of Good Traceability Links (GTL) and Bad Traceability Links (BTL).
> - **GTL (Good Traceability Links)**: Sum of DTL (True Negative) and ATL (True Positive).
> - **BTL (Bad Traceability Links)**: Sum of CTL (False Negative) and WTL (False Positive).
> - **ACC (Accuracy Ratio)**: Ratio of correctly recovered TLs, calculated as ACC = ATL / RTL.
> - **BLTR (Balanced Traceability Ratio)**: Ratio of wrongly traced TLs, computed as BLTR = WTL / RTL.
> - **VPR (Precision Ratio)**: Indicates algorithmic precision in analyzing recovered TLs, calculated as VPR = (GTL - BTL) / (2 * RTL) + 0.5.
<br>

## ERRORS and their causes

The following errors may occur during execution:

1. **"NodeStr: $strObj doesn't have 9 fields!"**
   - Occurs if parsing fails to extract 9 fields from a string object representing a NodeObject that should have exactly 9 fields.

2. **"Best score element not found in Perturbed SubGraph Nodes!"**
   - A sanity check error indicating that no best node match was found for an original node in the perturbed subgraph nodes.

3. **"Couldn't load original graph File at _"**
   - Indicates failure to load the original netgraph file from the specified path.

4. **"Couldn't load perturbed graph File at _"**
   - Indicates failure to load the perturbed netgraph file from the specified path.

5. **"hadoopFS should be set to either on local path, HDFS localhost path, or s3 bucket path"**
   - Raised when the `hadoopFS` parameter in `application.conf` is not configured to one of the specified paths: local path, HDFS localhost path, or an S3 bucket path.

6. **"YAML File $yamlFileName does not exist."**
   - Raised when the specified YAML file (`$yamlFileName`) does not exist in the location defined by `predictedYamlFileDir/predictedYamlFileName` as configured in `application.conf`.


## Scope for improvement

1. The SimRank algorithm can be optimized further to enhance performance and accuracy.
   
2. Implementing parallelization techniques could significantly boost efficiency, particularly in the context of large-scale graph processing.


## YouTube link: <ins>[Explanation](https://youtu.be/4fgGW0kkbjU)</ins>


## Need Help?

Reach out to me!
**Email:** [punit.malpani@gmail.com](mailto:punit.malpani@gmail.com)
