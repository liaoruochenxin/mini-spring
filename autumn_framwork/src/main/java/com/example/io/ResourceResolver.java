package com.example.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple classpath scan works both in directory and jar:
 * 
 * https://stackoverflow.com/questions/520328/can-you-find-all-classes-in-a-package-using-reflection#58773038
 * 
 * 做了什么？
 * 根据传入的包，获取对应classpath下文件
 * 
 * @author jw
 * @date 2026-1-15
 */
public class ResourceResolver {
    Logger logger = LoggerFactory.getLogger(getClass());

    String basePackage;

    public ResourceResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    public <R> List<R> scan(Function<Resource, R> mapper) {
        String basePackagePath = basePackage.replaceAll(".", "/");
        String path = basePackagePath;
        try {
            List<R> collector = new ArrayList<>();
            scan0(basePackagePath, path, collector, mapper);
            return collector;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    <R> void scan0(String basePackagePath, String path, List<R> collector, Function<Resource,R> mapper) throws IOException, URISyntaxException {
        logger.atDebug().log("scan path:{}", path);
        logger.atDebug().log("basePackagePath:{}", basePackagePath);
        Enumeration<URL> en = getContextClassLoader().getResources(path);
        while (en.hasMoreElements()) {
            URL url = en.nextElement();
            URI uri = url.toURI();
            String uriString = removeTrailingSlash(uriToString(uri));
            String uriBaseStr = uriString.substring(0, uriString.length() - basePackagePath.length());
            if (uriBaseStr.startsWith("file:")) {
                uriBaseStr = uriBaseStr.substring(5);
            }
            if (uriBaseStr.startsWith("jar:")) {
                scanFile(true, uriBaseStr, jarUriToPath(basePackagePath, uri), collector, mapper);
            } else {
                scanFile(false, uriBaseStr, Paths.get(uri), collector, mapper);
            }
        }
    }

    <R> void scanFile(boolean isJar, String base, Path root, List<R> collector, Function<Resource,R> mapper) throws IOException {
        String baseDir = removeTrailingSlash(base);
        Files.walk(root).filter(Files::isRegularFile).forEach(file -> {
                Resource res = null;
                if (isJar) {
                    res = new Resource(baseDir, removeLeadingSlash(file.toString()));
                } else {
                    String path = file.toString();
                    String name = removeLeadingSlash(path.substring(baseDir.length()));
                    res = new Resource("file:" + path, name);
                }
                logger.atDebug().log("found resource: {}", res);
                R r = mapper.apply(res);
                if (r != null) {
                    collector.add(r);
                }
            }
        );
    }

    ClassLoader getContextClassLoader() {
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

    /**
     * 去除结尾的斜杠
     * @param s 去除前字符串
     * @return 去除后字符串
     */
    String removeTrailingSlash(String s) {
        if (s.endsWith("/") || s.endsWith("\\")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /**
     * 将uri对象转为String
     * @param uri uri对象
     * @return uri对象的字符串
     */
    String uriToString(URI uri) {
        return URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8);
    }

    /**
     * 将jar包的uri对象转为Path对象
     * @param basePackagePath 
     * @param jarUri
     * @return
     * @throws IOException
     */
    Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
    }

    String removeLeadingSlash(String s) {
        if (s.startsWith("/") || s.startsWith("\\")) {
            s = s.substring(1);
        }
        return s;
    }
}
