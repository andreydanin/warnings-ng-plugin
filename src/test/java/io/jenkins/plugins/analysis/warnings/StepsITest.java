package io.jenkins.plugins.analysis.warnings;

import java.io.IOException;

import org.apache.commons.io.FilenameUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.junit.jupiter.api.Tag;
import org.jvnet.hudson.test.JenkinsRule;

import edu.hm.hafner.analysis.Issues;
import static edu.hm.hafner.analysis.assertj.Assertions.*;
import io.jenkins.plugins.analysis.core.steps.AnalysisResult;
import io.jenkins.plugins.analysis.core.steps.BuildIssue;
import io.jenkins.plugins.analysis.core.steps.PublishIssuesStep;
import io.jenkins.plugins.analysis.core.steps.ResultAction;
import io.jenkins.plugins.analysis.core.steps.ScanForIssuesStep;
import io.jenkins.plugins.analysis.core.steps.StaticAnalysisTool;

import hudson.FilePath;
import hudson.model.Result;

/**
 * Integration tests for pipeline support in analysis-core.
 *
 * @author Ullrich Hafner
 * @see ScanForIssuesStep
 * @see PublishIssuesStep
 */
@Tag("IntegrationTest")
@SuppressWarnings({"OverlyBroadThrowsClause", "ProhibitedExceptionDeclared"})
public class StepsITest {
    private static final String PUBLISH_ISSUES_STEP = "publishIssues issues:[issues]";

    /** Starts Jenkins and provides several useful helper methods. */
    @Rule
    public final JenkinsRule j = new JenkinsRule();

    /**
     * Runs the Eclipse parser on an output file that contains several issues: the build should report 8 issues.
     *
     * @throws Exception
     *         in case of an error
     */
    @Test
    public void shouldFindAllEclipseIssues() throws Exception {
        WorkflowJob job = createJobWithWorkspaceFile("eclipse.txt");
        job.setDefinition(parseAndPublish(Eclipse.class));

        AnalysisResult result = scheduleBuild(job);

        assertThat(result.getTotalSize()).isEqualTo(8);
        assertThat(result.getIssues()).hasSize(8);
    }

    /**
     * Runs the JavaC parser on an output file of the Eclipse compiler: the build should report no issues.
     *
     * @throws Exception
     *         in case of an error
     */
    @Test
    public void shouldNoJavacIssuesInEclipseOutput() throws Exception {
        WorkflowJob job = createJobWithWorkspaceFile("eclipse.txt");
        job.setDefinition(parseAndPublish(Java.class));

        AnalysisResult result = scheduleBuild(job);

        assertThat(result.getTotalSize()).isEqualTo(0);
    }

    /**
     * Runs the all Java parsers on three output files: the build should report issues of all tools.
     *
     * @throws Exception
     *         in case of an error
     */
    @Test
    public void shouldCombineIssuesOfSeveralFiles() throws Exception {
        WorkflowJob job = createJobWithWorkspaceFile("eclipse.txt", "javadoc.txt", "javac.txt");
        job.setDefinition(asStage(createScanForIssuesStep(Java.class, "java"),
                createScanForIssuesStep(Eclipse.class, "eclipse"),
                createScanForIssuesStep(JavaDoc.class, "javadoc"),
                "publishIssues issues:[java, eclipse, javadoc]"));

        AnalysisResult result = scheduleBuild(job);

        Issues<BuildIssue> issues = result.getIssues();
        assertThat(issues.filter(issue -> "eclipse".equals(issue.getOrigin()))).hasSize(8);
        assertThat(issues.filter(issue -> "java".equals(issue.getOrigin()))).hasSize(2);
        assertThat(issues.filter(issue -> "javadoc".equals(issue.getOrigin()))).hasSize(6);
        assertThat(issues.getToolNames()).containsExactlyInAnyOrder("java", "javadoc", "eclipse");
        assertThat(result.getIssues()).hasSize(8 + 2 + 6);
    }

    private CpsFlowDefinition parseAndPublish(final Class<? extends StaticAnalysisTool> parserClass) {
        return asStage(createScanForIssuesStep(parserClass), PUBLISH_ISSUES_STEP);
    }

    private String createScanForIssuesStep(final Class<? extends StaticAnalysisTool> parserClass) {
        return createScanForIssuesStep(parserClass, "issues");
    }

    private String createScanForIssuesStep(final Class<? extends StaticAnalysisTool> parserClass, final String issuesName) {
        return String.format("def %s = scanForIssues tool: [$class: '%s'], pattern:'**/*issues.txt'", issuesName, parserClass.getSimpleName());
    }

    private WorkflowJob createJobWithWorkspaceFile(final String... fileNames) throws IOException, InterruptedException {
        WorkflowJob job = createJob();
        copyFilesToWorkspace(job, fileNames);
        return job;
    }

    private void copyFilesToWorkspace(final WorkflowJob job, final String... fileNames) throws IOException, InterruptedException {
        FilePath workspace = j.jenkins.getWorkspaceFor(job);
        for (String fileName : fileNames) {
            workspace.child(createWorkspaceFileName(fileName)).copyFrom(getClass().getResourceAsStream(fileName));
        }
    }

    private String createWorkspaceFileName(final String fileName) {
        return String.format("%s-issues.txt", FilenameUtils.getBaseName(fileName));
    }

    private WorkflowJob createJob() throws IOException {
        return j.jenkins.createProject(WorkflowJob.class, "scanForIssues");
    }

    /**
     * Schedules a new build for the specified job and returns the created {@link AnalysisResult} after the build has
     * been finished.
     *
     * @param job
     *         the job to schedule
     *
     * @return the created {@link AnalysisResult}
     */
    private AnalysisResult scheduleBuild(final WorkflowJob job) throws Exception {
        WorkflowRun run = j.assertBuildStatus(Result.SUCCESS, job.scheduleBuild2(0));

        ResultAction action = run.getAction(ResultAction.class);
        assertThat(action).isNotNull();

        return action.getResult();
    }

    private CpsFlowDefinition asStage(final String... steps) {
        StringBuilder script = new StringBuilder();
        script.append("node {\n");
        script.append("  stage ('Integration Test') {\n");
        for (String step : steps) {
            script.append("    ");
            script.append(step);
            script.append('\n');
        }
        script.append("  }\n");
        script.append("}\n");


        System.out.println("----------------------------------------------------------------------");
        System.out.println(script);
        System.out.println("----------------------------------------------------------------------");
        return new CpsFlowDefinition(script.toString(), true);
    }
}