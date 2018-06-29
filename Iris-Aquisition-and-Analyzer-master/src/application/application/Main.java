package application;
	
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import mmcorej.*;
import org.json.*;

public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			BorderPane root = new BorderPane();
			Scene scene = new Scene(root,400,400);
			scene.getStylesheets().add(getClass().getResource("application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.show();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
    public static String getGreeting() {
        return "Hello world.";
    }

	public String MMCoreVersion(CMMCore core) {
		String info = core.getVersionInfo();
		return info;
	}
	//public void LoadCfg(String configfile){
	//core.loadSystemConfiguration(configfile);
	//}

	public static void main(String[] args) {
		launch(args);
		CMMCore core = new CMMCore();
		System.out.println(new application.Main().MMCoreVersion(core));
	}
}
