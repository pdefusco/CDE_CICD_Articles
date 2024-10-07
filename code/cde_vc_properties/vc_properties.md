## CDE Virtual Cluster Properties

Cloudera Data Engineering (CDE) is a platform designed to streamline and optimize data workflows in modern enterprises. It provides a comprehensive environment for building, managing, and scaling data pipelines, offering robust tools for data ingestion, processing, and transformation. With support for both batch and real-time data, it allows data engineers to handle large-scale, complex datasets with ease, ensuring efficient data processing across hybrid and multi-cloud environments.

A CDE Virtual Cluster is an isolated, scalable compute environment within the Cloudera Data Engineering service, primarily utilized for running Spark and Airflow pipelines. Unlike traditional clusters, CDE virtual clusters allow for multi-tenancy and resource isolation, meaning multiple teams can run different workloads on separate virtual clusters while sharing the same underlying infrastructure.

When running Spark pipelines at scale it might be useful to set global Spark Job properties. In CDE you can do exactly this. You can use the CDE API or UI to modify Spark Job global properties so all your Spark Jobs inherit these default configurations.
