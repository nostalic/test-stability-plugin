package de.esailors.jenkins.teststability;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.ClassResult;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.net.URL;

import static de.esailors.jenkins.teststability.Helper.expectConsistentMixedResults;
import static de.esailors.jenkins.teststability.Helper.expectSuccessAfter2MixedResults;
import static de.esailors.jenkins.teststability.Helper.getClassResult;
import static de.esailors.jenkins.teststability.Helper.testResult;
import static org.assertj.core.api.Assertions.assertThat;

// adapted from AttachmentPublisherPipelineTest in jenkinsci/junit-attachments-plugin
public class PipelineTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void pipelineStabilityResults() throws Exception {
        WorkflowJob project = j.jenkins.createProject(WorkflowJob.class, "test-job");

        Run build1 = runBuild(project, "workspaceMixedResults.zip", "pipelineWithoutPublisher.groovy", Result.UNSTABLE);
        ClassResult cr1 = getClassResult(testResult(build1), "test.foo.bar", "DefaultIntegrationTest");
        StabilityTestAction sta1 = cr1.getTestAction(StabilityTestAction.class);
        // Check that the stability publisher was left out.
        assertThat(sta1).isNull();

        // Repeat the (non-published) build, with the same results, but then delete the build. Ref JENKINS-42610
        Run build2 = runBuild(project, "workspaceMixedResults.zip", "pipelineWithoutPublisher.groovy", Result.UNSTABLE);
        ClassResult cr2 = getClassResult(testResult(build1), "test.foo.bar", "DefaultIntegrationTest");
        StabilityTestAction sta2 = cr2.getTestAction(StabilityTestAction.class);
        // Check that the stability publisher was left out.
        assertThat(sta2).isNull();
        // Because the records are deleted, and the stability was never recorded, it should be as if this build never happened
        build2.delete();

        // Repeat the build, with the same results, but now add the stability publisher.
        // This will force a call to buildUpInitialHistory for any failing tests.
        Run build3 = runBuild(project, "workspaceMixedResults.zip", "pipelineWithPublisher.groovy", Result.UNSTABLE);
        expectConsistentMixedResults(testResult(build3));

        // Repeat the (published) build, with the same results, but then delete the build. Ref JENKINS-42610
        Run build4 = runBuild(project, "workspaceMixedResults.zip", "pipelineWithPublisher.groovy", Result.UNSTABLE);
        expectConsistentMixedResults(testResult(build4));
        // Because the records are deleted, including the StabilityTestAction, it should be as if this build never happened
        build4.delete();

        // Build again, but now all tests pass:
        Run build5 = runBuild(project, "workspaceAllPass.zip", "pipelineWithPublisher.groovy", Result.SUCCESS);
        expectSuccessAfter2MixedResults(testResult(build5));
    }

    // Creates a job from the given workspace zip file, builds it and returns the WorkflowRun
    private WorkflowRun runBuild(WorkflowJob project, String workspaceZip, String pipelineFile, Result expectedStatus) throws Exception {
        FilePath workspace = j.jenkins.getWorkspaceFor(project);
        FilePath wsZip = workspace.child("workspace.zip");
        wsZip.copyFrom(getClass().getResource(workspaceZip));
        wsZip.unzip(workspace);
        for (FilePath f : workspace.list()) {
            f.touch(System.currentTimeMillis());
        }

        project.setDefinition(new CpsFlowDefinition(fileContentsFromResources(pipelineFile), true));

        WorkflowRun run = j.assertBuildStatus(expectedStatus, project.scheduleBuild2(0).get());
        return run;
    }

    private String fileContentsFromResources(String fileName) throws IOException {
        String fileContents = null;

        URL url = getClass().getResource(fileName);
        if (url != null) {
            fileContents = IOUtils.toString(url);
        }

        return fileContents;
    }
}
