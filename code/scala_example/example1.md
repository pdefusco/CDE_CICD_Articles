# Basic CI / CD Workflow in Cloudera Data Engineering

1. Test jobs in CDE Session from local.  
2. Once ready for operationalization push to git.
3. Sync with CDE repository
4. Deploy using CLI
5. Monitor
6. Promote to higher env using API by replicating repo and redeploy

We will prototype and test the WordCount app:

```
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
```

## 1. Test jobs in CDE Session from local

```
cde resource create --type files --name dev_files --vcluster-endpoint https://l9hb4klq.cde-f25hdn5h.mega-hol.oldk-i9ly.a4.cloudera.site/dex/api/v1

cde resource upload --name dev_files --local-path sonnet.txt --vcluster-endpoint https://l9hb4klq.cde-f25hdn5h.mega-hol.oldk-i9ly.a4.cloudera.site/dex/api/v1

cde session create --type spark-scala --name dvsession --mount-1-resource dev_files --executor-cores 2 --executor-memory 4 --vcluster-endpoint https://l9hb4klq.cde-f25hdn5h.mega-hol.oldk-i9ly.a4.cloudera.site/dex/api/v1

cde session interact --name dv-session --vcluster-endpoint https://l9hb4klq.cde-f25hdn5h.mega-hol.oldk-i9ly.a4.cloudera.site/dex/api/v1
```

Interactively Test the Application:

```
// Read input file
val inputFile = "/app/mount/sonnet.txt"
val outputDir = "s3a://mega-hol-buk-6419b4bf/data/dev"

// Perform word count
val textFile = spark.sparkContext.textFile(inputFile)
val wordCounts = textFile.flatMap(line => line.split("\\s+")).map(word => (word, 1)).reduceByKey(_ + _)

// Print WordCount
wordCounts.collect().foreach(println)

// Save results
wordCounts.saveAsTextFile(outputDir)
```

Test the code as a Spark App with a Spark Submit

```
cde spark submit \
  target/scala-2.13/cdeapp_2.13-0.1.jar
  class com.example.WordCount \
  sonnet.txt \
  s3a://paul-aug26-buk-a3c2b50a/data/test \
  --vcluster-endpoint https://6glgfdfb.cde-f25hdn5h.mega-hol.oldk-i9ly.a4.cloudera.site/dex/api/v1
```

You are ready to test the Spark Submit as a CDE Spark Job.

## 2. Push to git

This code is looking good. Let's push updates to the git repo.

```
git add cdeApp
git commit -m "developed app"
git push
```

We can now create a CDE Repository in order to import the application into the Virtual Cluster.

## 3. Sync with CDE repository

Create a CDE repository and create the CDE Spark Job using the contents.

```
cde repository create --name sparkAppRepoDev \
  --branch main \
  --url https://github.com/pdefusco/CDE_CICD_Articles.git \
  --vcluster-endpoint

cde repository sync --name sparkAppRepoDev \
  --vcluster-endpoint
```

## 4. Deploy using CLI

```
cde job create --name cde_spark_job_test \
  --type spark \
  --mount-1-resource sparkAppRepo \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file \
  --vcluster-endpoint

cde job run --name cde_spark_job_test \
  --executor-cores 4 \
  --executor-memory "2g" \
  --vcluster-endpoint

cde job run --name cde_spark_job_test \
  --executor-cores 2 \
  --executor-memory "4g" \
  --vcluster-endpoint

cde job run --name cde_spark_job_test \
  --executor-cores 5 \
  --executor-memory "8g" \
  --vcluster-endpoint
```

## 5. Monitor

Navigate to the Job Runs UI / run a few CDE CLI commands to check status.

```
cde job list --vcluster-endpoint
```

## 6. Promote to higher env using API by replicating repo and redeploy

Now that the job has succeeded, import it into the PRD cluster.

Create and sync the same Git repo from the PRD Cluster:

```
cde repository create --name sparkAppRepoPrd \
  --branch main
  --url https://github.com/pdefusco/CDE_CICD_Articles.git
  --v-cluster

cde repository sync --name sparkAppRepoPrd \
 --vcluster-endpoint

cde job create --name cde_spark_job_test \
  --type spark \
  --mount-1-resource sparkAppRepo \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file \
  --vcluster-endpoint

cde job run --name cde_spark_job_test \
  --executor-cores 4 \
  --executor-memory "2g" \
  --vcluster-endpoint
```
