module com.echosoul {
    requires java.desktop;
    requires java.logging;

    requires javafx.controls;
    requires javafx.media;
    requires javafx.swing;

    requires com.google.gson;
    requires okhttp3;
    requires org.json;
    requires java.sdk;

    exports app;
    opens app to javafx.graphics;
}
