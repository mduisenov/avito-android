package com.avito.instrumentation

import com.avito.cd.CdBuildResult
import com.avito.instrumentation.configuration.InstrumentationConfiguration
import com.avito.instrumentation.report.Report
import com.avito.report.ReportViewer
import com.avito.report.ReportsApi
import com.avito.report.model.ReportCoordinates
import com.avito.utils.logging.CILogger
import org.gradle.api.GradleException

/**
 * Этот action может ввести в заблуждение своим названием.
 * Он компонует доступные после этапа конфигурации данные в TestResults,
 * который содержит ссылки на будущие результаты
 */
internal class GetTestResultsAction(
    reportApiUrl: String,
    reportApiFallbackUrl: String,
    reportViewerUrl: String,
    private val reportCoordinates: ReportCoordinates,
    private val ciLogger: CILogger,
    private val buildId: String,
    private val report: Report = Report.Impl(
        reportsApi = ReportsApi.create(
            host = reportApiUrl,
            fallbackUrl = reportApiFallbackUrl,
            logger = { message, error -> ciLogger.debug(message, error) }
        ),
        logger = ciLogger,
        reportCoordinates = reportCoordinates,
        buildId = buildId
    ),
    private val reportViewer: ReportViewer = ReportViewer.Impl(reportViewerUrl),
    private val gitBranch: String,
    private val gitCommit: String,
    private val configuration: InstrumentationConfiguration.Data
) {

    fun getTestResults(): CdBuildResult.TestResults {
        checkPreconditions()

        return CdBuildResult.TestResults(
            reportId = getReportId(),
            reportUrl = getReportUrl(reportCoordinates),
            reportCoordinates = CdBuildResult.TestResults.ReportCoordinates(
                planSlug = reportCoordinates.planSlug,
                jobSlug = reportCoordinates.jobSlug,
                runId = reportCoordinates.runId
            )
        )
    }

    private fun getReportUrl(reportCoordinates: ReportCoordinates): String {
        return reportViewer.generateReportUrl(reportCoordinates, false).toString()
    }

    private fun getReportId(): String? {
        report.tryCreate(
            apiUrl = "", //todo
            gitBranch = gitBranch,
            gitCommit = gitCommit
        )

        return report.tryGetId()
    }

    private fun checkPreconditions() {
        if (configuration.targets.isEmpty()) {
            val message = "There are no targets in ${configuration.name} configuration"
            ciLogger.critical(message)

            throw GradleException(message)
        }
    }
}
