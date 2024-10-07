# Basic CI / CD Workflow in Cloudera Data Engineering

1. Test jobs in CDE Session from local.  
2. Once ready for operationalization push to git.
3. Sync with CDE repository
4. Deploy using CLI
5. Monitor
6. Promote to higher env using API by replicating repo and redeploy

We will prototype and test the Iceberg Merge Into and Incremental Read Operations.

## 1. Test jobs in CDE Session from local

Interactively Test the Application. Navigate to the CDE Home Page and launch a PySpark Session. Leave default settings intact.

Copy the following cell into the notebook. Before running it, ensure that you have edited the "username" variable with your assigned user.

```
from os.path import exists
from pyspark.sql import SparkSession
import pyspark.sql.functions as F
from pyspark.sql.types import *

storageLocation = "<YOUR-STORAGE-LOCATION-HERE>"
username = "pauldefusco"
```

No more code edits are required. Continue running each code snippet below in separate cells in the notebook.

```
### LOAD HISTORICAL TRANSACTIONS FILE FROM CLOUD STORAGE
transactionsDf = spark.read.json("{0}/cicddemo/pyspark/trans/{1}/rawtransactions".format(storageLocation, username))
transactionsDf.printSchema()
```

#### Iceberg Merge Into

Create Transactions Iceberg table:

```
spark.sql("CREATE DATABASE IF NOT EXISTS SPARK_CATALOG.CICD_DB_{}".format(username))
spark.sql("DROP TABLE IF EXISTS spark_catalog.CICD_DB_{0}.TRANSACTIONS_{0} PURGE".format(username))

transactionsDf.writeTo("SPARK_CATALOG.CICD_DB_{0}.TRANSACTIONS_{0}".format(username)).using("iceberg").tableProperty("write.format.default", "parquet").createOrReplace()
```

Load New Batch of Transactions in Temp View:

```
trxBatchDf = spark.read.schema("credit_card_number string, credit_card_provider string, event_ts timestamp, latitude double, longitude double, transaction_amount long, transaction_currency string, transaction_type string").json("{0}/trans/{1}/trx_batch_1".format(storageLocation, username))
trxBatchDf.createOrReplaceTempView("trx_batch")
```

Run MERGE INTO in order to load new batch into Transactions table:

```
# PRE-MERGE COUNTS BY TRANSACTION TYPE:
spark.sql("""SELECT TRANSACTION_TYPE, COUNT(*) FROM spark_catalog.CICD_DB_{0}.TRANSACTIONS_{0} GROUP BY TRANSACTION_TYPE""".format(username)).show()

# MERGE OPERATION
spark.sql("""MERGE INTO spark_catalog.CICD_DB_{0}.TRANSACTIONS_{0} t   
USING (SELECT * FROM trx_batch) s          
ON t.credit_card_number = s.credit_card_number               
WHEN MATCHED AND t.transaction_amount < 1000 AND t.transaction_currency != "CHF" THEN UPDATE SET t.transaction_type = "invalid"
WHEN NOT MATCHED THEN INSERT *""".format(username))

# POST-MERGE COUNT:
spark.sql("""SELECT TRANSACTION_TYPE, COUNT(*) FROM spark_catalog.CICD_DB_{0}.TRANSACTIONS_{0} GROUP BY TRANSACTION_TYPE""".format(username)).show()
```

Test the code as a Spark App with a Spark Submit

```
export STORAGE_LOCATION="<YOUR-STORAGE-LOCATION-HERE>"
esport USERNAME=<"YOUR-CDP-USERNAME-HERE>"

cde spark submit \
  pyspark-app.py \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1 \
  --arg $STORAGE_LOCATION \
  --arg $USERNAME
```

You are ready to test the Spark Submit as a CDE Spark Job.

## 2. Push to git

This code is looking good. Let's push updates to the git repo.

```
gh auth login
git add code
git commit -m "developed pyspark job"
git push
gh repo create my-newrepo --public --source=. --remote=upstream --push
```

We can now create a CDE Repository in order to import the application into the Virtual Cluster.

## 3. Sync with CDE repository

Create a CDE repository and create the CDE Spark Job using the contents.

```
cde job delete \
  --name cde_spark_job_test \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository delete \
  --name sparkAppRepoDev \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository create --name sparkAppRepoDev \
  --branch main \
  --url https://github.com/pdefusco/CDE_CICD_Articles.git \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository sync --name sparkAppRepoDev \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1
```

## 4. Deploy using CLI

```
cde job create --name cde_spark_job_test \
  --type spark \
  --mount-1-resource sparkAppRepoDev \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file code/pyspark_example/pyspark-app.py\
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job run --name cde_spark_job_test \
  --executor-cores 4 \
  --executor-memory "2g" \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job run --name cde_spark_job_test \
  --executor-cores 2 \
  --executor-memory "4g" \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1
```

## 5. Monitor

Navigate to the Job Runs UI / run a few CDE CLI commands to check status.

```
# List all Jobs in the Virtual Cluster:
cde job list \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

# List all jobs in the Virtual Cluster whose name is "cde_spark_job_test":
cde job list \
  --filter 'name[eq]cde_spark_job_test' \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

# List all jobs in the Virtual Cluster whose job application file name equals "code/pyspark_example/pyspark-app.py":
cde job list \
  --filter 'spark.file[eq]code/pyspark_example/pyspark-app.py' \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

# List all runs for Job "cde_spark_job_test":
cde run list \
  --filter 'job[eq]cde_spark_job_test' \
  --vcluster-endpoint https://898n992w.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1
```

## 6. Promote to higher env using API by replicating repo and redeploy

Now that the job has succeeded, import it into the PRD cluster.

Create and sync the same Git repo from the PRD Cluster:

```
cde job delete \
  --name cde_spark_job_prd \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository delete \
  --name sparkAppRepoPrd \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository create --name sparkAppRepoPrd \
  --branch main \
  --url https://github.com/pdefusco/CDE_CICD_Articles.git \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository sync --name sparkAppRepoPrd \
 --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job create --name cde_spark_job_prd \
  --type spark \
  --mount-1-resource sparkAppRepoPrd \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file code/pyspark_example/pyspark-app.py\
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job run --name cde_spark_job_prd \
  --executor-cores 4 \
  --executor-memory "2g" \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1
```

## 7. Build Orchestration Pipeline with Airflow

```
cde job delete \
  --name airflow-orchestration \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job delete \
  --name cde_spark_job_gold \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job delete \
  --name cde_spark_job_silver \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job delete \
  --name cde_spark_job_bronze \
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde repository sync --name sparkAppRepoPrd \
 --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job create --name cde_spark_job_bronze \
  --type spark \
  --arg pauldefusco \
  --arg s3a://paul-aug26-buk-a3c2b50a/data/spark3_demo/pdefusco/icedemo \
  --mount-1-resource sparkAppRepoPrd \
  --python-env-resource-name Python-Env-Shared \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file code/pyspark_example/airflow_pipeline/001_Lakehouse_Bronze.py\
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job create --name cde_spark_job_silver \
  --type spark \
  --arg pauldefusco \
  --mount-1-resource sparkAppRepoPrd \
  --python-env-resource-name Python-Env-Shared \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file code/pyspark_example/airflow_pipeline/002_Lakehouse_Silver.py\
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job create --name cde_spark_job_gold \
  --type spark \
  --arg pauldefusco \
  --arg s3a://paul-aug26-buk-a3c2b50a/spark3_demo/data/pdefusco \
  --mount-1-resource sparkAppRepoPrd \
  --python-env-resource-name Python-Env-Shared \
  --executor-cores 2 \
  --executor-memory "4g" \
  --application-file code/pyspark_example/airflow_pipeline/003_Lakehouse_Gold.py\
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1

cde job create --name airflow-orchestration \
  --type airflow \
  --mount-1-resource sparkAppRepoPrd \
  --dag-file code/pyspark_example/airflow_pipeline/004_airflow_dag_git.py\
  --vcluster-endpoint https://vtr4tm46.cde-vwkzdqwc.paul-aug.a465-9q4k.cloudera.site/dex/api/v1
```
