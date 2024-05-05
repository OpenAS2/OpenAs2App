package org.openas2.cmd.processor.restapi;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.ws.rs.Path;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PathScanner {
    private final Log logger = LogFactory.getLog(PathScanner.class.getSimpleName());

    public List<String> scanForAnnotatedMethods(String packageName) {
        List<String> annotatedMethods = new ArrayList<>();

        try {
            List<Class<?>> classes = getClasses(packageName);
            logger.debug("getClasses for package{" + packageName + "} the size of GetClasses=" + classes.size());
            for (Class<?> clazz : classes) {
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(Path.class)) {
                        Path pathAnnotation = method.getAnnotation(Path.class);
                        String info = "Class: " + clazz.getName() + ", Method: " + method.getName() + ", Path: " + pathAnnotation.value();
                        annotatedMethods.add(info);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error occurred while scanning for annotated methods: " + e.getMessage());
            e.printStackTrace();
        }

        return annotatedMethods;
    }

    private List<Class<?>> getClasses(String packageName) {
        List<Class<?>> classes = new ArrayList<>();
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = packageName.replace('.', '/');
            URL resource = classLoader.getResource(path);
            if (resource == null) {
                logger.error("Package not found: " + packageName);
                return classes;
            }
            URI uri = resource.toURI();
            Map<String, String> env = Collections.emptyMap(); // Provide empty map if null
            try {
                FileSystems.getFileSystem(uri);
            } catch (FileSystemNotFoundException e) {
                FileSystems.newFileSystem(uri, env);
            }
            java.nio.file.Path directory = Paths.get(uri);
            Files.walk(directory)
                .filter(file -> file.toString().endsWith(".class"))
                .forEach(file -> {
                    String className = packageName + '.' + file.getFileName().toString().replace(".class", "");
                    try {
                        classes.add(Class.forName(className));
                        logger.debug("classname "+className+"added to the  classes");
                    } catch (ClassNotFoundException e) {
                        logger.error("Class not found: " + className);
                    }
                });
        } catch (Exception e) {
            logger.error("Error occurred while loading classes: " + e.getMessage());
            e.printStackTrace();
        }
        return classes;
    }
}
