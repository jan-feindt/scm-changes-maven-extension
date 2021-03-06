package org.apache.maven.shared.scmupdates;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.Arrays.asList;
import static org.codehaus.plexus.util.FileUtils.fileRead;

/**
 * Builds only projects containing files specified in ".scm-updates" file within root folder.
 * <p/>
 * Based on {@link org.apache.maven.shared.scmchanges.MakeScmChanges} by
 * <a href="mailto:dfabulich@apache.org">Dan Fabulich</a>.
 *
 * @author Norman Wiechmann
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "scm-updates")
public class MakeScmUpdates extends AbstractMavenLifecycleParticipant {
    @Requirement
    Logger logger;

    /**
     * make.ignoreRootPom: Ignore updates in the root POM file, which would normally cause a full rebuild
     */
    boolean ignoreRootPom = false;

    /**
     * Disabled by default; activate via -Dmake.scmUpdates=true
     */
    boolean enabled = false;

    /**
     * make.baseDir: Search SCM for modified files in this directory.
     * Defaults to ${project.baseDir} for the root project.
     */
    File baseDir;

    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        readParameters(session);

        if (!enabled) {
            logger.debug("make.scmUpdates = false, not modifying project list");
            return;
        }

        Set<String> updatedFiles = readUpdatedFiles();

        List<String> includedProjects = new ArrayList<String>();

        MavenProject topLevelProject = session.getTopLevelProject();
        for (String updatedFilePath : updatedFiles) {
            logger.info("Updated " + updatedFilePath);

            File updatedFile = new File(baseDir, updatedFilePath);

            if (ignoreRootPom && topLevelProject.getFile().getAbsoluteFile().equals(updatedFile)) {
                continue;
            }

            boolean found = false;
            // TODO There's a cleverer/faster way to code this, right? This is O(n^2)
            for (MavenProject project : session.getProjects()) {
                File projectDirectory = project.getFile().getParentFile();
                if (updatedFile.getAbsolutePath().startsWith(projectDirectory.getAbsolutePath() + File.separator)) {
                    if (topLevelProject.equals(project)) {
                        // If we include the top level project, then we'll build everything.
                        // We have to be very careful before allowing that to happen.

                        // In particular, if the modified file is in a subdirectory X that is not itself
                        // a Maven project, we don't want that one file to cause a full build. 
                        // i.e. we ignore updates that are in a random subdirectory.

                        // Is the top level project actually in the baseDir?
                        // Sometimes people have sibling child projects, e.g.
                        // <module>../child-project</module>
                        // If the top level project isn't the baseDir, then running the whole build may be rational.
                        if (baseDir.equals(projectDirectory.getAbsoluteFile())) {

                            // is the changed file the baseDir or one of its immediate descendants?
                            // That should probably provoke a rebuild.
                            if (!(baseDir.equals(updatedFile) || baseDir.equals(updatedFile.getParentFile()))) {
                                // OK, so the changed file is in some random subdirectory of the baseDir.
                                // Skip it.
                                logger.debug("Not considering top level project for " + updatedFile +
                                    " because that would trigger a full rebuild.");
                                continue;
                            }
                        }
                    }
                    if (!includedProjects.contains(project)) {
                        logger.info("Including " + project);
                    }
                    includedProjects.add(project.getGroupId() + ":" + project.getArtifactId());
                    found = true;
                    break;
                }
            }
            if (!found) {
                logger.debug("Couldn't find file in any project root: " + updatedFile.getAbsolutePath());
            }
        }

        if (includedProjects.isEmpty()) {
            logger.info("No updates found. Nothing to do!");
            return;
        }

        MavenExecutionRequest request = session.getRequest();
        String makeBehavior = request.getMakeBehavior();
        if (makeBehavior == null) {
            request.setMakeBehavior(MavenExecutionRequest.REACTOR_MAKE_DOWNSTREAM);
        }
        if (MavenExecutionRequest.REACTOR_MAKE_UPSTREAM.equals(makeBehavior)) {
            request.setMakeBehavior(MavenExecutionRequest.REACTOR_MAKE_BOTH);
        }

        request.setSelectedProjects(includedProjects);
    }

    void readParameters(MavenSession session) throws MavenExecutionException {
        Properties sessionProps = session.getUserProperties();

        enabled = Boolean.parseBoolean(sessionProps.getProperty("make.scmUpdates", "false"));
        ignoreRootPom = Boolean.parseBoolean(sessionProps.getProperty("make.ignoreRootPom", "false"));

        String basePath = sessionProps.getProperty("make.baseDir");
        if (basePath != null) {
            baseDir = new File(basePath).getAbsoluteFile();
        } else {
            baseDir = session.getTopLevelProject().getBasedir().getAbsoluteFile();
        }
    }

    Set<String> readUpdatedFiles() throws MavenExecutionException {
        Set<String> updatedFiles = new HashSet<String>();

        String savedFileListPath = (new File(baseDir, ".scm-updates")).getAbsolutePath();
        try {
            String fileContent = fileRead(savedFileListPath, "UTF-8");
            String lineSeparator = System.getProperty("line.separator");
            updatedFiles.addAll(asList(fileContent.split(lineSeparator)));
        } catch (IOException e) {
            throw new MavenExecutionException(
                "Failed to read saved list of updated files", e);
        }

        return updatedFiles;
    }
}