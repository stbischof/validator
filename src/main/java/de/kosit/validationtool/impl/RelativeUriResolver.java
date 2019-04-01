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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import lombok.RequiredArgsConstructor;

import net.sf.saxon.Configuration;
import net.sf.saxon.lib.UnparsedTextURIResolver;
import net.sf.saxon.trans.XPathException;

/**
 * {@link URIResolver} that resolves artifacts relative to a given base uri. The resolved URI must be resolving as child
 * e.g. the baseUri must be a parent of the resolved artifact.
 *
 * @author Andreas Penski
 */
@RequiredArgsConstructor
public class RelativeUriResolver implements URIResolver, UnparsedTextURIResolver {

    /** the base uri */
    private final ContentRepository repository;

    @Override
    public Source resolve(final String href, final String base) throws TransformerException {
        final Path resolved = this.repository.resolve(base, href);
        if (isUnderBaseUri(resolved.toUri())) {
            try {

                return new StreamSource(Files.newInputStream(resolved), this.repository.getSystemId(resolved));
            } catch (final IOException e) {

                throw new IllegalStateException(String.format("Can not resolve required  %s", href), e);
            }
        } else {
            throw new IllegalStateException(
                    String.format("The resolved transformation artifact %s is not within the configured repository %s", resolved,
                                  this.repository));
        }
    }

    private static boolean isUnderBaseUri(final URI resolved) {
        return ContentRepository.isRepositoryContent(resolved);
    }

    @Override
    public Reader resolve(final URI absoluteURI, final String encoding, final Configuration config) throws XPathException {
        if (isUnderBaseUri(absoluteURI)) {
            try {
                return new InputStreamReader(absoluteURI.toURL().openStream(), encoding);
            } catch (final IOException e) {
                throw new IllegalStateException(String.format("Can not resolve required  %s", absoluteURI), e);
            }
        } else {
            throw new IllegalStateException(
                    String.format("The resolved transformation artifact %s is not within the configured repository %s", absoluteURI,
                                  this.repository));
        }
    }
}