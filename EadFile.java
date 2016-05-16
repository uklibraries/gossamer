package gossamer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.*;
import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

class EadFile {
    EadFile(File file, Text filenameText, Text statusText)
      throws BackingStoreException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        this.file = file;
        if (file.getName().indexOf(".") > 0) {
            this.base = file.getName().substring(0, file.getName().lastIndexOf(".")).toLowerCase();
        }
        else {
            this.base = file.getName().toLowerCase();
        }
        this.filenameText = filenameText;
        this.statusText = statusText;
        update();
    }

    private void update()
      throws BackingStoreException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        if (file != null) {
            String GOSSAMER_FILE_NODE = "gossamer/ui/prefs/File";
            prefs.put(
                    GOSSAMER_FILE_NODE,
                file.getParentFile().toString()
            );
            prefs.flush();
            statusText.setText("Loading XML finding aid");
            filenameText.setText(file.getName());
            statusText.setText("");
            validate();
        }
    }

    // Example from StackOverflow by user McDowell
    // http://stackoverflow.com/a/16054/237176
    private void validate() throws IOException, SAXException {
        statusText.setText("Validating XML finding aid");
        URL schemaFile = new URL("http://www.loc.gov/ead/ead.xsd");
        Source xmlFile= new StreamSource(file);
        SchemaFactory schemaFactory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(schemaFile);
        Validator validator = schema.newValidator();
        try {
            validator.validate(xmlFile);
            statusText.setText("");
        } catch (SAXException e) {
            statusText.setText("Invalid XML finding aid");
        }
    }

    private String getTextFor(String xpathString, String defaultString)
      throws XPathExpressionException {
        List<String> texts = getTextsFor(xpathString);
        if (texts.size() > 0) {
            return texts.get(0);
        }
        else {
            return defaultString;
        }
    }

    private List<String> getTextsFor(String xpathString)
      throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.compile(xpathString).evaluate(doc, XPathConstants.NODESET);
        List<String> result = new LinkedList<>();
        for (int j = 0; j < nodes.getLength(); ++j) {
            Node node = nodes.item(j);
            String text = node.getTextContent().trim();
            if (!text.equals("")) {
                result.add(text);
            }
        }
        return result;
    }

    private void buildMetsFile()
      throws XPathExpressionException, ParserConfigurationException, TransformerException, FileNotFoundException {
        class MetsMetadata {
            /* If this program was used at more than one site, we'd want to
               generalize this.

               In my Ruby source for ibrik, the basis for most of this section,
               creator repository is defined as

               # https://github.com/cokernel/ibrik/blob/master/lib/kdl/ead_metadata.rb#L48
               ```ruby
               def creator_repository
                 Partner.normalize(@xml.xpath('//xmlns:archdesc/xmlns:did/xmlns:repository').first.content)
               end
               ```

               The Partner class knows about all the repositories that have
               contributed to the Kentucky Digital Library, and can render their
               names in a normal form.

               However, ExploreUK only has one contributing repository, the
               University of Kentucky, so I choose to hardcode it here.

               -- Michael Slone, 2016-05-11
             */
            private String creator_repository = "University of Kentucky";
            private String preservation_repository = "University of Kentucky";

            /* Administrative metadata */
            private String accession_number;
            private String current_timestamp;
            private String current_year;
            private String record_status = "SIP";

            /* Dublin Core metadata */
            private String content_type;
            private String creator;
            private String date;
            private String description;
            private String format;
            private String title;
            private List<String> subjects;

            private void storeMetadataFromEad()
              throws XPathExpressionException {
                {
                    /* current_timestamp in Ruby:
                       Time.new.strftime('%Y-%m-%dT%H:%M:%S')
                    */
                    Date now = new Date();
                    StringBuilder sb = new StringBuilder();
                    Formatter formatter = new Formatter(sb, Locale.US);
                    formatter.format("%tY-%tm-%tdT%tH:%tM:%tS", now, now, now, now, now, now);
                    current_timestamp = sb.toString();

                    sb = new StringBuilder();
                    formatter = new Formatter(sb, Locale.US);
                    formatter.format("%tY", now);
                    current_year = sb.toString();
                }
                /* Should we read accession_number from the EAD itself? */
                accession_number = base;
                creator = getTextFor("//archdesc/did/origination", "unknown");
                date = getTextFor("//archdesc/did/unitdate", "");
                subjects = getTextsFor("//archdesc/controlaccess/subject");
                title = getTextFor("//archdesc/did/unittitle", "");
                if (title.equals("")) {
                    title = getTextFor("//archdesc/did/unitdate", "unknown");
                }
                format = "collections";
                content_type = "text";
                {
                    List<String> date_daos = getTextsFor("//date[@type='dao']");
                    if (date_daos.size() > 0) {
                        format = "images";
                        content_type = "image";
                    }
                }
                List<String> descriptions = getTextsFor("//physdesc");
                {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < descriptions.size(); ++j) {
                        sb.append(descriptions.get(j).replaceAll("\\s+", " "));
                        if (j + 1 < descriptions.size()) {
                            sb.append(" ");
                        }
                    }
                    description = sb.toString();
                }
            }

            private Document toDocument()
              throws ParserConfigurationException {
                DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
                domFactory.setNamespaceAware(true);
                Document doc = domFactory.newDocumentBuilder().newDocument();
                String mets = "http://www.loc.gov/METS/";
                Element rootNode = doc.createElementNS(mets, "mets:mets");
                doc.appendChild(rootNode);
                rootNode.setAttribute("OBJID", accession_number);
                rootNode.setAttribute("PROFILE", "lc:bibRecord");
                rootNode.setAttribute("xmlns:rights", "http://www.loc.gov/rights/");
                rootNode.setAttribute("xmlns:xlink", "http://www.w3.org/1999/xlink");
                rootNode.setAttribute("xmlns:lc", "http://www.loc.gov/mets/profiles");
                rootNode.setAttribute("xmlns:bib", "http://www.loc.gov/mets/profiles/bibRecord");
                String oai_dc = "http://www.openarchives.org/OAI/2.0/oai_dc/";
                rootNode.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
                rootNode.setAttribute("xsi:schemaLocation", "http://www.loc.gov/METS/ http://www.loc.gov/standards/mets/mets.xsd http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd");

                /* METS header */
                {
                    Element metsHdr = doc.createElementNS(mets, "mets:metsHdr");
                    metsHdr.setAttribute("CREATEDATE", current_timestamp);
                    metsHdr.setAttribute("LASTMODDATE", current_timestamp);
                    metsHdr.setAttribute("RECORDSTATUS", record_status);
                    String submission_note = "SIP METS and associated file(s) submitted to KUDL.";
                    Element creatorAgent = doc.createElementNS(mets, "mets:agent");
                    creatorAgent.setAttribute("ROLE", "CREATOR");
                    creatorAgent.setAttribute("TYPE", "REPOSITORY");
                    {
                        Element name = doc.createElementNS(mets, "mets:name");
                        name.setTextContent(creator_repository);
                        Element note = doc.createElementNS(mets, "mets:note");
                        note.setTextContent(submission_note);
                        creatorAgent.appendChild(name);
                        creatorAgent.appendChild(note);
                    }
                    metsHdr.appendChild(creatorAgent);

                    Element preservationAgent = doc.createElementNS(mets, "mets:agent");
                    preservationAgent.setAttribute("ROLE", "PRESERVATION");
                    preservationAgent.setAttribute("TYPE", "ORGANIZATION");
                    {
                        Element name = doc.createElementNS(mets, "mets:name");
                        name.setTextContent(preservation_repository);
                        Element note = doc.createElementNS(mets, "mets:note");
                        note.setTextContent(submission_note);
                        preservationAgent.appendChild(name);
                        preservationAgent.appendChild(note);
                    }
                    metsHdr.appendChild(preservationAgent);

                    Element altRecordID = doc.createElementNS(mets, "mets:altRecordID");
                    altRecordID.setAttribute("TYPE", "SIP");
                    altRecordID.setTextContent(accession_number);
                    metsHdr.appendChild(altRecordID);

                    rootNode.appendChild(metsHdr);
                }

                /* Descriptive metadata */
                {
                    Element dmdSec = doc.createElementNS(mets, "mets:dmdSec");
                    dmdSec.setAttribute("ID", "DMD1");
                    rootNode.appendChild(dmdSec);

                    Element mdWrap = doc.createElementNS(mets, "mets:mdWrap");
                    mdWrap.setAttribute("MIMETYPE", "text/xml");
                    mdWrap.setAttribute("MDTYPE", "OAI_DC");
                    dmdSec.appendChild(mdWrap);

                    Element xmlData = doc.createElementNS(mets, "mets:xmlData");
                    mdWrap.appendChild(xmlData);

                    Element dublin_core = doc.createElementNS(oai_dc, "oai_dc:dc");
                    String dc = "http://purl.org/dc/elements/1.1/";
                    dublin_core.setAttribute("xmlns:dc", dc);
                    dublin_core.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
                    dublin_core.setAttribute("xsi:schemaLocation", "http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd");
                    xmlData.appendChild(dublin_core);

                    Element dc_title = doc.createElement("dc:title");
                    dc_title.setAttribute("xml:lang", "en");
                    dc_title.setTextContent(title);
                    dublin_core.appendChild(dc_title);

                    Element dc_source = doc.createElement("dc:source");
                    dc_source.setTextContent(title);
                    dublin_core.appendChild(dc_source);

                    Element dc_creator = doc.createElement("dc:creator");
                    dc_creator.setTextContent(creator);
                    dublin_core.appendChild(dc_creator);

                    Element dc_identifier = doc.createElement("dc:identifier");
                    dc_identifier.setTextContent(accession_number);
                    dublin_core.appendChild(dc_identifier);

                    for (String subject : subjects) {
                        Element dc_subject = doc.createElement("dc:subject");
                        dc_subject.setTextContent(subject);
                        dublin_core.appendChild(dc_subject);
                    }

                    Element dc_publisher = doc.createElement("dc:publisher");
                    dc_publisher.setTextContent(creator_repository);
                    dublin_core.appendChild(dc_publisher);

                    Element dc_date = doc.createElement("dc:date");
                    dc_date.setTextContent(date);
                    dublin_core.appendChild(dc_date);

                    Element dc_format = doc.createElement("dc:format");
                    dc_format.setTextContent(format);
                    dublin_core.appendChild(dc_format);

                    Element dc_type = doc.createElement("dc:type");
                    dc_type.setTextContent(content_type);
                    dublin_core.appendChild(dc_type);

                    /* We're currently hardcoding language and rights.
                       This probably isn't the right place to store the
                       hardcoded versions.
                     */

                    Element dc_language = doc.createElement("dc:language");
                    dc_language.setTextContent("English");
                    dublin_core.appendChild(dc_language);

                    Element dc_rights = doc.createElement("dc:rights");
                    dc_rights.setTextContent("This digital resource may be freely searched and displayed.  Permission must be received for subsequent distribution in print or electronically.  Physical rights are retained by the owning repository.  Copyright is retained in accordance with U. S. copyright laws.  Please go to https://exploreuk.uky.edu for more information.");
                    dublin_core.appendChild(dc_rights);

                    Element dc_description = doc.createElement("dc:description");
                    dc_description.setTextContent(description);
                    dublin_core.appendChild(dc_description);
                }

                /* Administrative metadata */
                {
                    Element amdSec = doc.createElementNS(mets, "mets:amdSec");
                    rootNode.appendChild(amdSec);

                    {
                        Element rightsMD = doc.createElementNS(mets, "mets:rightsMD");
                        rightsMD.setAttribute("ID", "RMD1");
                        amdSec.appendChild(rightsMD);

                        Element mdWrap = doc.createElementNS(mets, "mets:mdWrap");
                        mdWrap.setAttribute("MDTYPE", "OTHER");
                        mdWrap.setAttribute("MIMETYPE", "text/xml");
                        mdWrap.setAttribute("OTHERMDTYPE", "RIGHTSMD");
                        rightsMD.appendChild(mdWrap);

                        Element xmlData = doc.createElementNS(mets, "mets:xmlData");
                        mdWrap.appendChild(xmlData);

                        Element versionStatement = doc.createElementNS(mets, "mets:versionStatement");
                        versionStatement.setTextContent(current_year);
                        xmlData.appendChild(versionStatement);
                    }

                    {
                        Element rightsMD = doc.createElementNS(mets, "mets:rightsMD");
                        rightsMD.setAttribute("ID", "ADMRTS1");
                        amdSec.appendChild(rightsMD);

                        Element mdWrap = doc.createElementNS(mets, "mets:mdWrap");
                        mdWrap.setAttribute("MDTYPE", "OTHER");
                        mdWrap.setAttribute("OTHERMDTYPE", "METSRights");
                        rightsMD.appendChild(mdWrap);

                        Element xmlData = doc.createElementNS(mets, "mets:xmlData");
                        mdWrap.appendChild(xmlData);

                        Element rightsDecl = doc.createElementNS(mets, "mets:RightsDeclarationMD");
                        rightsDecl.setAttribute("RIGHTSCATEGORY", "");
                        xmlData.appendChild(rightsDecl);

                        Element context = doc.createElementNS(mets, "mets:Context");
                        context.setAttribute("CONTEXTCLASS", "GENERAL PUBLIC");
                        rightsDecl.appendChild(context);

                        Element constraints = doc.createElementNS(mets, "mets:Constraints");
                        constraints.setAttribute("CONSTRAINTTYPE", "RE-USE");
                        context.appendChild(constraints);

                        Element constraintDesc = doc.createElementNS(mets, "mets:ConstraintDescription");
                        constraintDesc.setTextContent("View Collection Guide to obtain rights information.");
                        constraints.appendChild(constraintDesc);
                    }
                }

                return doc;
            }
        }

        MetsMetadata metadata = new MetsMetadata();
        metadata.storeMetadataFromEad();
        Document doc = metadata.toDocument();
        StringWriter stringWriter = new StringWriter();
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc.getDocumentElement()), new StreamResult(stringWriter));
        String xml = stringWriter.toString();
        String filename;
        {
            StringBuilder sb = new StringBuilder();
            sb.append(directory).append(File.separator);
            sb.append(base).append(File.separator);
            File dir = new File(sb.toString());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            sb.append("mets.xml");
            filename = sb.toString();
        }
        PrintWriter printWriter = new PrintWriter(filename);
        printWriter.print(xml);
        printWriter.close();
    }

    // The original version of buildDirectories() used a StackOverflow post by user ripper234
    // (http://stackoverflow.com/a/2818246/237176) as partial documentation for XPath.  That user
    // was in turn quoting http://www.roseindia.net/tutorials/xPath/java-xpath.shtml .  The code
    // has been pretty heavily modified since then.
    void buildDirectories(final ProgressBar pb)
      throws ParserConfigurationException, SAXException, IOException, XPathExpressionException, TransformerException {
        if (file != null) {
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(false);
            DocumentBuilder builder = domFactory.newDocumentBuilder();
            doc = builder.parse(file);
            xpath = XPathFactory.newInstance().newXPath();
            buildMetsFile();
            XPathExpression component_expr = getXpathForComponentWithContainers(xpath);

            final NodeList nodes = (NodeList)component_expr.evaluate(doc, XPathConstants.NODESET);
            statusText.setText("Count: " + nodes.getLength());
            Task task = new Task<Void>() {
                @Override protected Void call() throws Exception {
                    final int count = nodes.getLength();
                    for (int i = 0; i < count; i += 1) {
                        final int counter = i + 1;
                        if (isCancelled()) {
                            break;
                        }
                        Node n = nodes.item(i);
                        if (n != null && n.getNodeType() == Node.ELEMENT_NODE) {
                            Queue<String> directory_creation_queue;
                            final XPathExpression containers_expr = xpath.compile("did/container");
                            NodeList containerList = (NodeList)containers_expr.evaluate(n, XPathConstants.NODESET);

                            boolean hasParentAttribute = false;
                            for (int j = 0; j < containerList.getLength(); j += 1) {
                                Node container = containerList.item(j);
                                if (container != null && container.getNodeType() == Node.ELEMENT_NODE) {
                                    NamedNodeMap attributes = container.getAttributes();
                                    if (attributes.getNamedItem("parent") != null) {
                                        hasParentAttribute = true;
                                        break;
                                    }
                                }
                            }

                            class DirectoryQueueBuilder {
                                private Queue<String> directory_queue;
                                private Queue<String> current_directory;
                                private String base;
                                private boolean simple;
                                private boolean finalized = false;

                                private DirectoryQueueBuilder(String base, boolean hasParentAttribute) {
                                    this.base = base;
                                    simple = hasParentAttribute;
                                    directory_queue = new LinkedList<>();
                                    current_directory = new LinkedList<>();
                                }

                                private Queue<String> getDirectoryQueue() {
                                    if (!finalized) {
                                        finalized = true;
                                        processCurrentDirectory();
                                    }
                                    return directory_queue;
                                }

                                private void insert(Node container) {
                                    if (!simple) {
                                        NamedNodeMap attributes = container.getAttributes();
                                        Node parent_id = attributes.getNamedItem("parent");
                                        if (parent_id != null) {
                                            processCurrentDirectory();
                                        }
                                    }
                                    current_directory.add(renderContainer(container));
                                }

                                private void processCurrentDirectory() {
                                    if (current_directory.isEmpty()) {
                                        return;
                                    }
                                    StringBuilder sb = new StringBuilder();
                                    sb.append(directory).append(File.separator);
                                    sb.append(base).append(File.separator);
                                    sb.append(base).append(File.separator);
                                    while (!current_directory.isEmpty()) {
                                        String dir = current_directory.remove();
                                        sb.append(dir);
                                        if (current_directory.peek() != null) {
                                            sb.append(File.separator);
                                        }
                                    }
                                    directory_queue.add(sb.toString());
                                }

                                private String renderContainer(Node container) {
                                    NamedNodeMap attributes = container.getAttributes();
                                    String container_type = "container";
                                    String container_number = container.getTextContent().trim();
                                    String raw_type = normalizeString(attributes.getNamedItem("type").getTextContent());
                                    if (raw_type.length() > 0) {
                                        if (raw_type.equals("othertype")) {
                                            String raw_label = normalizeString(attributes.getNamedItem("label").getTextContent());
                                            if (raw_label.length() > 0) {
                                                container_type = raw_label;
                                            }
                                        } else {
                                            container_type = raw_type;
                                        }
                                    }
                                    // https://gist.github.com/jimjam88/8559505
                                    container_type = Character.toUpperCase(container_type.charAt(0)) + container_type.substring(1);
                                    return container_type + "_" + container_number;
                                }
                            }

                            DirectoryQueueBuilder qb = new DirectoryQueueBuilder(base, hasParentAttribute);

                            for (int j = 0; j < containerList.getLength(); j += 1) {
                                qb.insert(containerList.item(j));
                            }

                            directory_creation_queue = qb.getDirectoryQueue();

                            while (directory_creation_queue.peek() != null) {
                                File subdir = new File(directory_creation_queue.remove());
                                if (!subdir.exists()) {
                                    subdir.mkdirs();
                                }
                            }

                            updateProgress(counter, count);
                            Platform.runLater(() -> {
                                statusText.setText("Processed component " + (counter - 1) + " / " + count);
                            });
                        }
                    }
                    return null;
                }

                @Override protected void succeeded() {
                    super.succeeded();
                    statusText.setText("Finished building folders");
                    pb.setVisible(false);
                }
            };
            pb.progressProperty().unbind();
            pb.progressProperty().bind(task.progressProperty());
            Thread th = new Thread(task);
            th.setDaemon(true);
            th.start();
            pb.setVisible(true);
        }
    }

    private XPathExpression getXpathForComponentWithContainers(XPath xpath) throws XPathExpressionException {
        List<String> components = Arrays.asList(
                "c", "c01", "c02", "c03", "c04", "c05", "c06",
                "c07", "c08", "c09", "c10", "c11", "c12"
        );
        String hasaContainer = "[did/container]";
        StringBuilder sb = new StringBuilder();

        for (Iterator<String> iter = components.iterator(); iter.hasNext(); ) {
            sb.append("//").append(iter.next()).append(hasaContainer);
            if (iter.hasNext()) {
                sb.append('|');
            }
        }
        String isaComponentWithContainers = sb.toString();

        return xpath.compile(isaComponentWithContainers);
    }

    private String normalizeString(String string) {
        return string.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9]", "_");
    }

    void setDirectory(File directory) {
        this.directory = directory;
    }

    private String base;
    private File file;
    private Text filenameText;
    private File directory;
    private Text statusText;
    private Document doc;
    private XPath xpath;

    private Preferences prefs = Preferences.userRoot();
}
