/*
 * Copyright (C) 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.templates;

import com.google.api.services.bigquery.model.*;
import com.google.cloud.Timestamp;
import com.google.cloud.spanner.Options.RpcPriority;
import com.google.cloud.teleport.metadata.Template;
import com.google.cloud.teleport.metadata.TemplateCategory;
import com.google.cloud.teleport.v2.common.UncaughtExceptionLogger;

import java.util.ArrayList;
import java.util.List;

import com.google.cloud.teleport.v2.options.SpannerChangeStreamsToBigQueryDeleteLogTableOptions;
import com.google.common.collect.ImmutableList;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.spanner.SpannerConfig;
import org.apache.beam.sdk.io.gcp.spanner.SpannerIO;
import org.apache.beam.sdk.io.gcp.spanner.changestreams.model.DataChangeRecord;
import org.apache.beam.sdk.io.gcp.spanner.changestreams.model.Mod;
import org.apache.beam.sdk.io.gcp.spanner.changestreams.model.ModType;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.ParDo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link SpannerChangeStreamsToBigQueryDeleteLogTable} pipeline streams change stream record(s), filters out all but
 * delete events and writes the delete events to a BigQuery table.
 *
 * <p>Based on template <a
 * href="https://github.com/GoogleCloudPlatform/DataflowTemplates/blob/main/v2/googlecloud-to-googlecloud/README_Spanner_Change_Streams_to_PubSub.md">README</a>
 * for instructions on how to use or modify this template.
 */
@Template(
    name = "Spanner_Change_Streams_to_BigQuery_Delete_Log_Table",
    category = TemplateCategory.STREAMING,
    displayName = "Cloud Spanner change streams to BigQuery changelog table",
    description = {
        "The Cloud Spanner change streams to the BigQuery changelog table is a streaming pipeline that streams Cloud Spanner data change records, filters out all events except DELETE and writes them into BigQuery table using Dataflow Runner V2.\n",
        "Learn more about <a href=\"https://cloud.google.com/spanner/docs/change-streams\">change streams</a>, <a href=\"https://cloud.google.com/spanner/docs/change-streams/use-dataflow\">how to build change streams Dataflow pipelines</a>, and <a href=\"https://cloud.google.com/spanner/docs/change-streams/use-dataflow#best_practices\">best practices</a>."
    },
    optionsClass = SpannerChangeStreamsToBigQueryDeleteLogTableOptions.class,
    flexContainerName = "spanner-changestreams-to-bigquery-delete-log-table",
    documentation =
        "https://cloud.google.com/dataflow/docs/guides/templates/provided/cloud-spanner-change-streams-to-pubsub",
    contactInformation = "https://cloud.google.com/support",
    requirements = {
        "The Cloud Spanner instance must exist before running the pipeline.",
        "The Cloud Spanner database must exist prior to running the pipeline.",
        "The Cloud Spanner metadata instance must exist prior to running the pipeline.",
        "The Cloud Spanner metadata database must exist prior to running the pipeline.",
        "The Cloud Spanner change stream must exist prior to running the pipeline."
    },
    streaming = true)
public class SpannerChangeStreamsToBigQueryDeleteLogTable {
    private static final Logger LOG = LoggerFactory.getLogger(SpannerChangeStreamsToPubSub.class);
    private static final String USE_RUNNER_V2_EXPERIMENT = "use_runner_v2";
    private static final String COMMIT_TS_COLUMN = "commit_ts";
    private static final String IMPORT_TS_COLUMN = "import_ts";
    private static final String KEYS_COLUMN = "keys";
    private static final String TABLE_NAME_COLUMN = "table_name";

    public static void main(String[] args) {
        UncaughtExceptionLogger.register();

        LOG.info("Starting Input Messages to Pub/Sub");

        SpannerChangeStreamsToBigQueryDeleteLogTableOptions options =
            PipelineOptionsFactory.fromArgs(args).as(SpannerChangeStreamsToBigQueryDeleteLogTableOptions.class);

        run(options);
    }

    private static String getSpannerProjectId(SpannerChangeStreamsToBigQueryDeleteLogTableOptions options) {
        return options.getSpannerProjectId().isEmpty()
            ? options.getProject()
            : options.getSpannerProjectId();
    }

    public static PipelineResult run(SpannerChangeStreamsToBigQueryDeleteLogTableOptions options) {
        LOG.info("Requested Message Format is " + options.getOutputDataFormat());
        options.setStreaming(true);
        options.setEnableStreamingEngine(true);

        final Pipeline pipeline = Pipeline.create(options);
        // Get the Spanner project, instance, database, metadata instance, metadata database
        // change stream, pubsub topic, and pubsub api parameters.
        String spannerProjectId = getSpannerProjectId(options);
        String instanceId = options.getSpannerInstanceId();
        String databaseId = options.getSpannerDatabase();
        String metadataInstanceId = options.getSpannerMetadataInstanceId();
        String metadataDatabaseId = options.getSpannerMetadataDatabase();
        String changeStreamName = options.getSpannerChangeStreamName();

        // Retrieve and parse the start / end timestamps.
        Timestamp startTimestamp =
            options.getStartTimestamp().isEmpty()
                ? Timestamp.now()
                : Timestamp.parseTimestamp(options.getStartTimestamp());
        Timestamp endTimestamp =
            options.getEndTimestamp().isEmpty()
                ? Timestamp.MAX_VALUE
                : Timestamp.parseTimestamp(options.getEndTimestamp());

        // Add use_runner_v2 to the experiments option, since Change Streams connector is only supported
        // on Dataflow runner v2.
        List<String> experiments = options.getExperiments();
        if (experiments == null) {
            experiments = new ArrayList<>();
        }
        if (!experiments.contains(USE_RUNNER_V2_EXPERIMENT)) {
            experiments.add(USE_RUNNER_V2_EXPERIMENT);
        }
        options.setExperiments(experiments);

        String metadataTableName =
            options.getSpannerMetadataTableName() == null
                ? null
                : options.getSpannerMetadataTableName();

        final RpcPriority rpcPriority = options.getRpcPriority();
        SpannerConfig spannerConfig =
            SpannerConfig.create()
                .withHost(ValueProvider.StaticValueProvider.of(options.getSpannerHost()))
                .withProjectId(spannerProjectId)
                .withInstanceId(instanceId)
                .withDatabaseId(databaseId);
        // Propagate database role for fine-grained access control on change stream.
        if (options.getSpannerDatabaseRole() != null) {
            spannerConfig =
                spannerConfig.withDatabaseRole(
                    ValueProvider.StaticValueProvider.of(options.getSpannerDatabaseRole()));
        }
        pipeline
            .apply("Read Spanner Change Stream",
                SpannerIO.readChangeStream()
                    .withSpannerConfig(spannerConfig)
                    .withMetadataInstance(metadataInstanceId)
                    .withMetadataDatabase(metadataDatabaseId)
                    .withChangeStreamName(changeStreamName)
                    .withInclusiveStartAt(startTimestamp)
                    .withInclusiveEndAt(endTimestamp)
                    .withRpcPriority(rpcPriority)
                    .withMetadataTable(metadataTableName))
            .apply("Filter out other than DELETE types and convert to TableRow",
                ParDo.of(new DoFn<DataChangeRecord, TableRow>() {
                    @ProcessElement
                    public void processElement(ProcessContext c) {
                        DataChangeRecord record = c.element();

                        if (record == null || record.getModType() != ModType.DELETE) {
                            return;
                        }

                        for (Mod mod : record.getMods()) {
                            c.output(new TableRow()
                                .set(COMMIT_TS_COLUMN, record.getCommitTimestamp())
                                .set(TABLE_NAME_COLUMN, record.getTableName())
                                .set(KEYS_COLUMN, mod.getKeysJson())
                            );
                        }
                    }
                }))
            .apply("Write To BigQuery",
                BigQueryIO.<TableRow>write()
                    .withFormatFunction(row -> row.set(IMPORT_TS_COLUMN, Timestamp.now()))
                    .withoutValidation()
                    .to(options.getOutputTableSpec())
                    .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                    .withClustering(new Clustering().setFields(ImmutableList.of(TABLE_NAME_COLUMN, COMMIT_TS_COLUMN)))
                    .withTimePartitioning(new TimePartitioning().setField(IMPORT_TS_COLUMN).setType("HOUR"))
                    .withSchema(new TableSchema()
                        .setFields(ImmutableList.of(
                            new TableFieldSchema().setName(COMMIT_TS_COLUMN).setType("TIMESTAMP"),
                            new TableFieldSchema().setName(IMPORT_TS_COLUMN).setType("TIMESTAMP"),
                            new TableFieldSchema().setName(KEYS_COLUMN).setType("JSON"),
                            new TableFieldSchema().setName(TABLE_NAME_COLUMN).setType("STRING"))))
                    .withCreateDisposition(BigQueryIO.Write.CreateDisposition.CREATE_IF_NEEDED)
                    .withWriteDisposition(BigQueryIO.Write.WriteDisposition.WRITE_APPEND));

        return pipeline.run();
    }
}



