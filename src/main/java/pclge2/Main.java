package pclge2;

import javafx.application.Application;
import javafx.stage.Stage;
import pclge2.core.PathConfig;
import pclge2.ui.MainWindow;

/**
 * PCLGE2 启动器入口（Java 17 + JavaFX）。
 */
public class Main extends Application {

    private MainWindow window;

    @Override
    public void start(Stage stage) {
        window = new MainWindow(stage);
        window.show();
        String page = System.getProperty("pclge2.page");
        if (page != null && page.matches("0|1|2|3|4|5")) {
            window.openPage(Integer.parseInt(page));
        }
    }

    @Override
    public void stop() {
        if (window != null) {
            window.shutdown();
        }
    }

    public static void main(String[] args) {
        // 先把数据目录准备好，避免首次启动时目录缺失
        PathConfig.launcherDir();
        launch(args);
    }
}
