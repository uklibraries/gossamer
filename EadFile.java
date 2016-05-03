package gossamer;

import javafx.concurrent.Task;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import org.w3c.dom.Document;
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
        valid = false;
        update();
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file)
      throws BackingStoreException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        this.file = file;
        update();
    }

    protected void update()
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
    protected void validate() throws IOException, SAXException {
        statusText.setText("Validating XML finding aid");
        URL schemaFile = new URL("http://www.loc.gov/ead/ead.xsd");
        Source xmlFile= new StreamSource(file);
        SchemaFactory schemaFactory = SchemaFactory
                .newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = schemaFactory.newSchema(schemaFile);
        Validator validator = schema.newValidator();
        try {
            validator.validate(xmlFile);
            valid = true;
            statusText.setText("");
        } catch (SAXException e) {
            valid = false;
            statusText.setText("Invalid XML finding aid");
        }
    }

    // XPath example from StackOverflow by user ripper234
    // http://stackoverflow.com/a/2818246/237176
    //
    // Quoting http://www.roseindia.net/tutorials/xPath/java-xpath.shtml
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
                        if (isCancelled()) {
                            break;
                        }
                        Node n = nodes.item(i);
                        if (n != null && n.getNodeType() == Node.ELEMENT_NODE) {
                            List<String> path_components = new LinkedList<String>();
                            path_components.add(base);
                            path_components.add(base);
                            List<String> path_pieces = new LinkedList<String>();
                            XPathExpression containers_expr = xpath.compile("did/container");
                            NodeList containerList = (NodeList)containers_expr.evaluate(n, XPathConstants.NODESET);
                            for (int j = 0; j < containerList.getLength(); j += 1) {
                                Node container = containerList.item(j);
                                if (container != null && container.getNodeType() == Node.ELEMENT_NODE) {
                                    path_pieces.add(normalizeString(container.getTextContent()));
                                    // bozo
                                    StringBuilder containerStringBuilder = new StringBuilder();
                                    containerStringBuilder.append(base);
                                    for (String path_piece : path_pieces) {
                                        containerStringBuilder.append("_").append(path_piece);
                                    }
                                    String path_block = containerStringBuilder.toString();
                                    path_components.add(path_block);
                                }
                            }
                            StringBuilder subdirBuilder = new StringBuilder();
                            subdirBuilder.append(directory).append(File.separator);
                            for (Iterator<String> path_iterator = path_components.iterator(); path_iterator.hasNext(); ) {
                                subdirBuilder.append(path_iterator.next());
                                if (path_iterator.hasNext()) {
                                    subdirBuilder.append(File.separator);
                                }
                            }

                            File subdir = new File(subdirBuilder.toString());
                            subdir.mkdirs();
                            updateProgress(i + 1, count);
                            statusText.setText("Processed node " + i + " / " + count);
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
    private boolean valid;
}
