package gossamer;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.RunnableFuture;
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

    // The original version of buildDirectories() used a StackOverflow post by user ripper234
    // (http://stackoverflow.com/a/2818246/237176) as partial documentation for XPath.  That user
    // was in turn quoting http://www.roseindia.net/tutorials/xPath/java-xpath.shtml .  The code
    // has been pretty heavily modified since then.
    void buildDirectories(final ProgressBar pb)
      throws ParserConfigurationException, SAXException, IOException, XPathExpressionException {
        if (file != null) {
            DocumentBuilderFactory domFactory = DocumentBuilderFactory.newInstance();
            domFactory.setNamespaceAware(false);
            DocumentBuilder builder = domFactory.newDocumentBuilder();
            Document doc = builder.parse(file);
            final XPath xpath = XPathFactory.newInstance().newXPath();
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
                                    directory_queue = new LinkedList<String>();
                                    current_directory = new LinkedList<String>();
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
                };

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

    private Preferences prefs = Preferences.userRoot();
}
