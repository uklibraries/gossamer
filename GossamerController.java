package gossamer;

import javafx.fxml.FXML;
import javafx.scene.control.ProgressBar;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.xml.sax.SAXException;

import javax.swing.filechooser.FileSystemView;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;


public class GossamerController {
    @FXML protected void handleProcessAction()
      throws IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        statusMessage.setText("Building folders...");
        progressBar.setVisible(true);
        eadFile.buildDirectories(progressBar);
    }

    @FXML protected void handleSelectXmlFindingAid()
      throws BackingStoreException, IOException, ParserConfigurationException, SAXException, XPathExpressionException {
        String home = FileSystemView.getFileSystemView().getHomeDirectory().toString();
        String initialDirectory = prefs.get(
            GOSSAMER_FILE_NODE,
            home
        );
        File f = new File(initialDirectory);
        if (!f.exists() || !f.isDirectory()) {
            prefs.put(
                    GOSSAMER_FILE_NODE,
                    home
            );
            initialDirectory = home;
        }
        fileChooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("XML", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        fileChooser.setInitialDirectory(new File(initialDirectory));
        eadFile = new EadFile(fileChooser.showOpenDialog(stage), xmlFilename, statusMessage);
        if (containerFolder != null) {
            eadFile.setDirectory(containerFolder);
        }
    }

    @FXML protected void handleSelectContainerFolder() throws IOException {
        String home = FileSystemView.getFileSystemView().getHomeDirectory().toString();
        String GOSSAMER_DIRECTORY_NODE = "gossamer/ui/prefs/Directory";
        String initialDirectory = prefs.get(
                GOSSAMER_DIRECTORY_NODE,
                home
        );
        File f = new File(initialDirectory);
        if (!f.exists() || !f.isDirectory()) {
            prefs.put(
                    GOSSAMER_FILE_NODE,
                    home
            );
            initialDirectory = home;
        }
        directoryChooser.setInitialDirectory(new File(initialDirectory));
        containerFolder = directoryChooser.showDialog(stage);
        if (containerFolder != null) {
            prefs.put(
                    GOSSAMER_DIRECTORY_NODE,
                    containerFolder.toString()
            );
            containerFoldername.setText(containerFolder.getCanonicalPath());
            if (eadFile != null) {
                eadFile.setDirectory(containerFolder);
            }
        }
    }

    void setStage(Stage primaryStage) {
        stage = primaryStage;
    }

    private Stage stage;
    final private FileChooser fileChooser = new FileChooser();
    final private DirectoryChooser directoryChooser = new DirectoryChooser();

    private EadFile eadFile;
    private File containerFolder;

    @FXML private Text xmlFilename;
    @FXML private Text containerFoldername;
    @FXML private Text statusMessage;
    @FXML private ProgressBar progressBar;

    private final String GOSSAMER_FILE_NODE = "gossamer/ui/prefs/File";
    private Preferences prefs = Preferences.userRoot();
}
