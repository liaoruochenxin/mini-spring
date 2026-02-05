package com.example.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.example.io.InputStreamCallback;

public class ClassPathUtils {

    public static <T> T readInputStream(String path, InputStreamCallback<T> inputStreamCallback) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try (InputStream input = getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return inputStreamCallback.doWithInputStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }
    }

    public static String readString(String path) {
        return readInputStream(path, (input) -> {
            byte[] allBytes = input.readAllBytes();
            return new String(allBytes, StandardCharsets.UTF_8);
        });
    }

    static ClassLoader getContextClassLoader() {
        ClassLoader cl = null;
        // Web应用的ClassLoader不是JVM提供的基于ClassPath的ClassLoader,而是Servlet提供的ClassLoader
        // 它不在默认的ClassPath搜索，而是在/WEB-INF/classes和/WEB-INF/lib搜索。
        // 从Thread.currentThread().getContextClassLoader()可以获取到Servlet容器的专属ClassLoader
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        return cl;
    }
}
