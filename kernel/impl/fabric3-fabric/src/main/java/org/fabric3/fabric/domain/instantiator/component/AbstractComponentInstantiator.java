/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.fabric.domain.instantiator.component;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathVariableResolver;
import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.fabric3.api.model.type.F3NamespaceContext;
import org.fabric3.api.model.type.component.Component;
import org.fabric3.api.model.type.component.ComponentType;
import org.fabric3.api.model.type.component.Property;
import org.fabric3.api.model.type.component.PropertyValue;
import org.fabric3.fabric.domain.instantiator.InstantiationContext;
import org.fabric3.spi.model.instance.LogicalComponent;
import org.fabric3.spi.model.instance.LogicalCompositeComponent;
import org.fabric3.spi.model.instance.LogicalProperty;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Contains functionality common to different component instantiators.
 */
public abstract class AbstractComponentInstantiator {
    private static final DocumentBuilderFactory DOCUMENT_FACTORY;
    private static final XPathFactory XPATH_FACTORY;

    static {
        DOCUMENT_FACTORY = DocumentBuilderFactory.newInstance();
        DOCUMENT_FACTORY.setNamespaceAware(true);
        XPATH_FACTORY = XPathFactory.newInstance();
    }

    /**
     * Set the initial actual property values of a component.
     *
     * @param logicalComponent the component to initialize
     * @param component        the definition of the component
     * @param context          the instantiation context
     */
    protected void initializeProperties(LogicalComponent<?> logicalComponent, Component<?> component, InstantiationContext context) {

        Map<String, PropertyValue> propertyValues = component.getPropertyValues();
        ComponentType componentType = component.getComponentType();
        LogicalCompositeComponent parent = logicalComponent.getParent();

        for (Property property : componentType.getProperties().values()) {
            String name = property.getName();
            PropertyValue propertyValue = propertyValues.get(name);
            if (propertyValue != null && propertyValue.getInstanceValue() != null) {
                // instance value is set
                LogicalProperty logicalProperty = new LogicalProperty(name, propertyValue.getInstanceValue(), logicalComponent);
                logicalComponent.setProperties(logicalProperty);
            } else {
                Document value;
                if (propertyValue == null) {
                    String source = property.getSource();
                    if (source != null) {
                        // get the value by evaluating an XPath against the composite properties
                        try {
                            F3NamespaceContext nsContext = new F3NamespaceContext();
                            for (Map.Entry<String, String> entry : property.getNamespaces().entrySet()) {
                                nsContext.add(entry.getKey(), entry.getValue());
                            }
                            propertyValue = new PropertyValue("name", source);
                            value = deriveValueFromXPath(propertyValue, parent, nsContext);
                        } catch (PropertyTypeException e) {
                            InvalidProperty error = new InvalidProperty(name, logicalComponent, e);
                            context.addError(error);
                            return;
                        }
                    } else {
                        // use default value from component type
                        value = property.getDefaultValue();
                    }
                } else {
                    // the spec defines the following sequence
                    if (propertyValue.getFile() != null) {
                        // load the value from an external resource
                        value = loadValueFromFile(property.getName(), propertyValue.getFile(), logicalComponent, context);
                    } else if (propertyValue.getSource() != null) {
                        // get the value by evaluating an XPath against the composite properties
                        try {
                            NamespaceContext nsContext = propertyValue.getNamespaceContext();
                            value = deriveValueFromXPath(propertyValue, parent, nsContext);
                        } catch (PropertyTypeException e) {
                            InvalidProperty error = new InvalidProperty(name, logicalComponent, e);
                            context.addError(error);
                            return;
                        }
                    } else {
                        // use inline XML file
                        value = propertyValue.getValue();
                    }

                }
                if (property.isRequired() && value == null && (propertyValue == null || (propertyValue != null && propertyValue.getInstanceValue() == null))) {
                    // The XPath expression returned an empty value. Since the property is required, throw an exception
                    PropertySourceNotFound error = new PropertySourceNotFound(name, logicalComponent);
                    context.addError(error);
                } else if (!property.isRequired() && value == null) {
                    // The XPath expression returned an empty value. Since the property is optional, ignore it
                    continue;
                } else {
                    // set the property value
                    boolean many = property.isMany();
                    LogicalProperty logicalProperty;
                    QName type = property.getType();
                    if (type == null) {
                        logicalProperty = new LogicalProperty(name, value, many, logicalComponent);
                    } else {
                        logicalProperty = new LogicalProperty(name, value, many, type, logicalComponent);
                    }
                    logicalComponent.setProperties(logicalProperty);
                }
            }

        }

    }

    Document deriveValueFromXPath(PropertyValue propertyValue, LogicalComponent<?> parent, NamespaceContext nsContext) throws PropertyTypeException {

        XPathVariableResolver variableResolver = qName -> {
            String name = qName.getLocalPart();
            LogicalProperty property = parent.getProperties(name);
            if (property == null) {
                return null;
            }
            if (propertyValue.getType() != null && property.getType() != null && !propertyValue.getType().equals(property.getType())) {
                throw new PropertyTypeException("Property types are incompatible:" + name + " and " + propertyValue.getName());
            }
            Document value = property.getValue();
            if (value == null || value.getDocumentElement().getChildNodes().getLength() == 0) {
                return null;
            }
            // select the first value
            return value.getDocumentElement();
        };

        XPath xpath = XPATH_FACTORY.newXPath();
        xpath.setXPathVariableResolver(variableResolver);
        xpath.setNamespaceContext(nsContext);

        DocumentBuilder builder;
        try {
            builder = DOCUMENT_FACTORY.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e);
        }

        Document document = builder.newDocument();
        Element root = document.createElement("values");
        document.appendChild(root);
        String source = propertyValue.getSource();
        try {
            source = parseSource(source);
            NodeList result = (NodeList) xpath.evaluate(source, document, XPathConstants.NODESET);

            if (result.getLength() == 0) {
                return null;
            }

            for (int i = 0; i < result.getLength(); i++) {
                Node node = result.item(i);
                Element value;
                if (!"value".equals(node.getNodeName())) {
                    value = document.createElement("value");
                    root.appendChild(value);
                } else {
                    // value is already specified as the root of the XPath select, append directly to it
                    value = root;
                }
                // clone the node and copy the namespaces as the original may be accessed multiple times
                Node cloned = node.cloneNode(true);
                NamespaceHelper.copyNamespaces(node, value);
                document.adoptNode(cloned);
                short type = cloned.getNodeType();
                if (Node.ELEMENT_NODE == type || Node.TEXT_NODE == type) {
                    value.appendChild(cloned);
                } else if (Node.ATTRIBUTE_NODE == type) {
                    // convert the attribute to an element in the property DOM
                    Element element = document.createElement(cloned.getNodeName());
                    element.setTextContent(cloned.getNodeValue());
                    value.appendChild(element);
                } else {
                    throw new XPathExpressionException("Unsupported node type: " + type);
                }
            }
        } catch (XPathExpressionException e) {
            if (e.getCause() instanceof TransformerException) {
                String message = e.getCause().getMessage();
                if (message.contains("resolveVariable") || message.contains("null")) {
                    return null;
                }
            }
            throw new PropertyTypeException(e);
        }
        return document;

    }

    public Document loadValueFromFile(String name, URI fileUri, LogicalComponent<?> parent, InstantiationContext context) {
        try {
            DocumentBuilder builder = DOCUMENT_FACTORY.newDocumentBuilder();
            Document document = builder.parse(fileUri.toString());
            Element root = document.getDocumentElement();
            // support documents in various formats: with a root <values>, <value>, or no root element
            if (!"values".equals(root.getNodeName())) {
                if ("value".equals(root.getNodeName())) {
                    Element newRoot = document.createElement("values");
                    document.removeChild(root);
                    document.appendChild(newRoot);
                    newRoot.appendChild(root);
                } else {
                    Element newRoot = document.createElement("values");
                    document.removeChild(root);
                    document.appendChild(newRoot);
                    Element value = document.createElement("value");
                    newRoot.appendChild(value);
                    value.appendChild(root);
                }
            }
            return document;
        } catch (IOException e) {
            InvalidPropertyFile error = new InvalidPropertyFile(name, parent, e, fileUri);
            context.addError(error);
            return null;
        } catch (SAXException e) {
            InvalidPropertyFile error = new InvalidPropertyFile(name, parent, e, fileUri);
            context.addError(error);
            return null;
        } catch (ParserConfigurationException e) {
            throw new AssertionError(e);
        }
    }

    private String parseSource(String source) {
        if (source.startsWith("$")) {
            // ASM_5039 complex type with multiple values: ensure all values are selected
            int index = source.indexOf("/");
            if (index > 0 && index < source.length() - 2 && !source.substring(index + 1, index + 2).equals("/")) {
                source = source.substring(0, index) + "/" + source.substring(index);
            }
        }
        return source;
    }

}
