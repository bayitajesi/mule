/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.config.spring;

import static org.mule.config.spring.parsers.XmlMetadataAnnotations.METADATA_ANNOTATIONS_KEY;

import org.mule.config.spring.parsers.XmlMetadataAnnotations;
import org.mule.util.SystemUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.xml.DefaultDocumentLoader;
import org.springframework.beans.factory.xml.DocumentLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.Attributes;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Alternative to Spring's default document loader that uses <b>SAX</b> to add metadata to the <b>DOM</b> elements that
 * are the result of the default parser.
 * 
 * @since 3.8.0
 */
final class MuleDocumentLoader implements DocumentLoader
{

    private static final UserDataHandler NULL_DATA_HANDLER = new UserDataHandler()
    {
        @Override
        public void handle(short operation, String key, Object data, Node src, Node dst)
        {
            // Nothing to do.
        }
    };

    private DocumentLoader defaultLoader = new DefaultDocumentLoader();

    /**
     * Load the {@link Document} at the supplied {@link InputSource} using the standard JAXP-configured XML parser.
     */
    @Override
    public Document loadDocument(InputSource inputSource, EntityResolver entityResolver,
                                 ErrorHandler errorHandler, int validationMode, boolean namespaceAware) throws Exception
    {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        IOUtils.copy(inputSource.getByteStream(), output);

        InputSource defaultInputSource = new InputSource(new ByteArrayInputStream(output.toByteArray()));
        InputSource enrichInputSource = new InputSource(new ByteArrayInputStream(output.toByteArray()));

        Document doc = defaultLoader.loadDocument(defaultInputSource, entityResolver, errorHandler, validationMode, namespaceAware);

        createSaxAnnotator(doc).parse(enrichInputSource);

        return doc;
    }

    protected XMLReader createSaxAnnotator(Document doc) throws ParserConfigurationException, SAXException
    {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        SAXParser saxParser = saxParserFactory.newSAXParser();
        XMLReader documentReader = saxParser.getXMLReader();
        documentReader.setContentHandler(new XmlMetadataAnnotator(doc));
        return documentReader;
    }

    /**
     * SAX filter that builds the metadata that will annotate the built nodes.
     */
    private final static class XmlMetadataAnnotator extends DefaultHandler
    {
        private Locator locator;
        private DomWalkerElement walker;
        private Stack<XmlMetadataAnnotations> annotationsStack = new Stack<>();

        private XmlMetadataAnnotator(Document doc)
        {
            this.walker = new DomWalkerElement(doc.getDocumentElement());
        }

        @Override
        public void setDocumentLocator(Locator locator)
        {
            super.setDocumentLocator(locator);
            this.locator = locator;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException
        {
            walker = walker.walkIn();

            XmlMetadataAnnotations metadataBuilder = new XmlMetadataAnnotations(locator.getLineNumber());
            metadataBuilder.appendElementStart(qName, atts);
            annotationsStack.push(metadataBuilder);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException
        {
            annotationsStack.peek().appendElementBody(new String(ch, start, length).trim());
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException
        {
            XmlMetadataAnnotations metadataAnnotations = annotationsStack.pop();
            metadataAnnotations.appendElementEnd(qName);

            if (!annotationsStack.isEmpty())
            {
                annotationsStack.peek().appendElementBody(SystemUtils.LINE_SEPARATOR + metadataAnnotations.getElementString() + SystemUtils.LINE_SEPARATOR);
            }

            walker.getParentNode().setUserData(METADATA_ANNOTATIONS_KEY, metadataAnnotations, NULL_DATA_HANDLER);
            walker = walker.walkOut();
        }
    }

    /**
     * Allows for sequential navigation of a DOM tree.
     */
    private final static class DomWalkerElement
    {
        private final DomWalkerElement parent;
        private final Node node;

        private int childIndex = 0;

        public DomWalkerElement(Node node)
        {
            this.parent = null;
            this.node = node;
        }

        private DomWalkerElement(DomWalkerElement parent, Node node)
        {
            this.parent = parent;
            this.node = node;
        }

        public DomWalkerElement walkIn()
        {
            Node nextChild = node.getChildNodes().item(childIndex++);
            while (nextChild != null && nextChild.getNodeType() != Node.ELEMENT_NODE)
            {
                nextChild = node.getChildNodes().item(childIndex++);
            }
            return new DomWalkerElement(this, nextChild);
        }

        public DomWalkerElement walkOut()
        {
            Node nextSibling = parent.node.getNextSibling();
            while (nextSibling != null && nextSibling.getNodeType() != Node.ELEMENT_NODE)
            {
                nextSibling = nextSibling.getNextSibling();
            }
            return new DomWalkerElement(parent.parent, nextSibling);
        }

        public Node getParentNode()
        {
            return parent.node;
        }
    }
}
