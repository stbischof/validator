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

package de.kosit.validationtool.impl;

import static org.apache.commons.lang3.StringUtils.startsWith;

import java.net.URI;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import de.kosit.validationtool.api.CheckConfiguration;
import de.kosit.validationtool.api.InputFactory;
import de.kosit.validationtool.impl.model.Result;
import de.kosit.validationtool.impl.tasks.DocumentParseAction;
import de.kosit.validationtool.model.reportInput.XMLSyntaxError;
import de.kosit.validationtool.model.scenarios.ScenarioType;
import de.kosit.validationtool.model.scenarios.Scenarios;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Repository for die aktiven Szenario einer Prüfinstanz.
 * 
 * @author Andreas Penski
 */
@Slf4j
@RequiredArgsConstructor
public class ScenarioRepository {

    private static final String SUPPORTED_MAJOR_VERSION = "1";

    private static final String SUPPORTED_MAJOR_VERSION_SCHEMA = "http://www.xoev.de/de/validator/framework/1/scenarios";

    @Getter(value = AccessLevel.PRIVATE)

    private final Processor processor;

    @Getter(value = AccessLevel.PRIVATE)
    private final ContentRepository repository;

    private XsltExecutable noScenarioReport;

    @Getter
    private Scenarios scenarios;

    private static boolean isSupportedDocument(final XdmNode doc) {
        final XdmNode root = findRoot(doc);
        final String frameworkVersion = root.getAttributeValue(new QName("frameworkVersion"));
        return startsWith(frameworkVersion, SUPPORTED_MAJOR_VERSION)
                && root.getNodeName().getNamespaceURI().equals(SUPPORTED_MAJOR_VERSION_SCHEMA);
    }

    private static XdmNode findRoot(final XdmNode doc) {
        final Iterator<XdmNode> it = doc.children().iterator();
        while (it.hasNext()) {
            final XdmNode node = it.next();
            if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
                return node;
            }
        }
        throw new IllegalArgumentException("Kein root element gefunden");
    }

    private static void checkVersion(final Path scenarioDefinition) {
        final DocumentParseAction p = new DocumentParseAction();
        final Result<XdmNode, XMLSyntaxError> result = p.parseDocument(InputFactory.read(scenarioDefinition));
        if (result.isValid() && !isSupportedDocument(result.getObject())) {
            throw new IllegalStateException(
                    String.format("Specified scenario configuration %s is not supported.%nThis version only supports definitions of '%s'",
                            scenarioDefinition, SUPPORTED_MAJOR_VERSION_SCHEMA));

        }

    }

    public XsltExecutable getNoScenarioReport() {
        if (this.noScenarioReport == null) {
            this.noScenarioReport = this.repository
                    .loadXsltScript(URI.create(this.scenarios.getNoScenarioReport().getResource().getLocation()));
        }
        return this.noScenarioReport;
    }

    /**
     * Initialisiert das Repository mit der angegebenen Konfiguration.
     *
     * @param config die Konfiguration
     */
    public void initialize(final CheckConfiguration config) {
        final ConversionService conversionService = new ConversionService();
        checkVersion(config.getScenarioDefinitionPath());
        log.info("Loading scenarios from {}", config.getScenarioDefinition());
        final CollectingErrorEventHandler handler = new CollectingErrorEventHandler();
        this.scenarios = conversionService.readXml(config.getScenarioDefinitionPath(), Scenarios.class,
                ContentRepository.getScenarioSchema(), handler);
        if (!handler.hasErrors()) {
            log.info("Loaded scenarios for {} by {} from {}. The following scenarios are available:\n\n{}", this.scenarios.getName(),
                    this.scenarios.getAuthor(), this.scenarios.getDate(), summarizeScenarios());
            log.info("Loading scenario content from {}", config.getScenarioRepository());
            getScenarios().getScenario().forEach(s -> s.initialize(this.repository, false));
        } else {
            throw new IllegalStateException(String.format("Can not load scenarios from %s due to %s", config.getScenarioDefinition(),
                    handler.getErrorDescription()));
        }
        // initialize fallback report eager
        getNoScenarioReport();

    }

    private String summarizeScenarios() {
        final StringBuilder b = new StringBuilder();
        this.scenarios.getScenario().forEach(s -> {
            b.append(s.getName());
            b.append("\n");
        });
        return b.toString();
    }

    /**
     * Ermittelt für das angegebene Dokument das passende Szenario.
     * 
     * @param document das Eingabedokument
     * @return ein Ergebnis-Objekt zur weiteren Verarbeitung
     */
    public Result<ScenarioType, String> selectScenario(final XdmNode document) {
        Result<ScenarioType, String> result = new Result<>();
        final List<ScenarioType> collect = this.scenarios.getScenario().stream().filter(s -> match(document, s))
                .collect(Collectors.toList());
        if (collect.size() == 1) {
            result = new Result<>(collect.get(0));
        } else if (collect.isEmpty()) {
            result.getErrors().add("None of the loaded scenarios matches the specified document");
        } else {
            result.getErrors().add("More than on scenario matches the specified document");
        }
        return result;

    }

    private static boolean match(final XdmNode document, final ScenarioType scenario) {
        try {
            final XPathSelector selector = scenario.getSelector();
            selector.setContextItem(document);
            return selector.effectiveBooleanValue();
        } catch (final SaxonApiException e) {
            log.error("Error evaluating xpath expression", e);
        }
        return false;

    }

    void initialize(final Scenarios def) {
        this.scenarios = def;
    }
}
