package com.google.cloud.teleport.v2.templates;

import com.google.cloud.spanner.Options;
import com.google.cloud.teleport.metadata.TemplateParameter;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Validation;
import org.apache.beam.sdk.options.ValueProvider; /**
 * The {@link com.google.cloud.teleport.v2.options.SpannerChangeStreamsToPubSubOptions} interface provides the custom execution options
 * passed by the executor at the command-line.
 */
public interface SpannerChangeStreamsToBigQueryDeleteLogTableOptions extends DataflowPipelineOptions {

    @TemplateParameter.ProjectId(
        order = 1,
        optional = true,
        description = "Spanner Project ID",
        helpText =
            "Project to read change streams from. The default for this parameter is the project "
                + "where the Dataflow pipeline is running.")
    @Default.String("")
    String getSpannerProjectId();

    void setSpannerProjectId(String projectId);

    @TemplateParameter.Text(
        order = 2,
        description = "Spanner instance ID",
        helpText = "The Spanner instance to read change streams from.")
    @Validation.Required
    String getSpannerInstanceId();

    void setSpannerInstanceId(String spannerInstanceId);

    @TemplateParameter.Text(
        order = 3,
        description = "Spanner database",
        helpText = "The Spanner database to read change streams from.")
    @Validation.Required
    String getSpannerDatabase();

    void setSpannerDatabase(String spannerDatabase);

    @TemplateParameter.Text(
        order = 4,
        optional = true,
        description = "Spanner database role",
        helpText =
            "Database role user assumes while reading from the change stream. The database role"
                + " should have required privileges to read from change stream. If a database role is"
                + " not specified, the user should have required IAM permissions to read from the"
                + " database.")
    String getSpannerDatabaseRole();

    void setSpannerDatabaseRole(String spannerDatabaseRole);

    @TemplateParameter.Text(
        order = 5,
        description = "Spanner metadata instance ID",
        helpText = "The Spanner instance to use for the change streams connector metadata table.")
    @Validation.Required
    String getSpannerMetadataInstanceId();

    void setSpannerMetadataInstanceId(String spannerMetadataInstanceId);

    @TemplateParameter.Text(
        order = 6,
        description = "Spanner metadata database",
        helpText =
            "The Spanner database to use for the change streams connector metadata table. For change"
                + " streams tracking all tables in a database, we recommend putting the metadata"
                + " table in a separate database.")
    @Validation.Required
    String getSpannerMetadataDatabase();

    void setSpannerMetadataDatabase(String spannerMetadataDatabase);

    @TemplateParameter.Text(
        order = 7,
        optional = true,
        description = "Cloud Spanner metadata table name",
        helpText =
            "The Cloud Spanner change streams connector metadata table name to use. If not provided,"
                + " a Cloud Spanner change streams connector metadata table will automatically be"
                + " created during the pipeline flow. This parameter must be provided when updating"
                + " an existing pipeline and should not be provided otherwise.")
    String getSpannerMetadataTableName();

    void setSpannerMetadataTableName(String value);

    @TemplateParameter.Text(
        order = 8,
        description = "Spanner change stream",
        helpText = "The name of the Spanner change stream to read from.")
    @Validation.Required
    String getSpannerChangeStreamName();

    void setSpannerChangeStreamName(String spannerChangeStreamName);

    @TemplateParameter.DateTime(
        order = 9,
        optional = true,
        description = "The timestamp to read change streams from",
        helpText =
            "The starting DateTime, inclusive, to use for reading change streams"
                + " (https://tools.ietf.org/html/rfc3339). For example, 2022-05-05T07:59:59Z."
                + " Defaults to the timestamp when the pipeline starts.")
    @Default.String("")
    String getStartTimestamp();

    void setStartTimestamp(String startTimestamp);

    @TemplateParameter.DateTime(
        order = 10,
        optional = true,
        description = "The timestamp to read change streams to",
        helpText =
            "The ending DateTime, inclusive, to use for reading change streams"
                + " (https://tools.ietf.org/html/rfc3339). Ex-2022-05-05T07:59:59Z. Defaults to an"
                + " infinite time in the future.")
    @Default.String("")
    String getEndTimestamp();

    void setEndTimestamp(String startTimestamp);

    @TemplateParameter.Text(
        order = 11,
        optional = true,
        description = "Cloud Spanner Endpoint to call",
        helpText = "The Cloud Spanner endpoint to call in the template. Only used for testing.",
        example = "https://spanner.googleapis.com")
    @Default.String("https://spanner.googleapis.com")
    String getSpannerHost();

    void setSpannerHost(String value);

    @TemplateParameter.Text(
        order = 12,
        optional = true,
        description = "Output data format",
        helpText =
            "The format of the output to Pub/Sub. Allowed formats are JSON, AVRO. Default is JSON.")
    @Default.String("JSON")
    String getOutputDataFormat();

    void setOutputDataFormat(String outputDataFormat);

    @TemplateParameter.Enum(
        order = 13,
        enumOptions = {
            @TemplateParameter.TemplateEnumOption("LOW"),
            @TemplateParameter.TemplateEnumOption("MEDIUM"),
            @TemplateParameter.TemplateEnumOption("HIGH")
        },
        optional = true,
        description = "Priority for Spanner RPC invocations",
        helpText =
            "The request priority for Cloud Spanner calls. The value must be one of:"
                + " [HIGH,MEDIUM,LOW].")
    @Default.Enum("HIGH")
    Options.RpcPriority getRpcPriority();

    void setRpcPriority(Options.RpcPriority rpcPriority);

    @TemplateParameter.BigQueryTable(
        order = 14,
        description = "BigQuery output table",
        helpText =
            "BigQuery table location to write the output to. The name should be in the format "
                + "`<project>:<dataset>.<table_name>`. The table's schema must match input objects.")
    ValueProvider<String> getOutputTableSpec();

    void setOutputTableSpec(ValueProvider<String> value);
}
