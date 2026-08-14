package com.triread.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyBatisSqlSafetyTest {

    @Test
    void mapperQueriesUseBoundParametersInsteadOfStringSubstitution() throws IOException {
        Path mapperDirectory = Path.of("src", "main", "resources", "mapper");

        List<Path> mapperFiles;
        try (var paths = Files.walk(mapperDirectory)) {
            mapperFiles = paths
                    .filter(path -> path.toString().endsWith(".xml"))
                    .toList();
        }

        assertThat(mapperFiles).isNotEmpty();
        for (Path mapperFile : mapperFiles) {
            String mapperXml = Files.readString(mapperFile, StandardCharsets.UTF_8);
            assertThat(mapperXml)
                    .as("MyBatis mapper must not use raw ${...} substitution: %s", mapperFile)
                    .doesNotContain("${");
        }
    }
}
