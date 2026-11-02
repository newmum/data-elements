package com.haitong.files;

import com.haitong.config.RelayProperties;
import jakarta.servlet.MultipartConfigElement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Servlet multipart spills to the private runtime directory; the file kernel remains stream based. */
@Configuration
@ConditionalOnProperty(prefix="relay.files",name="enabled",havingValue="true")
public class FileMultipartConfiguration {
    @Bean
    MultipartConfigElement multipartConfigElement(FileTransferProperties files,RelayProperties relay) throws IOException {
        Path directory=Path.of(relay.getWorkDir(),"multipart").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        long limit=files.getSingleUploadMaxBytes();
        return new MultipartConfigElement(directory.toString(),limit,Math.addExact(limit,65536),0);
    }
}
