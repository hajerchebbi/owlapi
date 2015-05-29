/* This file is part of the OWL API.
 * The contents of this file are subject to the LGPL License, Version 3.0.
 * Copyright 2014, The University of Manchester
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see http://www.gnu.org/licenses/.
 *
 * Alternatively, the contents of this file may be used under the terms of the Apache License, Version 2.0 in which case, the provisions of the Apache License Version 2.0 are applicable instead of those above.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License. */
package org.semanticweb.owlapi.rdf.rdfxml.renderer;

import static org.semanticweb.owlapi.util.OWLAPIPreconditions.*;

import java.io.PrintWriter;
import java.util.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.semanticweb.owlapi.io.XMLUtils;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLRuntimeException;
import org.semanticweb.owlapi.util.StringLengthComparator;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

/**
 * Developed as part of the CO-ODE project http://www.co-ode.org .
 * 
 * @author Matthew Horridge, The University Of Manchester, Medical Informatics
 *         Group
 * @since 2.0.0
 */
public class XMLWriterImpl implements XMLWriter {

    private final @Nonnull Stack<XMLElement> elementStack;
    protected final @Nonnull PrintWriter writer;
    private String encoding = "";
    private final @Nonnull String xmlBase;
    private final @Nonnull XMLWriterNamespaceManager xmlWriterNamespaceManager;
    private Map<String, String> entities;
    private static final int TEXT_CONTENT_WRAP_LIMIT = Integer.MAX_VALUE;
    private boolean preambleWritten;
    private static final String PERCENT_ENTITY = "&#37;";
    protected final XMLWriterPreferences xmlPreferences;

    /**
     * @param writer
     *        writer
     * @param xmlWriterNamespaceManager
     *        xmlWriterNamespaceManager
     * @param xmlBase
     *        xmlBase
     * @param preferences
     *        xml writer preferences instance
     */
    public XMLWriterImpl(PrintWriter writer, XMLWriterNamespaceManager xmlWriterNamespaceManager, String xmlBase,
            XMLWriterPreferences preferences) {
        this.writer = checkNotNull(writer, "writer cannot be null");
        this.xmlWriterNamespaceManager = checkNotNull(xmlWriterNamespaceManager,
                "xmlWriterNamespaceManager cannot be null");
        this.xmlBase = checkNotNull(xmlBase, "xmlBase cannot be null");
        xmlPreferences = checkNotNull(preferences, "preferences cannot be null");
        elementStack = new Stack<>();
        setupEntities();
    }

    private void setupEntities() {
        List<String> namespaces = Lists.newArrayList(xmlWriterNamespaceManager.getNamespaces());
        Collections.sort(namespaces, new StringLengthComparator());
        entities = new LinkedHashMap<>();
        for (String curNamespace : namespaces) {
            String curPrefix = "";
            if (xmlWriterNamespaceManager.getDefaultNamespace().equals(curNamespace)) {
                curPrefix = xmlWriterNamespaceManager.getDefaultPrefix();
            } else {
                curPrefix = xmlWriterNamespaceManager.getPrefixForNamespace(curNamespace);
            }
            if (!curPrefix.isEmpty()) {
                entities.put(curNamespace, '&' + curPrefix + ';');
            }
        }
    }

    protected String swapForEntity(String value) {
        for (String curEntity : entities.keySet()) {
            String entityVal = entities.get(curEntity);
            if (value.length() > curEntity.length()) {
                String repVal = value.replace(curEntity, entityVal);
                if (repVal.length() < value.length()) {
                    return repVal;
                }
            }
        }
        return value;
    }

    /**
     * @return default namespace
     */
    public String getDefaultNamespace() {
        return xmlWriterNamespaceManager.getDefaultNamespace();
    }

    @Override
    public String getXMLBase() {
        return xmlBase;
    }

    @Override
    public XMLWriterNamespaceManager getNamespacePrefixes() {
        return xmlWriterNamespaceManager;
    }

    @Override
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    public void setWrapAttributes(boolean b) {
        if (!elementStack.isEmpty()) {
            XMLElement element = elementStack.peek();
            element.setWrapAttributes(b);
        }
    }

    @Override
    public void writeStartElement(IRI name) {
        String qName = xmlWriterNamespaceManager.getQName(name);
        if (!XMLUtils.isQName(qName)) {
            // Could not generate a valid QName, therefore, we cannot
            // write valid XML - just throw an exception!
            throw new IllegalElementNameException(name.toString());
        }
        XMLElement element = new XMLElement(qName, elementStack.size());
        if (!elementStack.isEmpty()) {
            XMLElement topElement = elementStack.peek();
            if (topElement != null) {
                topElement.writeElementStart(false);
            }
        }
        elementStack.push(element);
    }

    @Override
    public void writeEndElement() {
        // Pop the element off the stack and write it out
        if (!elementStack.isEmpty()) {
            XMLElement element = elementStack.pop();
            element.writeElementEnd();
        }
    }

    @Override
    public void writeAttribute(String attr, String val) {
        XMLElement element = elementStack.peek();
        String qName = xmlWriterNamespaceManager.getQName(attr);
        if (qName != null) {
            element.setAttribute(qName, val);
        }
    }

    @Override
    public void writeAttribute(IRI attr, String val) {
        XMLElement element = elementStack.peek();
        String qName = xmlWriterNamespaceManager.getQName(attr);
        if (qName != null) {
            element.setAttribute(qName, val);
        }
    }

    @Override
    public void writeTextContent(String text) {
        XMLElement element = elementStack.peek();
        element.setText(text);
    }

    @Override
    public void writeComment(String commentText) {
        XMLElement element = new XMLElement(null, elementStack.size());
        element.setText("<!-- " + commentText.replace("--", "&#45;&#45;") + " -->");
        if (!elementStack.isEmpty()) {
            XMLElement topElement = elementStack.peek();
            if (topElement != null) {
                topElement.writeElementStart(false);
            }
        }
        if (preambleWritten) {
            element.writeElementStart(true);
        } else {
            elementStack.push(element);
        }
    }

    private void writeEntities(IRI rootName) {
        String qName = xmlWriterNamespaceManager.getQName(rootName);
        if (!XMLUtils.isQName(qName)) {
            throw new OWLRuntimeException("Cannot create valid XML: qname for " + rootName + " is null");
        }
        writer.write("\n\n<!DOCTYPE " + qName + " [\n");
        for (String entityVal : entities.keySet()) {
            String entity = entities.get(entityVal);
            entity = entity.substring(1, entity.length() - 1);
            writer.write("    <!ENTITY ");
            writer.write(entity);
            writer.write(" \"");
            entityVal = XMLUtils.escapeXML(entityVal);
            entityVal = entityVal.replace("%", PERCENT_ENTITY);
            writer.write(entityVal);
            writer.write("\" >\n");
        }
        writer.write("]>\n\n\n");
    }

    @Override
    public void startDocument(IRI rootElement) {
        String encodingString = "";
        if (!encoding.isEmpty()) {
            encodingString = " encoding=\"" + encoding + '"';
        }
        writer.write("<?xml version=\"1.0\"" + encodingString + "?>\n");
        if (xmlPreferences.isUseNamespaceEntities()) {
            writeEntities(rootElement);
        }
        preambleWritten = true;
        while (!elementStack.isEmpty()) {
            elementStack.pop().writeElementStart(true);
        }
        writeStartElement(rootElement);
        setWrapAttributes(true);
        writeAttribute("xmlns", xmlWriterNamespaceManager.getDefaultNamespace());
        if (!xmlBase.isEmpty()) {
            writeAttribute("xml:base", xmlBase);
        }
        for (String curPrefix : xmlWriterNamespaceManager.getPrefixes()) {
            if (!curPrefix.isEmpty()) {
                writeAttribute("xmlns:" + curPrefix,
                        verifyNotNull(xmlWriterNamespaceManager.getNamespaceForPrefix(curPrefix)));
            }
        }
    }

    @Override
    public void endDocument() {
        // Pop of each element
        while (!elementStack.isEmpty()) {
            writeEndElement();
        }
        writer.flush();
    }

    /** XML element. */
    public class XMLElement {

        private final String name;
        private final Map<String, String> attributes;
        protected @Nullable String textContent;
        private boolean startWritten;
        private int indentation;
        private boolean wrapAttributes;

        /**
         * @param name
         *        name
         * @param indentation
         *        indentation
         */
        public XMLElement(String name, int indentation) {
            this.name = name;
            attributes = new LinkedHashMap<>();
            this.indentation = indentation;
            textContent = null;
            startWritten = false;
        }

        /**
         * @param b
         *        b
         */
        public void setWrapAttributes(boolean b) {
            wrapAttributes = b;
        }

        /**
         * @param attribute
         *        attribute
         * @param value
         *        value
         */
        public void setAttribute(String attribute, String value) {
            attributes.put(attribute, value);
        }

        /**
         * @param content
         *        content
         */
        public void setText(String content) {
            textContent = content;
        }

        /**
         * @param close
         *        close
         */
        public void writeElementStart(boolean close) {
            if (!startWritten) {
                startWritten = true;
                insertIndentation();
                if (name != null) {
                    writer.write('<');
                    writer.write(name);
                    writeAttributes();
                    if (textContent != null) {
                        boolean wrap = textContent.length() > TEXT_CONTENT_WRAP_LIMIT;
                        if (wrap) {
                            writeNewLine();
                            indentation++;
                            insertIndentation();
                        }
                        writer.write('>');
                        writeTextContent();
                        if (wrap) {
                            indentation--;
                        }
                    }
                    if (close) {
                        if (textContent != null) {
                            writeElementEnd();
                        } else {
                            writer.write("/>");
                            writeNewLine();
                        }
                    } else {
                        if (textContent == null) {
                            writer.write('>');
                            writeNewLine();
                        }
                    }
                } else {
                    // Name is null so by convension this is a comment
                    if (textContent != null) {
                        writer.write("\n\n\n");
                        for (String token : Splitter.on('\n').split(textContent)) {
                            if (!token.equals("\n")) {
                                insertIndentation();
                            }
                            writer.write(token);
                            writer.write("\n");
                        }
                        writer.write("\n\n");
                    }
                }
            }
        }

        /**
         * Write end element.
         */
        public void writeElementEnd() {
            if (name != null) {
                if (!startWritten) {
                    writeElementStart(true);
                } else {
                    if (textContent == null) {
                        insertIndentation();
                    }
                    writer.write("</");
                    writer.write(name);
                    writer.write(">");
                    writeNewLine();
                }
            }
        }

        private void writeAttribute(String attr, String val) {
            writer.write(attr);
            writer.write('=');
            writer.write('"');
            if (xmlPreferences.isUseNamespaceEntities()) {
                writer.write(swapForEntity(XMLUtils.escapeXML(val)));
            } else {
                writer.write(XMLUtils.escapeXML(val));
            }
            writer.write('"');
        }

        private void writeAttributes() {
            for (Iterator<String> it = attributes.keySet().iterator(); it.hasNext();) {
                String attr = it.next();
                String val = attributes.get(attr);
                writer.write(' ');
                writeAttribute(attr, val);
                if (it.hasNext() && wrapAttributes) {
                    writer.write("\n");
                    indentation++;
                    insertIndentation();
                    indentation--;
                }
            }
        }

        private void writeTextContent() {
            if (textContent != null) {
                // only escape the data if this is not an XML literal
                if ("http://www.w3.org/1999/02/22-rdf-syntax-ns#XMLLiteral".equals(attributes.get("rdf:datatype"))) {
                    writer.write(textContent);
                } else {
                    writer.write(XMLUtils.escapeXML(textContent));
                }
            }
        }

        private void insertIndentation() {
            if (xmlPreferences.isIndenting()) {
                for (int i = 0; i < indentation * xmlPreferences.getIndentSize(); i++) {
                    writer.write(' ');
                }
            }
        }

        private void writeNewLine() {
            writer.write('\n');
        }
    }
}
