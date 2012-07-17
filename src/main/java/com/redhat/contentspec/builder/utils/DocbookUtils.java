package com.redhat.contentspec.builder.utils;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/*
 * A class to add more functionality to the com.redhat.ecs.commonutils.DocBookUtilities class.
 */
public class DocbookUtils extends
		com.redhat.ecs.commonutils.DocBookUtilities {
	
	/** A prefix for error xref ids */
	private static final String ERROR_XREF_ID_PREFIX = "TagErrorXRef";
	
	public static String buildErrorListItem(final String error)
	{
		return "<listitem><para>" + error + "</para></listitem>\n";
	}
	
	public static String buildErrorListItem(final String topicID, final String error)
	{
		return buildErrorListItem(topicID, error, true, null);
	}
	
	public static String buildErrorListItem(final String topicID, final String error, final boolean addXRefId)
	{
		return buildErrorListItem(topicID, error, addXRefId, null);
	}
	
	/*
	 * A changed version of the original buildErrorListItem that removes the server link from the error message.
	 */
	public static String buildErrorListItem(final String topicID, final String error, final boolean addXRefId, final String codeExample)
	{
		String revalue = "<listitem";
		if (addXRefId)
			revalue += " id=\"" + ERROR_XREF_ID_PREFIX + topicID + "\" xreflabel=\"Topic " + topicID + "\"";
		
		revalue += "><para>Topic " + topicID + " " + error; 
		
		if (codeExample != null)
			revalue += "<programlisting>" + XMLUtilities.wrapStringInCDATA(codeExample) + "</programlisting>";
			
		revalue	 += "</para></listitem>\n";
		
		return revalue;	
	}
	
	public static String addXMLBoilerplate(final String xml, final String entityFileName, final String rootElementName)
	{
		return "<?xml version='1.0' encoding='UTF-8' ?>\n" +
		"<!DOCTYPE " + (rootElementName == null ? "chapter" : rootElementName) + " PUBLIC \"-//OASIS//DTD DocBook XML V4.5//EN\" \"http://www.oasis-open.org/docbook/xml/4.5/docbookx.dtd\" [\n" +
		"<!ENTITY % BOOK_ENTITIES SYSTEM \"" + entityFileName + "\">\n" +
		"%BOOK_ENTITIES;\n" +
		"]>\n\n" +
		xml;
	}
	
	/*
	 * Wraps an external link in literal tags. (Copied and reworked from Matt Casperson's buildxRef function)
	 * 
	 * @param xmlDoc The XML Document object where the link will be added.
	 * @param xref A string that's used normally as the xref link.
	 * @return An element node that can contains the data for the link.
	 */
	public static List<Element> buildLiteral(final Document xmlDoc, final String xref)
	{
		final List<Element> retValue = new ArrayList<Element>();
		
		final Element xrefItem = xmlDoc.createElement("literal");
		xrefItem.setTextContent(xref);
		
		retValue.add(xrefItem);
		
		return retValue;
	}
	
	/*
	 * Wraps a an external link in literal tags and adds an emphasis'd prefix as well. (Copied and reworked from Matt Casperson's buildEmphasisPrefixedxRef function)
	 * 
	 * @param xmlDoc The XML Document object where the link will be added.
	 * @param prefix A String to prefix to the link.
	 * @param xref A string that's used normally as the xref link.
	 * @return An element node that can cantains the data for the link.
	 */
	public static List<Element> buildEmphasisPrefixedLiteral(final Document xmlDoc, final String prefix, final String xref)
	{
		final List<Element> retValue = new ArrayList<Element>();
		
		final Element emphasis = xmlDoc.createElement("emphasis");
		emphasis.setTextContent(prefix);
		retValue.add(emphasis);
		
		final Element xrefItem = xmlDoc.createElement("literal");
		xrefItem.setTextContent(xref);
		retValue.add(xrefItem);
		
		return retValue;
	}
}
