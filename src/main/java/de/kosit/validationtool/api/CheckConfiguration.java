/*
 * Licensed to the Koordinierungsstelle für IT-Standards (KoSIT) under
 * one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  KoSIT licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package de.kosit.validationtool.api;

import java.net.URI;
import java.nio.file.Path;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import de.kosit.validationtool.impl.FilesystemHelper;

/**
 * Zentrale Konfigration einer Prüf-Instanz.
 * 
 * @author Andreas Penski
 */
@Getter
@Setter
@Slf4j
@RequiredArgsConstructor
public class CheckConfiguration {

    /**
     * URL, die auf die scenerio.xml Datei zeigt.
     */
    private final URI scenarioDefinition;

    /**
     * Root-Ordner mit den von den einzelnen Szenarien benötigten Dateien
     */
    private URI scenarioRepository;

    /**
     * Die Quelle des Repository - wichtig für in JARs gebundelte Repositories
     */
    @Setter(AccessLevel.PRIVATE)
    private String repositorySource;

    /**
     * Liefert die Szenario-Konfiguration als {@link Path }-Objekt
     * 
     * @return Szenario-Konfiguration
     */
    public Path getScenarioDefinitionPath() {
        return FilesystemHelper.createPath(getScenarioDefinition());
    }

    /**
     * Liefert das Szenario-Repository als {@link Path }-Objekt
     *
     * @return Szenario-Repository
     */
    public Path getScenarioRepositoryPath() {
        return FilesystemHelper.createPath(getScenarioRepository());
    }

    /**
     * Liefert das Szenario-Repository-Quelle als {@link Path }-Objekt. Im Falle von Classpath-Repositories ist es notwenidg
     * zu wissen, aus welchem Jar die Resourcen geladen werden.
     *
     * @return Szenario-Repository-Quelle
     */
    public String getRepositorySource() {
        if (FilesystemHelper.isJarResource(this.scenarioDefinition)) {
            return FilesystemHelper.getJar(getScenarioRepository());
        }
        return null;
    }

    /**
     * Liefert das Repository mit den Artefakten der einzelnen Szenarien.
     *
     * @return uri die durch entsprechende resolver aufgelöst werden kann
     */
    public URI getScenarioRepository() {
        if (this.scenarioRepository == null) {
            this.scenarioRepository = createDefaultRepository();

        }
        return this.scenarioRepository;
    }

    private URI createDefaultRepository() {
        log.info("Creating default scenario repository (alongside scenario definition)");
        return this.scenarioDefinition.resolve("../");
    }

}
