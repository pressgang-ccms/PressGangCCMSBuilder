package com.redhat.contentspec.test.builder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import com.redhat.contentspec.builder.utils.SAXXMLValidator;
import org.junit.BeforeClass;
import org.junit.Test;

public class ValidatorTestCase {
    private final static SAXXMLValidator validator = new SAXXMLValidator();
    private static byte[] dtdFile = null;

    @BeforeClass
    public static void setUp() {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final InputStream is = ClassLoader.getSystemResourceAsStream("rocbookx.dtd");

        assertNotNull("Unable to load the rocbookx DTD file", is);

        byte[] buffer = new byte[1000];
        int length = 0;
        try {
            while ((length = is.read(buffer, 0, 1000)) != -1) {
                bos.write(buffer, 0, length);
            }

            bos.flush();
        } catch (IOException e) {
            fail(e.getMessage());
        }

        dtdFile = bos.toByteArray();
    }

    @Test
    public void testValidData() {
        // Check validation without xml preamble
        final String testXml = "<section>\n" +
                "\t<title>This is a test case</title>\n" +
                "\t<para>Test paragraph</para>\n" +
                "</section>";

        boolean result = validator.validateXML(testXml, "rocbookx.dtd", dtdFile, "section");

        assertTrue(validator.getErrorText(), result);

        // Check validation with rocbook preamble
        final String testPreambleXml = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                "<!DOCTYPE section SYSTEM \"rocbookx.dtd\">\n" +
                testXml;

        result = validator.validateXML(testPreambleXml, "rocbookx.dtd", dtdFile, "section");

        assertTrue(validator.getErrorText(), result);

        // Check validation with normal full preamble
        final String testFullPreambleXml = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                "<!DOCTYPE section PUBLIC \"-//OASIS//DTD DocBook XML V4.5//EN\" \"http://www.oasis-open.org/docbook/xml/4.5/docbookx" +
                ".dtd\">\n" +
                testXml;

        result = validator.validateXML(testFullPreambleXml, "rocbookx.dtd", dtdFile, "section");

        assertTrue(validator.getErrorText(), result);
    }

    @Test
    public void testInvalidData() {
        /* Check validation without a root node */
        String testXml = "\t<title>This is a test case</title>\n" + "\t<para>Test paragraph</para>\n";

        boolean result = validator.validateXML(testXml, "rocbookx.dtd", dtdFile, "section");

        assertFalse(result);
		
		/* Check validation with an incorrect root node */
        testXml = "<chapter>\n" +
                "\t<title>This is a test case</title>\n" +
                "\t<para>Test paragraph</para>\n" +
                "</chapter>";

        result = validator.validateXML(testXml, "rocbookx.dtd", dtdFile, "section");

        assertFalse(result);
		
		/* Check validation with the wrong dtd data */
        testXml = "<section>\n" +
                "\t<title>This is a test case</title>\n" +
                "\t<para>Test paragraph</para>\n" +
                "\t<formalpara></formalpara>\n" +
                "</section>";

        result = validator.validateXML(testXml, "rocbookx.dtd", dtdFile, "section");

        assertFalse(result);
		
		/* Check validation with malformed XML*/
        testXml = "<section>\n" +
                "\t<title>This is a test case</tittle>\n" +
                "\t<para>Test paragraph</para>\n" +
                "</section>";

        result = validator.validateXML(testXml, "rocbookx.dtd", dtdFile, "section");

        assertFalse(result);
    }
}
