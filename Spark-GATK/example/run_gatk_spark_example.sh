#
# Copyright (c) 2016 NCIC, Institute of Computing Technology, Chinese Academy of Sciences
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

spark_master=spark://MASTER_NODE:7077
gatk_spark_submit=/PATH/TO/GATK-Spark/bin/gatk-spark-submit

# reference and known vcfs
ref_path=/PATH/TO/YOUR/human_g1k_v37.fasta
indel_path=/PATH/TO/YOUR/1000G_phase1.indels.b37.vcf,/PATH/TO/YOUR/Mills_and_1000G_gold_standard.indels.b37.vcf
snp_path=/PATH/TO/YOUR/dbsnp_138.b37.vcf

# input and output
sam_path=/PATH/TO/test.bam
output=/PATH/TO/testResult

# arguments
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

