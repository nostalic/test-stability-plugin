package de.esailors.jenkins.teststability;

import hudson.model.Descriptor;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.junit.ClassResult;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.tasks.junit.TestDataPublisher;
import hudson.util.DescribableList;
import org.jvnet.hudson.test.ExtractResourceSCM;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.TouchBuilder;

import static de.esailors.jenkins.teststability.Helper.expectConsistentMixedResults;
import static de.esailors.jenkins.teststability.Helper.expectMixedResultsAfterSuccess;
import static de.esailors.jenkins.teststability.Helper.expectSuccessAfter2MixedResults;
import static de.esailors.jenkins.teststability.Helper.getClassResult;
import static de.esailors.jenkins.teststability.Helper.testResult;
import static org.assertj.core.api.Assertions.assertThat;

// adapted from AttachmentPublisherTest in jenkinsci/junit-attachments-plugin
public class FreestyleTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void freestyleStabilityResults() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        Run build1 = runBuild(project, "workspaceMixedResults.zip", Result.UNSTABLE, false);
        ClassResult classResult1 = getClassResult(testResult(build1), "test.foo.bar", "DefaultIntegrationTest");
        StabilityTestAction stability1 = classResult1.getTestAction(StabilityTestAction.class);
        // Check that the stability publisher was left out.
        assertThat(stability1).isNull();

        // Repeat the (non-published) build, with the same results, but then delete the build. Ref JENKINS-42610
        Run build2 = runBuild(project, "workspaceMixedResults.zip", Result.UNSTABLE, false);
        ClassResult classResult2 = getClassResult(testResult(build2), "test.foo.bar", "DefaultIntegrationTest");
        StabilityTestAction stability2 = classResult2.getTestAction(StabilityTestAction.class);
        // Check that the stability publisher was left out.
        assertThat(stability2).isNull();
        // Because the records are deleted, and the stability was never recorded, it should be as if this build never happened
        build2.delete();

        // Repeat the build, with the same results, but now add the stability publisher.
        // This will force a call to buildUpInitialHistory for any failing tests.
        Run build3 = runBuild(project, "workspaceMixedResults.zip", Result.UNSTABLE, true);
        expectConsistentMixedResults(testResult(build3));

        // Repeat the (published) build, with the same results, but then delete the build. Ref JENKINS-42610
        Run build4 = runBuild(project, "workspaceMixedResults.zip", Result.UNSTABLE, true);
        expectConsistentMixedResults(testResult(build4));
        // Because the records are deleted, including the StabilityTestAction, it should be as if this build never happened
        build4.delete();

        // Build again, but now all tests pass:
        Run build5 = runBuild(project, "workspaceAllPass.zip", Result.SUCCESS, true);
        expectSuccessAfter2MixedResults(testResult(build5));
    }

    @Test
    public void buildUpInitialHistory() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();

        Run build1 = runBuild(project, "workspaceAllPass.zip", Result.SUCCESS, false);
        // Check that the stability publisher was left out.
        ClassResult classResult1 = getClassResult(testResult(build1), "test.foo.bar", "DefaultIntegrationTest");
        StabilityTestAction stability1 = classResult1.getTestAction(StabilityTestAction.class);
        assertThat(stability1).isNull();

        Run build2 = runBuild(project, "workspaceMixedResults.zip", Result.UNSTABLE, true);
        expectMixedResultsAfterSuccess(testResult(build2));
    }

    // Runs a dummy build with the given workspace zip file and returns the FreeStyleBuild (Run)
    private FreeStyleBuild runBuild(FreeStyleProject project, String workspaceZip, Result expectedStatus, boolean addPublisher) throws Exception {
        DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>> publishers =
                new DescribableList<TestDataPublisher, Descriptor<TestDataPublisher>>(project);
        if (addPublisher) {
            publishers.add(new StabilityTestDataPublisher());
        }

        project.setScm(new ExtractResourceSCM(getClass().getResource(workspaceZip)));
        project.getBuildersList().add(new TouchBuilder());

        JUnitResultArchiver archiver = new JUnitResultArchiver("*.xml");
        archiver.setTestDataPublishers(publishers);
        project.getPublishersList().add(archiver);

        FreeStyleBuild b = project.scheduleBuild2(0).get();
        j.assertBuildStatus(expectedStatus, b);

        return b;
    }

}
