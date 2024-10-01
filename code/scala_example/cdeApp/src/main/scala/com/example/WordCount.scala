import org.apache.spark.sql.SparkSession

object WordCount {
  def main(args: Array[String]): Unit = {
    // Check input arguments
    if (args.length < 2) {
      System.err.println("Usage: WordCount <input_file> <output_directory>")
      System.exit(1)
    }

    // Initialize Spark session
    val spark = SparkSession.builder
      .appName("Word Count")
      .getOrCreate()

    // Read input file
    val inputFile = args(0)
    val outputDir = args(1)

    // Perform word count
    val textFile = spark.sparkContext.textFile(inputFile)
    val wordCounts = textFile
      .flatMap(line => line.split("\\s+"))
      .map(word => (word, 1))
      .reduceByKey(_ + _)

    // Save results
    wordCounts.saveAsTextFile(outputDir)

    // Stop Spark session
    spark.stop()
  }
}
