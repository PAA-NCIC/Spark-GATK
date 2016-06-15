/*
 * Copyright (c) 2016 NCIC, Institute of Computing Technology, Chinese Academy of Sciences
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
package org.ncic.gatkspark

import java.io.{PrintWriter, File}
import java.text.SimpleDateFormat
import java.util.{Date, Locale}
import javax.tools.Tool

import htsjdk.variant.variantcontext.VariantContext
import ncic.haplotypecaller.filter.FilterUtils
import org.apache.spark.SparkContext._
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.{ SparkContext, Logging }
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.algorithms.consensus._
import org.bdgenomics.adam.converters.AlignmentRecordConverter
import org.bdgenomics.adam.instrumentation.Timers._
import org.bdgenomics.adam.models.{SAMFileHeaderWritable, ReferenceRegion, SnpTable}
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.GATKSparkMarkDuplicates
import org.bdgenomics.adam.rdd.ADAMSaveAnyArgs
import org.bdgenomics.adam.rich.{RichNucleotideContigFragment, RichVariant}
import org.bdgenomics.formats.avro.{NucleotideContigFragment, AlignmentRecord}
import org.bdgenomics.utils.cli._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import ncic.haplotypecaller.VariantCaller


/**
  * Define DNASeq WGS process.
  * Created by lhy on 8/11/2015.
  * Modified by wbc on 25/4/2016
  */
object HaplotypeCaller extends BDGCommandCompanion {
  val commandName = "haplotype"
  val commandDescription = "Call Haplotype and find the variants."

  def apply(cmdLine: Array[String]) = {
    new HaplotypeCaller(Args4j[HaplotypeCallerArgs](cmdLine))
  }

  def findVariants(fragment: NucleotideContigFragment, records: Iterable[AlignmentRecord], filtered: Option[Iterable[AlignmentRecord]]
                   , variants: Iterable[VariantContext], header: SAMFileHeaderWritable, fragmentLen: Long): Iterable[VariantContext] = {
    val reads = filtered match {
      case Some(f) => (records++f).map(r => (new AlignmentRecordConverter).convert(r, header))
      case None => records.map(r => (new AlignmentRecordConverter).convert(r, header))
    }
    VariantCaller.findVariant(fragment, reads.toList, variants.toList, header.header, fragmentLen.toInt)
  }
}

class HaplotypeCallerArgs extends Args4jBase with ParquetArgs {
  @Argument(required = true, metaVar = "INPUT", usage = "The ADAM, BAM or SAM file to apply the haplotype caller", index = 0)
  var samInputPath: String = null
  @Argument(required = true, metaVar = "FASTA", usage = "The FASTA file to convert", index = 1)
  var fastaFile: String = null
  @Argument(required = true, metaVar = "OUTPUT", usage = "Location to write the transformed data in ADAM/Parquet format", index = 2)
  var outputPath: String = null
  @Args4jOption(required = false, name = "-mark_duplicate_reads", usage = "Mark duplicate reads")
  var markDuplicates: Boolean = true
  @Args4jOption(required = false, name = "-recalibrate_base_qualities", usage = "Recalibrate the base quality scores (ILLUMINA only)")
  var recalibrateBaseQualities: Boolean = true
  @Args4jOption(required = false, name = "-dump_observations", usage = "Local path to dump BQSR observations to. Outputs CSV format.")
  var observationsPath: String = null
  @Args4jOption(required = false, name = "-realign_indels", usage = "Locally realign indels present in reads.")
  var locallyRealign: Boolean = true
  @Args4jOption(required = false, name = "-known_snps", usage = "Sites-only VCF giving location of known SNPs")
  var knownSnpsFile: String = null
  @Args4jOption(required = false, name = "-known_indels", usage = "VCF file including locations of known INDELs. If none is provided, default consensus model will be used.")
  var knownIndelsFile: String = null
  @Args4jOption(required = false, name = "-max_indel_size", usage = "The maximum length of an INDEL to realign to. Default value is 500.")
  var maxIndelSize = 500
  @Args4jOption(required = false, name = "-max_consensus_number", usage = "The maximum number of consensus to try realigning a target region to. Default value is 30.")
  var maxConsensusNumber = 30
  @Args4jOption(required = false, name = "-log_odds_threshold", usage = "The log-odds threshold for accepting a realignment. Default value is 5.0.")
  var lodThreshold = 5.0
  @Args4jOption(required = false, name = "-max_target_size", usage = "The maximum length of a target region to attempt realigning. Default length is 3000.")
  var maxTargetSize = 3000
  @Args4jOption(required = false, name = "-dbsnp", usage = "dbsnp file")
  var dbsnp: String = null
  @Args4jOption(required = false, name = "-fragment_length", usage = "Sets maximum fragment length. Default value is 10,000. Values greater than 1e9 should be avoided.")
  var fragmentLength: Long = 100000L
  @Args4jOption(required = false, name = "-partition_size", usage = "Sets partition size. Default value is 1,200. Values greater than 1e9 should be avoided.")
  var partition_size: Int = 1200
}

class HaplotypeCaller(protected val args: HaplotypeCallerArgs) extends BDGSparkCommand[HaplotypeCallerArgs] with Logging {
  val companion = HaplotypeCaller

  def apply(fragments: RDD[NucleotideContigFragment], records: RDD[AlignmentRecord], args: HaplotypeCallerArgs): RDD[ReferenceRegion] = {
    fragments.toActiveRegion(records, args.fragmentLength)
  }

  def timeFormat():String={
    val date = new Date();
    date.toString
  }

  /**
    * Run WGS process from mark duplicate to haplotypeCaller.
    * The tools we provide include MarkDuplicates, IndelRealigner, BaseRecalibrator and HaplotypeCaller,
    * in which the IndelRealigner and BaseRecalibrator is provided by ADAM.
    * We implement HaplotypeCaller based on data format of ADAM, and rewrite the MarkDuplicate algorithm on spark,
    * which has same result with Picard MarkDuplicates.
    * More documents about DNASeq WGS process refer to website of broad institute: <a>www.broadinstitute.org/gatk/index.php</a>
    * @param sc
    */
  def run(sc: SparkContext) {
    // load sam/bam file
    val records = sc.loadAlignments(args.samInputPath)
    var adamRecords = records

    // MarkDuplicates
    if (args.markDuplicates) {
      println(timeFormat + "  Mark duplicates start")
      adamRecords = adamRecords.adamMarkDuplicates().repartition(args.partition_size)
      println(timeFormat + "  Mark duplicates finished")
    }

    // IndelRealigner
    // Call the method provided by adam core
    if (args.locallyRealign) {
      println(timeFormat + "  Locally realignment start")
      val consensusGenerator = Option(args.knownIndelsFile)
        .fold(new ConsensusGeneratorFromReads().asInstanceOf[ConsensusGenerator])(
          new ConsensusGeneratorFromKnowns(_, sc).asInstanceOf[ConsensusGenerator])

      adamRecords = adamRecords.adamRealignIndels(consensusGenerator,
        false,
        args.maxIndelSize,
        args.maxConsensusNumber,
        args.lodThreshold,
        args.maxTargetSize)

      println(timeFormat + "  Locally realignment finished")
    }

    // BaseRecalibrator
    // Call the method provided by adam core
    if (args.recalibrateBaseQualities) {
      println(timeFormat + "  Base qualities recalibration start")
      val knownSnps: SnpTable = createKnownSnpsTable(sc)
      adamRecords = adamRecords.adamBQSR(sc.broadcast(knownSnps), Option(args.observationsPath))
      println(timeFormat + "  Base qualities recalibration finished")
    }


    println(timeFormat + "  Haplotype caller start")
    // HaplotypeCaller
    val filterUtils = new FilterUtils
    val myRecords = adamRecords.filter(r => filterUtils.ifPassFilter(r) && r.getEnd != null)

    myRecords.cache()

    val sd = myRecords.adamGetSequenceDictionary()
    val rgd = myRecords.adamGetReadGroupDictionary()
    val header = (new AlignmentRecordConverter).createSAMHeader(sd, rgd)
    val hdrBcast = sc.broadcast(SAMFileHeaderWritable(header))


    val fragmentLength = args.fragmentLength
    val frgBcast = sc.broadcast(fragmentLength)
    val fragments = sc.loadFasta(args.fastaFile, frgBcast.value)

    val filtered = myRecords.filter(r => r.getStart > 300 && ((r.getStart - 1) % frgBcast.value < 300 || (r.getEnd - 1) % frgBcast.value < 300))
    // generate variant information
    val result = fragments.keyBy(f => (f.getContig.getContigName, f.getFragmentNumber.toLong))
      .join(myRecords.groupBy(r => (r.getContig.getContigName, (r.getEnd - 1) / frgBcast.value)))
      .leftOuterJoin(filtered.groupBy(f => (f.getContig.getContigName, (f.getStart - 301) / frgBcast.value)))
      .map(p => HaplotypeCaller.findVariants(p._2._1._1, p._2._1._2, p._2._2, List.empty, hdrBcast.value, frgBcast.value)).flatMap(p => p.seq)

    println(timeFormat + "  Haplotype caller Finished, writing result into disk")
    result.saveAsTextFile(args.outputPath);
    println(timeFormat + "  All tasks finished")
  }

  private def createKnownSnpsTable(sc: SparkContext): SnpTable = CreateKnownSnpsTable.time {
    Option(args.knownSnpsFile).fold(SnpTable())(f => SnpTable(sc.loadVariants(f).map(new RichVariant(_))))
  }

}
