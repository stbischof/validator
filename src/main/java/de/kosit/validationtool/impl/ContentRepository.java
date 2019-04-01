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

import static de.kosit.validationtool.impl.FilesystemHelper.isJarResource;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;

/**
 * Repository für verschiedene XML Artefakte zur Vearbeitung der Prüfszenarien.
 * 
 * @author Andreas Penski
 */
@RequiredArgsConstructor
@Slf4j
public class ContentRepository {

    @Getter
    private final Processor processor;

    private final Path repository;

    private final String jar;

    private Schema reportInputSchema;

    /**
     * Konstruktor.
     * 
     * @param p Saxon {@link Processor}
     * @param repository der Pfad zum Repository
     */
    public ContentRepository(final Processor p, final Path repository) {
        this(p, repository, null);
    }

    private static Source resolve(final URL resource) {
        try {
            return new StreamSource(resource.openStream(), resource.toURI().getRawPath());
        } catch (final IOException | URISyntaxException e) {
            throw new IllegalStateException("Can not load schema for resource " + resource.getPath(), e);
        }
    }

    private static Schema createSchema(final Source[] schemaSources, final LSResourceResolver resourceResolver) {
        try {
            final SchemaFactory sf = ObjectFactory.createSchemaFactory();
            sf.setResourceResolver(resourceResolver);
            return sf.newSchema(schemaSources);
        } catch (final SAXException e) {
            throw new IllegalArgumentException("Can not load schema from sources " + schemaSources[0].getSystemId(), e);
        }
    }

    private static Schema createSchema(final Source[] schemaSources) {
        return createSchema(schemaSources, null);
    }

    /**
     * Lädt ein XSL von der angegebenen URI
     *
     * @param uri die URI der XSL Definition
     * @return ein XSLT Executable
     */
    public XsltExecutable loadXsltScript(final URI uri) {
        log.info("Loading XSLT script from  {}", uri);
        final XsltCompiler xsltCompiler = getProcessor().newXsltCompiler();
        final CollectingErrorEventHandler listener = new CollectingErrorEventHandler();
        try {
            xsltCompiler.setErrorListener(listener);
            xsltCompiler.setURIResolver(new RelativeUriResolver(this));

            return xsltCompiler.compile(resolve(uri));
        } catch (final SaxonApiException e) {
            listener.getErrors().forEach(event -> event.log(log));
            throw new IllegalStateException("Can not compile xslt executable for uri " + uri, e);
        } finally {
            if (!listener.hasErrors() && listener.hasEvents()) {
                log.warn("Received warnings while loading a xslt script {}", uri);
                listener.getErrors().forEach(e -> e.log(log));
            }
        }
    }

    /**
     * Erzeugt ein Schema-Objekt auf Basis der übergebenen URL.
     *
     * @param url die url
     * @return das erzeugte Schema
     */
    public static Schema createSchema(final URL url) {
        log.info("Load schema from source {}", url.getPath());
        return createSchema(new Source[] { resolve(url) });
    }

    /**
     * Liefert das definiert Schema für die Szenario-Konfiguration
     *
     * @return Scenario-Schema
     */
    public static Schema getScenarioSchema() {
        return createSchema(ContentRepository.class.getResource("/xsd/scenarios.xsd"));
    }

    /**
     * Liefert das definierte Schema für die Validierung des [@link CreateReportInput}
     *
     * @return ReportInput-Schema
     */
    public Schema getReportInputSchema() {
        if (this.reportInputSchema == null) {
            final Source source = resolve(ContentRepository.class.getResource("/xsd/createReportInput.xsd"));
            this.reportInputSchema = createSchema(new Source[] { source }, new ClassPathResourceResolver("/xsd"));
        }
        return this.reportInputSchema;
    }

    /**
     * Erzeugt ein Schema auf Basis der übegebenen URIs
     * 
     * @param uris die uris in String-Repräsentation
     * @return das Schema
     */
    public Schema createSchema(final Collection<String> uris) {
        return createSchema(uris.stream().map(s -> resolve(URI.create(s))).toArray(Source[]::new));
    }

    private Source resolve(final URI source) {
        Path root = this.repository;
        if (!Files.isDirectory(this.repository)) {
            root = this.repository.getParent();
        }
        final Path resolve = root.resolve(source.getPath());
        try {
            return new StreamSource(Files.newInputStream(resolve), getSystemId(resolve));
        } catch (final IOException e) {
            throw new IllegalArgumentException("Fehler beim Auflösen eines Pfades", e);
        }
    }

    /**
     * Erzeugt einen [@link XPathExecutable} auf Basis der angegebenen Informationen.
     * 
     * @param expression der XPATH-Ausdruck
     * @param namespaces optionale Namespace-Mappings
     * @return ein kompiliertes Executable
     */
    public XPathExecutable createXPath(final String expression, final Map<String, String> namespaces) {
        try {
            final XPathCompiler compiler = getProcessor().newXPathCompiler();
            if (namespaces != null) {
                namespaces.entrySet().forEach(n -> compiler.declareNamespace(n.getKey(), n.getValue()));
            }
            return compiler.compile(expression);
        } catch (final SaxonApiException e) {
            throw new IllegalStateException(String.format("Can not compile xpath match expression '%s'",
                    StringUtils.isNotBlank(expression) ? expression : "EMPTY EXPRESSION"), e);
        }
    }

    public static boolean isRepositoryContent(final URI resolved) {
        return true;
    }

    public Path resolve(final String base, final String href) {
        final Path result;
        if (isJarResource(base)) {
            result = resolveJarResource(base, href);
        } else {
            result = Paths.get(base).resolve(href);
        }
        return result;
    }

    private Path resolveJarResource(final String base, final String href) {
        final String[] splits = base.split("!");
        Path basePath = this.repository.resolve(splits[1]);
        final Path ref = basePath.getFileSystem().getPath(href);
        if (!ref.isAbsolute()) {
            basePath = basePath.getParent();
        }
        return basePath.resolve(ref).normalize();
    }

    public String getSystemId(final Path resolved) {
        if (isJarResource(resolved)) {
            return this.jar + resolved.toAbsolutePath().toString();
        }
        return resolved.toAbsolutePath().toString();
    }

}
