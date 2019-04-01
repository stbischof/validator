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

package de.kosit.validationtool.impl.tasks;

import java.util.List;
import java.util.stream.Collectors;

import org.w3c.dom.Document;

import lombok.RequiredArgsConstructor;

import de.kosit.validationtool.impl.CollectingErrorEventHandler;
import de.kosit.validationtool.impl.ContentRepository;
import de.kosit.validationtool.impl.ObjectFactory;
import de.kosit.validationtool.impl.RelativeUriResolver;
import de.kosit.validationtool.impl.model.BaseScenario;
import de.kosit.validationtool.model.reportInput.CreateReportInput;
import de.kosit.validationtool.model.reportInput.ValidationResultsSchematron;
import de.kosit.validationtool.model.scenarios.ScenarioType;

import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltTransformer;

/**
 * Ausführung von konfigurierten Schematron Validierungen eines Szenarios.
 * 
 * @author Andreas Penski
 */
@RequiredArgsConstructor
public class SchematronValidationAction implements CheckAction {

    private final ContentRepository repository;

    private List<ValidationResultsSchematron> validate(final XdmNode document, final ScenarioType scenario) {
        return scenario.getSchematronValidations().stream().map(v -> validate(document, v)).collect(Collectors.toList());
    }

    private ValidationResultsSchematron validate(final XdmNode document, final BaseScenario.Transformation validation) {
        try {
            final XsltTransformer transformer = validation.getExecutable().load();
            // resolving nur relative zum Repository
            final RelativeUriResolver resolver = new RelativeUriResolver(this.repository);
            transformer.setURIResolver(resolver);
            final CollectingErrorEventHandler e = new CollectingErrorEventHandler();
            transformer.setMessageListener(e);

            final Document result = ObjectFactory.createDocumentBuilder(false).newDocument();
            transformer.setDestination(new DOMDestination(result));
            transformer.setInitialContextNode(document);
            transformer.transform();
            final ValidationResultsSchematron s = new ValidationResultsSchematron();
            s.setResource(validation.getResourceType());
            final ValidationResultsSchematron.Results r = new ValidationResultsSchematron.Results();
            r.setAny(result.getDocumentElement());
            s.setResults(r);
            return s;

        } catch (final SaxonApiException e) {
            throw new IllegalStateException("Can not run schematron validation", e);
        }
    }

    @Override
    public void check(final Bag results) {
        final CreateReportInput report = results.getReportInput();
        final List<ValidationResultsSchematron> validationResult = validate(results.getParserResult().getObject(),
                results.getScenarioSelectionResult().getObject());
        report.getValidationResultsSchematron().addAll(validationResult);
    }

    @Override
    public boolean isSkipped(final Bag results) {
        return results.getSchemaValidationResult() == null || results.getSchemaValidationResult().isInvalid();
    }
}
