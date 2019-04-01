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
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link LSResourceResolver} der objekte relativ zu einem Basis-Pfad aus dem Classpath der Anwendung laden kann. Dies
 * wird hauptsächlich dafür genutzt die Artefakte des Validations-Tools zu laden. Anwendungsspezifische Artefakte
 * sollten aus dem {@link ContentRepository} geladen werden - nicht direkt aus dem Classpath.
 * 
 * @author Andreas Penski
 */
@Slf4j
class ClassPathResourceResolver implements LSResourceResolver {

    /**
     * Simple {@link LSInput}-Implementierung, die einen Stream liefern kann
     */
    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class LSInputImpl implements LSInput {

        private Reader characterStream;

        private InputStream byteStream;

        private String systemId;

        private String publicId;

        private String baseURI;

        private String encoding;

        private boolean certifiedText;

        private String stringData;

        /**
         * Instantiierung einer neue Instanz.
         *
         * @param publicId die publicId
         * @param systemId die systemId
         * @param baseURI die baseURI
         */
        public LSInputImpl(final String publicId, final String systemId, final String baseURI) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;
        }

        @Override
        public boolean getCertifiedText() {
            return this.certifiedText;
        }
    }

    private final URI base;

    /**
     * Instantiiert einen neuen resolver mit angegebenen Basispfad
     *
     * @param basePath der Basispfad
     */
    public ClassPathResourceResolver(final String basePath) {
        if (!StringUtils.startsWith(basePath, "/")) {
            throw new IllegalArgumentException("Base path must start with a slash");
        }
        this.base = URI.create(basePath + (basePath.endsWith("/") == basePath.length() > 1 ? "" : "/"));
    }

    @Override
    public LSInput resolveResource(final String type, final String namespaceURI, final String publicId, final String systemId,
            final String baseURI) {
        final URL resource = ClassPathResourceResolver.class.getResource(resolve(systemId, baseURI));
        if (resource != null) {
            try {
                final InputStream in = resource.openStream();
                final LSInputImpl input = new LSInputImpl(publicId, systemId, baseURI);
                input.setByteStream(in);
                return input;

            } catch (final IOException e) {
                log.error("Error loading schema resource from {}", resource, e);
            }
        }
        // not found
        return null;
    }

    private String resolve(final String systemId, final String baseUri) {
        String id = systemId;
        if (isJarResource(baseUri)) {
            id = URI.create(FilesystemHelper.getResoucePath(baseUri)).resolve(systemId).toString().substring(1);
        }
        return this.base.resolve(id).toASCIIString();

    }
}
