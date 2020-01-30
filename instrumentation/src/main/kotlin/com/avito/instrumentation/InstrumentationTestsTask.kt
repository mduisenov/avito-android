package com.avito.instrumentation

import com.avito.android.stats.statsdConfig
import com.avito.bitbucket.bitbucketConfig
import com.avito.bitbucket.pullRequestId
import com.avito.cd.buildOutput
import com.avito.instrumentation.configuration.InstrumentationConfiguration
import com.avito.instrumentation.executing.ExecutionParameters
import com.avito.kotlin.dsl.getMandatoryStringProperty
import com.avito.report.model.Team
import com.avito.slack.model.SlackChannel
import com.avito.utils.gradle.envArgs
import com.avito.utils.gradle.kubernetesCredentials
import com.avito.utils.logging.ciLogger
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.mapProperty
import org.gradle.kotlin.dsl.property
import org.gradle.workers.IsolationMode
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@Suppress("UnstableApiUsage")
abstract class InstrumentationTestsTask @Inject constructor(
    objects: ObjectFactory,
    private val workerExecutor: WorkerExecutor
) : DefaultTask() {

    @InputFile
    val application: RegularFileProperty = objects.fileProperty()

    @InputFile
    val testApplication: RegularFileProperty = objects.fileProperty()

    @Optional
    @InputFile
    val impactAnalysisResult: RegularFileProperty = objects.fileProperty()

    @Optional
    @InputFile
    val apkOnTargetCommit: RegularFileProperty = objects.fileProperty()

    @Optional
    @InputFile
    val testApkOnTargetCommit: RegularFileProperty = objects.fileProperty()

    @Input
    val sendStatistics = objects.property<Boolean>()

    @Input
    val slackToken = objects.property<String>()

    @Input
    val buildId = objects.property<String>()

    @Input
    val buildUrl = objects.property<String>()

    @Input
    val testedVariantName = objects.property<String>()

    @Input
    val defaultBranch = objects.property<String>()

    @Input
    val gitCommit = objects.property<String>()

    @Input
    val fullTestSuite = objects.property<Boolean>()

    @Input
    val gitBranch = objects.property<String>()

    @Input
    val sourceCommitHash = objects.property<String>()

    @Input
    @Optional
    val suppressFailure = objects.property<Boolean>()

    @Input
    @Optional
    val targetCommit = objects.property<String?>()

    @Input
    @Optional
    val targetBranch = objects.property<String>()

    @Input
    val instrumentationConfiguration = objects.property<InstrumentationConfiguration.Data>()

    @Input
    val parameters = objects.property<ExecutionParameters>()

    @Internal
    val reportApiUrl = objects.property<String>()

    @Internal
    val reportApiFallbackUrl = objects.property<String>()

    @Internal
    val reportViewerUrl = objects.property<String>()

    @Internal
    val registry = objects.property<String>()

    @Internal
    val unitToChannelMapping = objects.mapProperty<Team, SlackChannel>()

    @OutputDirectory
    val output: DirectoryProperty = objects.directoryProperty()

    @TaskAction
    fun doWork() {
        val configuration = instrumentationConfiguration.get()
        val reportCoordinates = configuration.instrumentationParams.reportCoordinates()

        val getTestResultsAction = GetTestResultsAction(
            reportApiUrl = reportApiUrl.get(),
            reportApiFallbackUrl = reportApiFallbackUrl.get(),
            reportViewerUrl = reportViewerUrl.get(),
            reportCoordinates = reportCoordinates,
            ciLogger = ciLogger,
            buildId = buildId.get(),
            gitBranch = gitBranch.get(),
            gitCommit = gitCommit.get(),
            configuration = configuration
        )
        val buildOutput = project.buildOutput.get()
        val testResults = getTestResultsAction.getTestResults()
        buildOutput.testResults[configuration.name] = testResults

        //todo новое api, когда выйдет в stable
        // https://docs.gradle.org/5.6/userguide/custom_tasks.html#using-the-worker-api
        @Suppress("DEPRECATION")
        workerExecutor.submit(InstrumentationTestsAction::class.java) { workerConfiguration ->
            workerConfiguration.isolationMode = IsolationMode.NONE
            workerConfiguration.setParams(
                InstrumentationTestsAction.Params(
                    mainApk = application.get().asFile,
                    testApk = testApplication.get().asFile,
                    apkOnTargetCommit = apkOnTargetCommit.orNull?.asFile,
                    testApkOnTargetCommit = testApkOnTargetCommit.orNull?.asFile,
                    instrumentationConfiguration = configuration,
                    executionParameters = parameters.get(),
                    buildId = buildId.get(),
                    buildUrl = buildUrl.get(),
                    targetCommit = targetCommit.orNull,
                    kubernetesCredentials = project.kubernetesCredentials,
                    projectName = project.name,
                    currentBranch = gitBranch.get(),
                    sourceCommitHash = sourceCommitHash.get(),
                    testedVariantName = testedVariantName.get(),
                    suppressFailure = suppressFailure.getOrElse(false),
                    impactAnalysisResult = impactAnalysisResult.asFile.orNull,
                    logger = ciLogger,
                    outputDir = output.get().asFile,
                    sendStatistics = sendStatistics.get(),
                    slackToken = slackToken.get(),
                    isFullTestSuite = fullTestSuite.get(),
                    downsamplingFactor = project.envArgs.testDownsamplingFactor,
                    reportId = testResults.reportId,
                    reportApiUrl = reportApiUrl.get(),
                    fileStorageUrl = project.fileStorageUrl,
                    pullRequestId = project.pullRequestId.orNull,
                    bitbucketConfig = project.bitbucketConfig.get(),
                    statsdConfig = project.statsdConfig.get(),
                    unitToChannelMapping = unitToChannelMapping.get(),
                    reportApiFallbackUrl = reportApiFallbackUrl.get(),
                    reportViewerUrl = reportViewerUrl.get(),
                    registry = registry.get()
                )
            )
        }
    }

    // TODO: extract to a config to file-storage module
    private val Project.fileStorageUrl: String
        get() = this.getMandatoryStringProperty("avito.fileStorage.url")
}
