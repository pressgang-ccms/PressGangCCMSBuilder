package com.redhat.contentspec.builder.utils;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import com.google.code.regexp.NamedMatcher;
import com.google.code.regexp.NamedPattern;
import org.jboss.pressgang.ccms.utils.common.XMLUtilities;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * An XML Validator Utility to validate XML using SAX. SAX is significantly faster then DOM so if you aren't required to check elements in
 * the xml then this should be a lot faster then a DOM Validator.
 *
 * @author lnewson
 */
public class SAXXMLValidator implements ErrorHandler, EntityResolver {
    protected boolean errorsDetected;
    private String errorText;
    private String dtdFileName;
    private byte[] dtdData;
    final private boolean showErrors;

    public SAXXMLValidator(final boolean showErrors) {
        this.showErrors = showErrors;
    }

    public SAXXMLValidator() {
        this.showErrors = false;
    }

    public boolean validateXML(final Document doc, final String dtdFileName, final byte[] dtdData) {
        return doc == null || doc.getDocumentElement() == null ? false : validateXML(XMLUtilities.convertDocumentToString(doc), dtdFileName,
                dtdData, doc.getDocumentElement().getNodeName());
    }

    /**
     * Validates some piece of XML to ensure that it is valid.
     *
     * @param xml         The XML to be validated.
     * @param dtdFileName The filename of the DTD data.
     * @param dtdData     The DTD data to be used to validate against.
     * @param rootEleName The name of the root XML Element.
     * @return
     */
    public boolean validateXML(final String xml, final String dtdFileName, final byte[] dtdData, String rootEleName) {
        if (xml == null || dtdFileName == null || dtdData == null || rootEleName == null) return false;

        this.dtdData = dtdData;
        this.dtdFileName = dtdFileName;

        String encoding = XMLUtilities.findEncoding(xml);
        if (encoding == null) encoding = "UTF-8";

        final SAXParserFactory factory = SAXParserFactory.newInstance();
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setValidating(true);
            factory.setNamespaceAware(false);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();
            reader.setEntityResolver(this);
            reader.setErrorHandler(this);
            reader.parse(new InputSource(new ByteArrayInputStream(setXmlDtd(xml, dtdFileName, rootEleName).getBytes(encoding))));
        } catch (SAXParseException e) {
            handleError(e);
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * A function that will resolve the dtd file location to the dtd byte[] data specified.
     */
    @Override
    public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
        final InputSource dtdsource = new InputSource();
        if (systemId.endsWith(this.dtdFileName)) {
            dtdsource.setByteStream(new ByteArrayInputStream(this.dtdData));
            return dtdsource;
        }

        return null;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(String errorText) {
        this.errorText = errorText;
    }

    @Override
    public void error(SAXParseException ex) throws SAXParseException {
        throw ex;
    }

    @Override
    public void fatalError(SAXParseException ex) throws SAXParseException {
        throw ex;
    }

    @Override
    public void warning(SAXParseException ex) throws SAXParseException {
        // Do nothing if its a warning
    }

    public boolean handleError(final SAXParseException error) {
        errorsDetected = true;
        errorText = error.getMessage();
        if (showErrors) System.out.println("XMLValidator.handleError() " + errorText);
        return true;
    }

    /**
     * Sets the DTD for an xml file. If there are any entities then they are removed. This function will aslo add the preamble to the XML
     * if it doesn't exist.
     *
     * @param xml            The XML to add the DTD for.
     * @param dtdFileName    The file/url name of the DTD.
     * @param dtdRootEleName The name of the root element in the XML that is inserted into the <!DOCTYPE > node.
     * @return The xml with the dtd added.
     */
    private String setXmlDtd(final String xml, final String dtdFileName, final String dtdRootEleName) {
        String output = null;

		/* Check if the XML already has a DOCTYPE. If it does then replace the values and remove entities for processing */
        final NamedPattern pattern = NamedPattern.compile(
                "<\\!DOCTYPE[ ]+(?<Name>.*?)[ ]+((PUBLIC[ ]+\".*?\"|SYSTEM)[ ]+\"(?<SystemId>.*?)\")[ ]*((?<Entities>\\[(.|\n)*\\][ ]*))"
                        + "?>");
        final NamedMatcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String name = matcher.group("Name");
            String systemId = matcher.group("SystemId");
            String entities = matcher.group("Entities");
            String doctype = matcher.group();
            String newDoctype = doctype.replace(name, dtdRootEleName).replace(systemId, dtdFileName);
            if (entities != null) {
                newDoctype = newDoctype.replace(entities, "");
            }
            output = xml.replace(doctype, newDoctype);
            return output;
        }

		/* The XML doesn't have any doctype so add it */
        final String preamble = XMLUtilities.findPreamble(xml);
        if (preamble != null) {
            output = xml.replace(preamble, preamble + "\n" + "<!DOCTYPE " + dtdRootEleName + " SYSTEM \"" + dtdFileName + "\">");
        } else {
            output = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                    "<!DOCTYPE " + dtdRootEleName + " SYSTEM \"" + dtdFileName + "\">\n" + xml;
        }

        return output;
    }
}
