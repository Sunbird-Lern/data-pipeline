FROM sunbird/flink:1.18.1-scala_2.12-java11

USER root
RUN apt-get update \
    && apt-get upgrade -y \
    && apt-get install -y wget \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN rm -f /opt/flink/lib/log4j-*.jar && \
    wget -q https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-core/2.17.1/log4j-core-2.17.1.jar -P /opt/flink/lib/ && \
    wget -q https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-api/2.17.1/log4j-api-2.17.1.jar -P /opt/flink/lib/ && \
    wget -q https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-slf4j-impl/2.17.1/log4j-slf4j-impl-2.17.1.jar -P /opt/flink/lib/ && \
    wget -q https://repo1.maven.org/maven2/org/apache/logging/log4j/log4j-1.2-api/2.17.1/log4j-1.2-api-2.17.1.jar -P /opt/flink/lib/
COPY target/jobs-distribution-1.0.tar.gz /tmp
USER flink
RUN tar -xvf /tmp/jobs-distribution-1.0.tar.gz -C $FLINK_HOME/lib/
RUN cp $FLINK_HOME/opt/flink-s3-fs-presto-1.18.1.jar $FLINK_HOME/lib/flink-aaa-s3-fs-presto-1.18.1.jar
USER root
RUN rm -f /tmp/jobs-distribution-1.0.tar.gz

COPY lib/flink-shaded-hadoop-2-uber-2.8.3-10.0.jar $FLINK_HOME/lib/
COPY lib/gcs-connector-hadoop2-2.2.0.jar $FLINK_HOME/lib/
COPY lib/hadoop-azure-3.3.1.jar $FLINK_HOME/lib/

RUN mkdir -p $FLINK_HOME/plugins/gs-fs-hadoop && mkdir -p $FLINK_HOME/plugins/azure-fs-hadoop

COPY lib/flink-gs-fs-hadoop-1.18.1.jar $FLINK_HOME/plugins/gs-fs-hadoop
COPY lib/flink-azure-fs-hadoop-1.18.1.jar $FLINK_HOME/plugins/azure-fs-hadoop

COPY lib/core-site.xml $FLINK_HOME/conf/
USER flink
