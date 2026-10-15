package com.aiasti.tiger.application;

import com.aiasti.tiger.domain.port.TagAccessPort;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 从用户选择的路径发现待导入的音频文件（FR-1、BIZ-014）。
 *
 * <p>目录按 {@code recursive} 决定是否递归子目录；非音频扩展名的文件静默忽略，
 * 不产生跳过项。结果按路径排序，保证导入顺序稳定。
 */
@Component
public class AudioFileDiscovery {

    private final TagAccessPort tagAccess;

    public AudioFileDiscovery(TagAccessPort tagAccess) {
        this.tagAccess = tagAccess;
    }

    public List<Path> discover(List<Path> inputs, boolean recursive) {
        Set<Path> found = new LinkedHashSet<>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) {
                collectFromDirectory(input, recursive, found);
            } else if (Files.isRegularFile(input) && tagAccess.supports(input)) {
                found.add(normalize(input));
            }
        }
        List<Path> result = new ArrayList<>(found);
        result.sort(Comparator.comparing(Path::toString));
        return result;
    }

    private void collectFromDirectory(Path directory, boolean recursive, Set<Path> found) {
        try (Stream<Path> stream = recursive ? Files.walk(directory) : Files.list(directory)) {
            stream.filter(Files::isRegularFile)
                    .filter(tagAccess::supports)
                    .map(AudioFileDiscovery::normalize)
                    .forEach(found::add);
        } catch (IOException e) {
            // 目录不可读：忽略该目录，不阻断整批导入
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }
}
