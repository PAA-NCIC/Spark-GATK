Spark-GATK
====

# Introduction

Spark-GATK is a genomics analysis framwork based on [Apache Spark](http://spark.incubator.apache.org/) and [ADAM](http://bdgenomics.org/).

In the latest version, WGS pipline is implemented based on this framwork.

# Get Started

## Build Spark-GATK
Spark-GATK is built using Apache Maven. To build Spark-GATK and its example programs, run:
```
$ git clone https://github.com/PAA-NCIC/Spark-GATK.git
$ cd Spark-GATK
$ mvn package
$ ...
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 12.479 s
[INFO] Finished at: 2016-04-28T09:26:44+08:00
[INFO] Final Memory: 42M/912M
[INFO] ------------------------------------------------------------------------
```
If you want to run Spark-GATK on a Spark cluster, you should make sure that each machine on your cluster have a copy of this directory.

## Installing Spark
A Spark release must be on your system before running Spark-GATK.
Our work default to Spark 1.2.0 built for Hadoop 2.4.0, but any more recent Spark distribution should also work.
The latest release of Spark can be download from [Spark website](http://spark.apache.org/downloads.html). More information about installing Spark refers to [Spark Installing Document](https://github.com/apache/spark).

## Run a example of WGS
A pipline from bam file to vcf file is implemented in Spark-GATK.

Before running a WGS pipline, a human b37 reference and known indel/snp vcf files must be provided in HDFS. The input bam file must be on HDFS too.
Then you can write example script like following:
```
spark_master=spark://master_node_name:7077
gatk_spark_submit=/BASEDIR/NAME/OF/GATK-Spark/bin/gatk-spark-submit

ref_path=/PATH/TO/REFERENCE/human_g1k_v37.fasta                    # must be in HDFS
indel_path=/PATH/TO/KNOWN/_NDEL_FILES/1000G_phase1.indels.b37.vcf  # must be in HDFS
snp_path=/PATH/TO/KNOWN_SNP_FILES/dbsnp_138.b37.vcf                # must be in HDFS

sam_path=/PATH/TO/INPUT_FILE/test.bam                               # input file, must be in HDFS
output=/PATH/TP/OUTPUT_DIR/testResult        # output directory, must be in HDFS

fragment_length=10000
driver_memory=30G
executor_memory=20G
total_executor_cores=20

$gatk_spark_submit haplotype $sam_path $ref_path $output \
   -known_indels $indel_path \
   -known_snps $snp_path \
   -fragment_length ${fragment_length} \
   --master ${spark_master} \
   --driver-memory ${driver_memory} \
   --executor-memory ${executor_memory} \
   --total-executor-cores ${total_executor_cores}
```

You will see output as following:
```
Thu Apr 28 12:12:55 CST 2016 Mark duplicates start
Thu Apr 28 12:12:55 CST 2016 Mark duplicates finished
Thu Apr 28 12:12:55 CST 2016 Locally realignment start
Thu Apr 28 12:15:46 CST 2016 Locally realignment finished
Thu Apr 28 12:15:46 CST 2016 Base qualities recalibration start
Thu Apr 28 12:28:31 CST 2016 Base qualities recalibration finished
Thu Apr 28 12:28:31 CST 2016 Haplotype caller start
Thu Apr 28 12:28:57 CST 2016 Haplotype caller Finished, writing result into disk
Thu Apr 28 12:30:57 CST 2016 All tasks finished
```

# Arguments
Spark-GATK support all arguments defined by [ADAM](http://bdgenomics.org/), and also defines a series of arguments for WGS pipline.
````
Arguments for process defination.
 INPUT                                                           : The ADAM, BAM or SAM file to apply the transforms to
 FASTA                                                           : The reference FASTA file to convert
 OUTPUT                                                          : Location to write the transformed data in ADAM/Parquet format
 -mark_duplicate_reads                                           : If remove duplicate reads
 -locallyRealign                                                 : If locally realign indels present in reads.
 -recalibrate_base_qualities                                     : If recalibrate the base quality scores (ILLUMINA only)
 -dump_observations                                              : Local path to dump BQSR observations to. Outputs CSV format.
 -known_snps                                                     : Sites-only VCF giving location of known SNPs
 -knownIndelsFile                                                : VCF file including locations of known INDELs. If not provided, default consensus model will be used
 -max_indel_size                                                 : Maximum length of an INDEL region. (default 500)
 -max_consensus_number                                           : Maximum number of consensus for target region realignment. (default 30)
 -log_odds_threshold                                             : Log-odds threshold for accepting a realignment. (default 5.0)
 -max_target_size                                                : Maximum length of a target region to attempt realigning. (default 3000)
 -fragmentLength                                                 : Maximum fragment length. Default value is 10,000. Values greater than 1e9 should be avoided.
 -partition_size                                                 : Partition size. Values greater than 1e9 should be avoided.(default 1200)
````

# License
Spark-GATK is released under a [GNU General Public License](https://github.com/PAA-NCIC/Spark-GATK/blob/master/LICENSE).
