package com.aiasti.tiger.application.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.aiasti.tiger.domain.model.TrackTags;

/**
 * 工作区：当前会话内的一组曲目与跳过项（BIZ-006、BIZ-017，无数据库、不跨会话）。
 *
 * <p>桌面端为单用户单会话场景，方法以 synchronized 保证一致；清空/关闭即丢弃。
 * 曲目顺序为导入顺序；界面上的手动排序仅在渲染层维护，不写入此处（BIZ-017）。
 */
@Component
public class Workspace {

    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final List<SkippedFile> skipped = new ArrayList<>();
    private long sequence;

    /** 是否已包含该文件（按规范化绝对路径去重，Windows 下不区分大小写）。 */
    public synchronized boolean contains(Path path) {
        return tracks.containsKey(key(path));
    }

    /** 新增导入曲目；若已存在则不覆盖（保留首次快照，FR-1）。 */
    public synchronized Track addImported(Path path, TrackTags tags, long durationSeconds) {
        String key = key(path);
        Track existing = tracks.get(key);
        if (existing != null) {
            return existing;
        }
        Track track = Track.imported(++sequence, path, tags, durationSeconds);
        tracks.put(key, track);
        return track;
    }

    /** 追加本次导入的跳过项。 */
    public synchronized void addSkipped(List<SkippedFile> items) {
        skipped.addAll(items);
    }

    /**
     * 将指定曲目移出工作区（BIZ-015）。
     *
     * <p>仅移除会话内状态（含首次快照与未提交编辑），不改动磁盘文件。
     *
     * @return 实际移除的数量
     */
    public synchronized int remove(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<String> keys = tracks.entrySet().stream()
                .filter(entry -> ids.contains(entry.getValue().id()))
                .map(Map.Entry::getKey)
                .toList();
        keys.forEach(tracks::remove);
        return keys.size();
    }

    /** 按曲目 id 取内嵌封面。 */
    public synchronized Optional<CoverData> cover(long id) {
        return tracks.values().stream()
                .filter(track -> track.id() == id)
                .findFirst()
                .flatMap(Track::cover);
    }

    /** 按曲目 id 查找（写回按提交清单逐项处理，DEV-003）。 */
    synchronized Optional<Track> find(long id) {
        return tracks.values().stream()
                .filter(track -> track.id() == id)
                .findFirst();
    }

    public synchronized WorkspaceView view() {
        List<TrackView> views = tracks.values().stream().map(Track::toView).toList();
        return new WorkspaceView(views, List.copyOf(skipped));
    }

    /** 清空工作区：释放快照、编辑态与会话记录（BIZ-006）。 */
    public synchronized void clear() {
        tracks.clear();
        skipped.clear();
        sequence = 0;
    }

    private static String key(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }
}
