/*
  Copyright 2011-2014 Red Hat

  This file is part of PresGang CCMS.

  PresGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PresGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PresGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.jboss.pressgang.ccms.contentspec.builder.constants;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.jboss.pressgang.ccms.utils.common.CollectionUtilities;

public class BuilderConstants {
    /**
     * Number of times to try setting the property tags on the topics
     */
    public static final Integer MAXIMUM_SET_PROP_TAG_NAME_RETRY = 50;
    public static final Integer FIXED_URL_BATCH_SIZE = 50;

    public static final String DOCBOOK_45_DTD = "docbookx.dtd";
    public static final String ROCBOOK_45_DTD = "rocbookx.dtd";
    public static final String DOCBOOK_50_RNG = "docbook.rng";

    public static final String FAILPENGUIN_PNG_NAME = "failpenguinPng";

    public static final Pattern IMAGE_FILE_REF_PATTERN = Pattern.compile("^((\\./)?images/)?\\d+(-\\d+)?\\.\\w+$");
    public static final Pattern COMMON_CONTENT_FILE_REF_PATTERN = Pattern.compile("^Common_Content/.*$");
    public static final Pattern VALID_PUBLICAN_DOCNAME_PATTERN = Pattern.compile("^[0-9a-zA-Z_\\-\\.\\+]+$");

    /**
     * A regular expression that identifies a topic id
     */
    public static final String INJECT_ID_RE = "(\\d+|T(\\d+|(\\-[ ]*[A-Za-z][A-Za-z\\d\\-_]*)))";
    public static final Pattern TOPIC_ID_PATTERN = Pattern.compile("^\\d+$");
    public static final Pattern PARSER_UNIQUE_ID_PATTERN = Pattern.compile("^L\\d+(-.*)?$");

    // Regex strings used to replace content
    public static final String ESCAPED_TITLE_REGEX = "<<contentSpec\\.escapedTitle>>";
    public static final String TITLE_REGEX = "<<contentSpec\\.title>>";
    public static final String BRAND_REGEX = "<<contentSpec\\.brand>>";
    public static final String VERSION_REGEX = "<<contentSpec\\.version>>";
    public static final String PRODUCT_REGEX = "<<contentSpec\\.product>>";
    public static final String EDITION_REGEX = "<<contentSpec\\.edition>>";
    public static final String PUBSNUMBER_REGEX = "\\<pubsnumber\\>.*\\</pubsnumber\\>";
    public static final String SUBTITLE_REGEX = "<<contentSpec\\.subtitle>>";
    public static final String BZPRODUCT_REGEX = "<<contentSpec\\.bzproduct>>";
    public static final String BZCOMPONENT_REGEX = "<<contentSpec\\.bzcomponent>>";
    public static final String BUILDER_VERSION_REGEX = "<<csBuilder\\.version>>";
    public static final String BOOK_TYPE_REGEX = "<<contentSpec\\.bookType>>";
    public static final String DRAFT_REGEX = "<<contentSpec\\.draft>>";
    public static final String DATE_FORMAT_REGEX = "Day Mon DD HH:MM:SS YYYY";
    public static final String REV_DATE_FORMAT_REGEX = "DAY MON DD YYYY";
    public static final String DATE_STRING_FORMAT = "EEE MMM dd HH:mm:ss yyyy";
    public static final String REV_DATE_STRING_FORMAT = "EEE MMM dd yyyy";
    public static final String YEAR_FORMAT_REGEX = "YYYY";
    public static final String TOPIC_ID_REGEX = "<!-- Inject TopicID -->";
    public static final String TOPIC_TITLE_REGEX = "<!-- Inject TopicTitle -->";
    public static final String ERROR_XREF_REGEX = "<!-- Inject ErrorXREF -->";
    public static final String PREFACE_REGEX = "<!-- Inject Preface -->";
    public static final String LEGAL_NOTICE_REGEX = "<!-- Inject Legal Notice -->";
    public static final String ABSTRACT_REGEX = "<!-- Inject Abstract -->";
    public static final String REV_HISTORY_REGEX = "<!-- Inject Revision History -->";
    public static final String XIINCLUDES_INJECTION_STRING = "<!-- Inject XIIncludes -->";
    public static final String CONTENT_SPEC_ID_REGEX = "<<contentSpec\\.ID>>";
    public static final String CONTENT_SPEC_REV_REGEX = "<<contentSpec\\.Rev>>";
    public static final String CONTENT_SPEC_COPYRIGHT_REGEX = "<<contentSpec\\.copyrightHolder>>";
    public static final String CONTENT_SPEC_BUGZILLA_URL_REGEX = "<<contentSpec\\.bugzillaUrl>>";

    // Revision_History.xml regex constants
    public static final String AUTHOR_FIRST_NAME_REGEX = "<!-- Inject authorInformation\\.firstName -->";
    public static final String AUTHOR_SURNAME_REGEX = "<!-- Inject authorInformation\\.lastName -->";
    public static final String AUTHOR_EMAIL_REGEX = "<!-- Inject authorInformation\\.email -->";
    public static final String REVNUMBER_REGEX = "<!-- Inject revnumber -->";

    // Common Content File Names
    public static final String[] COMMON_CONTENT_FILES = new String[]{"Conventions.xml", "Program_Listing.xml", "Feedback.xml",
            "Legal_Notice.xml"};

    public static final String BUILT_MSG = "Built from Content Specification: %d, Revision: %d";
    public static final String BUILT_FILE_MSG = "Content Specification built from file";

    // Defaults
    public static final String DEFAULT_CONDITION = "default";
    public static final String DEFAULT_DB45_BRAND = "common";
    public static final String DEFAULT_DB50_BRAND = "common-db5";
    public static final String SUBTITLE_DEFAULT = "Subtitle goes here";
    public static final String DEFAULT_EDITION = "1";
    public static final String DEFAULT_PUBSNUMBER = "1";
    public static final String DEFAULT_VERSION = "1.0";

    public static final String DEFAULT_AUTHOR_FIRSTNAME = "CS Builder";
    public static final String DEFAULT_AUTHOR_LASTNAME = "Robot";
    public static final String DEFAULT_EMAIL = "robot@dev.null.com";
    public static final String DEFAULT_REVNUMBER = "0.0-0";

    public static final String LINUX_PUBLICAN_COMMON_CONTENT = "/usr/share/publican/Common_Content/";

    public static final String DEFAULT_ABSTRACT_TEXT = "A brief paragraph describing this book. This will be used as the description for " +
            "the rpm package.";
    public static final String DEFAULT_ABSTRACT = "<abstract>\n\t\t<para>\n" +
            "\t\t\t" + DEFAULT_ABSTRACT_TEXT + "\n" +
            "\t\t</para>\n" +
            "\t</abstract>\n";

    public static final String CS_NAME_ENT_FILE = "<!ENTITY PRODUCT \"<<contentSpec.product>>\">\n" +
            "<!ENTITY BOOKID \"<<contentSpec.escapedTitle>>\">\n" +
            "<!ENTITY YEAR \"YYYY\">\n" +
            "<!ENTITY TITLE \"<<contentSpec.Title>>\">\n" +
            "<!ENTITY HOLDER \"<<contentSpec.copyrightHolder>>\">\n" +
            "<!ENTITY BZURL \"<<contentSpec.bugzillaUrl>>\">\n" +
            "<!ENTITY BZCOMPONENT \"<<contentSpec.bzcomponent>>\">\n" +
            "<!ENTITY BZPRODUCT \"<<contentSpec.bzproduct>>\">";

    public static final String LEGAL_NOTICE_XML = "<xi:include href=\"Legal_Notice.xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n"
            + "\t<xi:fallback xmlns:xi=\"http://www.w3.org/2001/XInclude\">\n" + "\t\t<xi:include href=\"Common_Content/Legal_Notice" +
            ".xml\" xmlns:xi=\"http://www.w3.org/2001/XInclude\" />\n" + "\t</xi:fallback>\n" + "</xi:include>";


    // Warning compiler output messages.
    public static final String WARNING_UNTRANSLATED_TOPIC = "This topic is an untranslated topic.";
    public static final String WARNING_NONPUSHED_TOPIC = "This topic hasn't been pushed for translation.";
    public static final String WARNING_OLD_UNTRANSLATED_TOPIC = "This untranslated topic uses content that is older than the specified "
            + "topic's content.";
    public static final String WARNING_OLD_TRANSLATED_TOPIC = "This topic's translated content is older than the specified topic's " +
            "content.";
    public static final String WARNING_INCOMPLETE_TRANSLATION = "This topic hasn't been fully translated.";
    public static final String WARNING_EMPTY_TOPIC_XML = "This topic has no XML data";
    public static final String WARNING_FUZZY_TRANSLATION = "This topic contains strings that are marked as \"fuzzy\".";
    public static final String WARNING_POSSIBLE_INVALID_INJECTIONS = "is possibly an invalid custom Injection Point.";

    // Error compiler output messages.
    public static final String ERROR_INVALID_XML_CONTENT = "This topic contains an invalid element that can't be converted into a DOM " +
            "Element.";
    public static final String ERROR_BAD_XML_STRUCTURE = "This topic doesn't have well-formed xml.";
    public static final String ERROR_INVALID_TOPIC_XML = "This topic has invalid DocBook XML.";
    public static final String ERROR_INVALID_INJECTIONS = "This topic has invalid Injection Points.";

    // Glossary Defs for warning messages
    public static final List<String> WARNING_NO_CONTENT_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic doesn't have any XML Content to display.", "To fix this warning, " +
                    "" + "open the topic URL and add some content."});

    public static final List<String> WARNING_UNTRANSLATED_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic hasn't been translated yet by the Translator(s), as such the topic will be displayed using the " +
                    "untranslated content.", "To fix this warning, please contact the Translator(s) responsible for translating the " +
                    "topics in this locale."});

    public static final List<String> WARNING_NONPUSHED_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic hasn't been pushed for translation yet, as such the topic will be displayed using the original " +
                    "topic's content.", "To fix this warning, please send a request to the User responsible for pushing Translations to "
                    + "Zanata and request that the topic be pushed for translation."});

    public static final List<String> WARNING_INCOMPLETE_TRANSLATED_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic hasn't finished being translated by the Translator(s) yet, as such the topic will be displayed using " +
                    "incomplete translated content.", "To fix this warning, please contact the Translator(s) responsible for " +
                    "translating the topics in this locale."});

    public static final List<String> WARNING_FUZZY_TRANSLATED_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic hasn't finished being translated by the Translator(s) yet, as such the topic will be displayed using " +
                    "translated content that may not be 100% correct.", "To fix this warning, " +
                    "please contact the Translator(s) responsible for translating the topics in this locale."});

    public static final List<String> WARNING_OLD_UNTRANSLATED_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"A previous revision of this topic has been pushed to Zanata, and has not yet been translated. This previous" +
                    " revision has been included in the book, but will display content that is older than what was defined by the Content" +
                    " Specification.", "To fix this warning, please send a request to the User responsible for pushing Translations to "
                    + "Zanata and request that the topic be pushed for translation."});

    public static final List<String> WARNING_OLD_TRANSLATED_TOPIC_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"A previous revision of this topic has been pushed to Zanata, and has been translated. This previous " +
                    "revision has been included in the book, but will display content that is older than what was defined by the Content " +
                    "Specification.", "To fix this warning, please send a request to the User responsible for pushing Translations to " +
                    "Zanata and request that the topic be pushed for translation. In most cases the existing translations will be " +
                    "able to be reused when the topic is pushed to Zanata."});

    public static final List<String> WARNING_POSSIBLE_INVALID_INJECTIONS_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The XML comment mentioned has been identified as a possible custom Injection Point, " +
                    "that is incorrectly referenced.", "To fix this error please ensure that the type is valid, " +
                    "a colon is used to separate the IDs from the type and only topic IDs are used in the ID list."});

    // Glossary Defs for error messages
    public static final List<String> ERROR_INVALID_XML_CONTENT_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic XML contains invalid elements that cannot be successfully converted in DOM elements.",
                    "To fix this error please remove or correct any invalid XML elements or entities."});

    public static final List<String> ERROR_BAD_XML_STRUCTURE_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic XML is not well-formed XML and maybe missing opening or closing element statements.",
                    "To fix this error please ensure that all XML elements having an opening and closing statement and all XML reserved "
                            + "characters are represented as XML entities."});

    public static final List<String> ERROR_INVALID_TOPIC_XML_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic XML is not valid against the DocBook specification.", "To fix this error please ensure that all XML " +
                    "elements are valid DocBook elements . Also check to ensure all XML sub elements are valid for the root XML element."});

    public static final List<String> ERROR_INVALID_INJECTIONS_DEFINITION = CollectionUtilities.toArrayList(
            new String[]{"The topic XML contains Injection Points that cannot be resolved into links.",
                    "To fix this error please ensure that all the topics referred to by Injection Points are included in the build and/or" +
                            " have adequate relationships."});

    public static final List<String> VALID_PROGRAM_LISTING_LANGS = Arrays.asList("Java", "SQL", "C", "C++", "Bash", "Perl", "JavaScript",
            "Python", "XML", "Ruby", "C#", "HTML", "CSS", "Javadoc", "Haskell", "Lua", "Makefile", "Pascal", "RPM Spec", "Diff");

    public static final List<String> VALID_PROGRAM_LISTING_LANGS_LOWERCASE = Arrays.asList("java", "sql", "c", "c++", "bash", "perl",
            "javascript", "python", "xml", "ruby", "c#", "html", "css", "javadoc", "haskell", "lua", "makefile", "pascal", "rpm spec",
            "diff");

    public static final String ERROR_TOPIC_CANNOT_BE_USED_AS_INITIAL_CONTENT = "The Topic has content that" +
            " cannot be used in the initial content of a Chapter/Preface/Section/Appendix/Part.";
}
