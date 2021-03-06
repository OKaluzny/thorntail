/**
 * Copyright 2015-2017 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.bootstrap.env;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.modules.maven.ArtifactCoordinates;
import org.wildfly.swarm.bootstrap.logging.BootstrapLogger;
import org.wildfly.swarm.bootstrap.modules.MavenResolvers;

/**
 * [hb] TODO: rename to UberJarDependencies
 *
 * @author Heiko Braun
 * @since 18/07/16
 */
public class MavenDependencyResolution implements DependencyResolution {
    private static BootstrapLogger LOGGER = BootstrapLogger.logger("org.wildfly.swarm.bootstrap");

    @Override
    public Set<String> resolve(List<String> exclusions) throws IOException {
        final Set<String> archivePaths = new HashSet<>();

        ApplicationEnvironment env = ApplicationEnvironment.get();

        env.getDependencies()
                .forEach(dep -> {
                    String[] parts = dep.split(":");
                    ArtifactCoordinates coords = null;

                    if (parts.length == 4) {
                        coords = new ArtifactCoordinates(parts[0], parts[1], parts[3]);
                    } else if (parts.length == 5) {
                        coords = new ArtifactCoordinates(parts[0], parts[1], parts[3], parts[4]);
                    }

                    try {
                        final File artifact = MavenResolvers.get().resolveJarArtifact(coords);
                        if (artifact == null) {
                            LOGGER.error("Unable to resolve artifact: " + coords);
                        } else {
                            archivePaths.add(artifact.getAbsolutePath());
                        }
                    } catch (IOException e) {
                        LOGGER.error("Error resolving artifact " + coords, e);
                    }
                });

        return archivePaths;
    }
}
