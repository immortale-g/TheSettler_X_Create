package com.thesettler_x_create.minecolonies.client.gui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.thesettler_x_create.ShopGuiLayouts;
import java.net.URL;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Validates every layout against the schema BlockUI ships in its own jar.
 *
 * <p>BlockUI resolves an element name to a pane class at run time and skips what it does not know.
 * A window whose {@code itemicon} was renamed upstream therefore loads, draws most of itself, and
 * leaves out one row - in game, never in the build. The same goes for an attribute BlockUI stops
 * reading.
 *
 * <p>Checking against {@code assets/blockui/gui/block_ui.xsd} asks BlockUI itself instead of
 * keeping a list of element names here, so every layout and every future element is covered at
 * once. The layouts name that schema by URL for editor support; this test validates against the
 * jar's copy and drops the hint, so it needs no network.
 */
class BlockUiLayoutSchemaTest {
  private static final String SCHEMA = "assets/blockui/gui/block_ui.xsd";
  private static final String SCHEMA_FULL_CHECKING =
      "http://apache.org/xml/features/validation/schema-full-checking";
  private static final String PANE_GROUP = "paneContainerGroup";

  @Test
  void everyLayoutMatchesTheBlockUiSchema() throws Exception {
    Schema schema = blockUiSchema();

    for (Path layout : ShopGuiLayouts.all()) {
      Validator validator = schema.newValidator();
      try {
        validator.validate(new DOMSource(withoutSchemaHint(layout)));
      } catch (Exception failure) {
        fail(layout + " does not match BlockUI's own schema: " + failure.getMessage(), failure);
      }
    }
  }

  /**
   * BlockUI's schema, with the one rule relaxed that its own layouts break as well: how many panes
   * a container must hold.
   *
   * <p>{@code listType} extends the scroll view, whose panes are already unbounded, and then asks
   * for exactly one more. That content model is ambiguous - Xerces refuses the file outright under
   * full schema checking, and with checking off it reads a list's single row template as the
   * unbounded part and then misses the required one. Nothing of this matters in game, where BlockUI
   * reads the xml itself and never the schema. What this test is after are the names: every element
   * and every attribute in our layouts still being one BlockUI declares.
   */
  private static Schema blockUiSchema() throws Exception {
    URL schemaUrl = BlockUiLayoutSchemaTest.class.getClassLoader().getResource(SCHEMA);
    assertNotNull(
        schemaUrl,
        SCHEMA
            + " is not on the test classpath. BlockUI used to ship it; without it the layouts are"
            + " checked by nothing until a player opens the window.");

    Document schemaDocument = parse(schemaUrl.toExternalForm());
    NodeList groups =
        schemaDocument.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "group");
    int relaxed = 0;
    for (int i = 0; i < groups.getLength(); i++) {
      Element group = (Element) groups.item(i);
      if (PANE_GROUP.equals(group.getAttribute("ref"))) {
        group.setAttribute("minOccurs", "0");
        relaxed++;
      }
    }
    assertTrue(
        relaxed > 0,
        "BlockUI's schema no longer references " + PANE_GROUP + "; this test needs a second look");

    SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
    schemaFactory.setFeature(SCHEMA_FULL_CHECKING, false);
    return schemaFactory.newSchema(new DOMSource(schemaDocument));
  }

  /**
   * The layout with its {@code xsi:noNamespaceSchemaLocation} removed. That attribute points at
   * BlockUI's schema on GitHub, and leaving it in place invites the validator to go and fetch it.
   */
  private static Document withoutSchemaHint(Path layout) throws Exception {
    Document document = parse(layout.toUri().toString());
    document
        .getDocumentElement()
        .removeAttributeNS(
            XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI, "noNamespaceSchemaLocation");
    return document;
  }

  private static Document parse(String systemId) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    return builder.parse(systemId);
  }
}
