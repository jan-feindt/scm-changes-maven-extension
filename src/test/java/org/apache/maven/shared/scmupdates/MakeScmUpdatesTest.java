package org.apache.maven.shared.scmupdates;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

import java.io.File;
import java.util.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the implementation of {@link MakeScmUpdates}.
 *
 * @author Norman Wiechmann
 * @version 1.0
 * @since 2013-10-08
 */
public class MakeScmUpdatesTest {
    MakeScmUpdates msc;

    @Before
    public void setUp() {
        msc = new MakeScmUpdates();
        msc.enabled = true;
        msc.baseDir = new File("").getAbsoluteFile();
        msc.logger = mock(Logger.class);
    }

    @Test
    public void readParameters() throws Exception {
        MavenSession session = mock(MavenSession.class);
        Properties props = new Properties();
        when(session.getUserProperties()).thenReturn(props);

        MavenProject project = new MavenProject();
        project.setFile(new File("").getAbsoluteFile());

        when(session.getTopLevelProject()).thenReturn(project);

        msc.readParameters(session);

        assertThat(msc.enabled, is(false));
    }

    @Test(expected = MavenExecutionException.class)
    public void getChangedFilesFromScmFailure()
        throws MavenExecutionException {
        msc.readUpdatedFiles();
    }

    @Test
    public void disabled() throws MavenExecutionException {
        MavenSession session = mock(MavenSession.class);
        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        when(session.getRequest()).thenReturn(request);

        msc.enabled = false;
        msc = PowerMockito.spy(msc);

        PowerMockito.doNothing().when(msc).readParameters((MavenSession) any());

        msc.afterProjectsRead(session);
        assertTrue(request.getSelectedProjects().isEmpty());
    }

    @Test
    public void nothingToDo() throws MavenExecutionException {
        MavenSession session = mock(MavenSession.class);
        msc = PowerMockito.spy(msc);

        // use default parameters
        PowerMockito.doNothing().when(msc).readParameters((MavenSession) any());

        // returns an empty list of changed files
        Set<String> updatedFiles = Collections.emptySet();
        PowerMockito.doReturn(updatedFiles).when(msc).readUpdatedFiles();

        MavenProject project = mock(MavenProject.class);
        when(session.getTopLevelProject()).thenReturn(project);

        msc.afterProjectsRead(session);
    }

    @Test
    public void normalFlow() throws MavenExecutionException {
        MavenSession session = mock(MavenSession.class);
        msc = PowerMockito.spy(msc);

        // use default parameters
        PowerMockito.doNothing().when(msc).readParameters((MavenSession) any());

        Set<String> updatedFiles = Collections.singleton("pom.xml");
        PowerMockito.doReturn(updatedFiles).when(msc).readUpdatedFiles();

        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml").getAbsoluteFile());

        when(session.getTopLevelProject()).thenReturn(project);
        when(session.getProjects()).thenReturn(Arrays.asList(project));

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        when(session.getRequest()).thenReturn(request);

        msc.afterProjectsRead(session);

        List<String> selectedProjects = request.getSelectedProjects();
        assertThat(selectedProjects.toString(), is("[unknown:empty-project]"));
        assertThat(request.getMakeBehavior(), is(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM));
    }

    @Test
    public void alreadyBuildingUpstream() throws MavenExecutionException {
        MavenSession session = mock(MavenSession.class);
        msc = PowerMockito.spy(msc);

        // use default parameters
        PowerMockito.doNothing().when(msc).readParameters((MavenSession) any());

        Set<String> updatedFiles = Collections.singleton("pom.xml");
        PowerMockito.doReturn(updatedFiles).when(msc).readUpdatedFiles();

        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml").getAbsoluteFile());
        when(session.getTopLevelProject()).thenReturn(project);
        when(session.getProjects()).thenReturn(Arrays.asList(project));

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        request.setMakeBehavior(MavenExecutionRequest.REACTOR_MAKE_UPSTREAM);
        when(session.getRequest()).thenReturn(request);

        msc.afterProjectsRead(session);

        List<String> selectedProjects = request.getSelectedProjects();
        assertThat(selectedProjects.toString(), is("[unknown:empty-project]"));
        assertThat(request.getMakeBehavior(), is(MavenExecutionRequest.REACTOR_MAKE_BOTH));
    }

    @Test
    public void nothingToDoBecauseIgnoringRootPom() throws MavenExecutionException {
        MavenSession session = mock(MavenSession.class);
        msc.ignoreRootPom = true;
        msc = PowerMockito.spy(msc);

        // use default parameters
        PowerMockito.doNothing().when(msc).readParameters((MavenSession) any());

        Set<String> updatedFiles = Collections.singleton("pom.xml");
        PowerMockito.doReturn(updatedFiles).when(msc).readUpdatedFiles();

        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml").getAbsoluteFile());
        when(session.getTopLevelProject()).thenReturn(project);
        when(session.getProjects()).thenReturn(Arrays.asList(project));

        msc.afterProjectsRead(session);
    }

    @Test
    public void nothingToDoBecauseIgnoringRandomSubdirectory() throws MavenExecutionException {
        MavenSession session = mock(MavenSession.class);
        msc = PowerMockito.spy(msc);

        // use default parameters
        PowerMockito.doNothing().when(msc).readParameters((MavenSession) any());

        Set<String> updatedFiles = Collections.singleton("foo/pom.xml");
        PowerMockito.doReturn(updatedFiles).when(msc).readUpdatedFiles();

        MavenProject project = new MavenProject();
        project.setFile(new File("pom.xml").getAbsoluteFile());

        when(session.getTopLevelProject()).thenReturn(project);
        when(session.getProjects()).thenReturn(Arrays.asList(project));

        msc.afterProjectsRead(session);
    }
}