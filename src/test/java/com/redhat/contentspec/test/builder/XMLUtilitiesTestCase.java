package com.redhat.contentspec.test.builder;

import static org.junit.Assert.*;

import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.redhat.contentspec.builder.constants.BuilderConstants;
import com.redhat.contentspec.builder.utils.XMLUtilities;

public class XMLUtilitiesTestCase {

	@Test
	public void testConvertStringToDocument()
	{
		final String testXml = "<section>\n\t<title>Test Entity</title>\n\t<screen>\n\t\t# grep -E &apos;svm|vmx&apos; /proc/cpuinfo <literal>test</literal>&apos;s\n\t</screen>\n</section>";
		
		try {
			/* Test with entities to ensure a document is created */
			final Document doc = XMLUtilities.convertStringToDocument(testXml);
			assertNotNull("Failed to create a DOM Document", doc);
			
			/* Check to make sure that the xml came out as it went in */
			final String outputXml = XMLUtilities.convertNodeToString(doc, Arrays.asList(BuilderConstants.VERBATIM_XML_ELEMENTS.split(",")), Arrays.asList(BuilderConstants.INLINE_XML_ELEMENTS.split(",")),
				Arrays.asList(BuilderConstants.CONTENTS_INLINE_XML_ELEMENTS.split(",")), true);
			
			assertEquals("The XML doesn't match so its likely an entity failed to be replaced", testXml, outputXml);
		} catch (SAXException e) {
			fail(e.getMessage());
		}
		
		/* Test to see if malformed XML is found */
		final String testFaultyXml = "<section>\n\t<title>Test Entity\n\t<screen>\n\t\t# grep -E &apos;svm|vmx&apos; /proc/cpuinfo <literal>test</literal>&apos;s\n\t</screen>\n</section>";
		
		try {
			/* Test with entities to ensure a document is created */
			final Document doc = XMLUtilities.convertStringToDocument(testFaultyXml);
			assertNotNull("Failed to create a DOM Document", doc);
		} catch (SAXException e) {
			assertEquals(e.getMessage(), "The element type \"title\" must be terminated by the matching end-tag \"</title>\".");
		}
		
		/* Test with an invalid entity i.e a HTML entity */
		final String testHTMLXml = "<section>\n\t<title>Test Entity</title>\n\t<screen>\n\t\t# grep -E &#39;svm|vmx&#39; /proc/cpuinfo <literal>test</literal>&apos;s\n\t</screen>\n</section>";
		
		try {
			/* Test with entities to ensure a document is created */
			final Document doc = XMLUtilities.convertStringToDocument(testHTMLXml);
			assertNull("Created the DOM Document successfully. HTML entities now work?", doc);
		} catch (SAXException e) {
			fail(e.getMessage());
		}
	}
	
	@Test
	public void testConvertNodeToString() throws ParserConfigurationException
	{	
		/* Build up the XML */
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = builderFactory.newDocumentBuilder();
		Document doc = builder.newDocument();
		
		/* Create the document to be converted to a string */
		Element sectionEle = doc.createElement("section");
		doc.appendChild(sectionEle);
		
		/* Add the title to the section */
		Element sectionTitle = doc.createElement("title");
		sectionTitle.setTextContent("Test Title");
		sectionEle.appendChild(sectionTitle);
		
		/* Add a basic paragraph */
		Element sectionPara = doc.createElement("para");
		sectionPara.setTextContent("This is a test paragraph to test node conversion");
		sectionEle.appendChild(sectionPara);
		
		/* Add a programlisting element that contains a CDATA section */
		Element sectionProgramListing = doc.createElement("programlisting");
		sectionEle.appendChild(sectionProgramListing);
		
		/* Add the CDATA to the program listing */
		CDATASection cdataSection = doc.createCDATASection("This & that.\nWe are testing the nodeToString conversion here.\n");
		sectionProgramListing.appendChild(cdataSection);
		
		/* Test each function */
		checkConvertNodeToString1(doc);
		checkConvertNodeToString2(doc);
		checkConvertNodeToString3(doc);
	}
	
	// TODO Expand on the options that go into this method
	public void checkConvertNodeToString1(final Document doc)
	{
		/* The xml that should be output from the conversion */
		final String testXml = 
				"<section>\n" +
				"\t<title>Test Title</title>\n" +
				"\t<para>\n" +
				"\t\tThis is a test paragraph to test node conversion\n" +
				"\t</para>\n" +
				"\t<programlisting><![CDATA[This & that.\n" +
				"We are testing the nodeToString conversion here.\n" +
				"]]></programlisting>\n" +
				"</section>";
		
		/* Convert the document to a String */
		final String convertedXml = XMLUtilities.convertNodeToString(doc, true, false, false, Arrays.asList(BuilderConstants.VERBATIM_XML_ELEMENTS.split(",")), Arrays.asList(BuilderConstants.INLINE_XML_ELEMENTS.split(",")),
				Arrays.asList(BuilderConstants.CONTENTS_INLINE_XML_ELEMENTS.split(",")), true, 1, 0);
		
		assertEquals(testXml, convertedXml);
	}
	
	public void checkConvertNodeToString2(final Document doc)
	{		
		/* The xml that should be output from the conversion */
		final String testXml = 
				"<section><title>Test Title</title><para>This is a test paragraph to test node conversion</para><programlisting><![CDATA[This & that.\n" +
				"We are testing the nodeToString conversion here.\n" +
				"]]></programlisting></section>";
		
		/* Convert the document to a String */
		final String convertedXml = XMLUtilities.convertNodeToString(doc, true);
		
		assertEquals(testXml, convertedXml);
		
		/* The xml that should be output from the conversion when includeElementNames is set to false */
		final String testXml2 = 
				"<title>Test Title</title><para>This is a test paragraph to test node conversion</para><programlisting><![CDATA[This & that.\n" +
				"We are testing the nodeToString conversion here.\n" +
				"]]></programlisting>";
		
		/* Convert the document to a String */
		final String convertedXml2 = XMLUtilities.convertNodeToString(doc, false);
		
		assertEquals(testXml2, convertedXml2);
	}
	
	// TODO Expand on the options that go into this method
	public void checkConvertNodeToString3(final Document doc)
	{
		/* The xml that should be output from the conversion */
		final String testXml = 
				"<section>\n" +
				"\t<title>Test Title</title>\n" +
				"\t<para>\n" +
				"\t\tThis is a test paragraph to test node conversion\n" +
				"\t</para>\n" +
				"\t<programlisting><![CDATA[This & that.\n" +
				"We are testing the nodeToString conversion here.\n" +
				"]]></programlisting>\n" +
				"</section>";
		
		/* Convert the document to a String */
		final String convertedXml = XMLUtilities.convertNodeToString(doc, Arrays.asList(BuilderConstants.VERBATIM_XML_ELEMENTS.split(",")), Arrays.asList(BuilderConstants.INLINE_XML_ELEMENTS.split(",")),
				Arrays.asList(BuilderConstants.CONTENTS_INLINE_XML_ELEMENTS.split(",")), true);
		
		assertEquals(testXml, convertedXml);
	}
}
