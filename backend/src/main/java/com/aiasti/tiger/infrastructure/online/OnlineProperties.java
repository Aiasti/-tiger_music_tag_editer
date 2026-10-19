package com.aiasti.tiger.infrastructure.online;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 在线服务配置（FR-5、FR-6、DEV-004）。
 *
 * <p>统一从配置读取；AcoustID 内置用户指定的项目专用默认 Key，可用环境变量覆盖。
 */
@ConfigurationProperties(prefix = "app.online")
public class OnlineProperties {

    private final MusicBrainz musicbrainz = new MusicBrainz();
    private final AcoustId acoustid = new AcoustId();
    private final CoverArt coverArt = new CoverArt();
    private final Itunes itunes = new Itunes();
    private final LrcCx lrcCx = new LrcCx();
    private final Fpcalc fpcalc = new Fpcalc();
    private final Lyrics lyrics = new Lyrics();

    public MusicBrainz getMusicbrainz() {
        return musicbrainz;
    }

    public AcoustId getAcoustid() {
        return acoustid;
    }

    public CoverArt getCoverArt() {
        return coverArt;
    }

    public Itunes getItunes() {
        return itunes;
    }

    public LrcCx getLrcCx() {
        return lrcCx;
    }

    public Fpcalc getFpcalc() {
        return fpcalc;
    }

    public Lyrics getLyrics() {
        return lyrics;
    }

    /** MusicBrainz：只读接口无需 Key，但必须携带可识别应用的 User-Agent。 */
    public static class MusicBrainz {
        private String baseUrl = "https://musicbrainz.org/ws/2";
        private String userAgent = "TigerMusicTagEditor/0.1.0 (master8@live.cn)";
        private int requestsPerSecond = 1;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }
    }

    /** AcoustID：需要应用 API Key，限制 3 请求/秒。 */
    public static class AcoustId {
        private String baseUrl = "https://api.acoustid.org/v2";
        private String apiKey = "";
        private int requestsPerSecond = 3;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }
    }

    /** Cover Art Archive：按发行组/发行版取正面封面。 */
    public static class CoverArt {
        private String baseUrl = "https://coverartarchive.org";
        private int requestsPerSecond = 1;
        private int timeoutSeconds = 20;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** iTunes Search API：按专辑/艺术家/标题检索封面。 */
    public static class Itunes {
        private String baseUrl = "https://itunes.apple.com";
        private List<String> countries = List.of("US", "JP", "HK");
        private int requestsPerSecond = 1;
        private int timeoutSeconds = 15;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public List<String> getCountries() {
            return countries;
        }

        public void setCountries(List<String> countries) {
            this.countries = countries == null || countries.isEmpty()
                    ? List.of("US", "JP", "HK") : countries;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** lrcapi 公开/自建服务：歌词与封面共用 /jsonapi。 */
    public static class LrcCx {
        private String baseUrl = "https://api.lrc.cx";
        private String userAgent = "TigerMusicTagEditor/0.5.1 (master98@live.cn)";
        private boolean enabled = true;
        private int requestsPerSecond = 1;
        private int timeoutSeconds = 10;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** Chromaprint CLI；默认从 PATH 查找 fpcalc。 */
    public static class Fpcalc {
        private String path = "fpcalc";
        private int timeoutSeconds = 30;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /** 歌词源：首版接入 LRCLIB。 */
    public static class Lyrics {
        private final Lrclib lrclib = new Lrclib();

        public Lrclib getLrclib() {
            return lrclib;
        }
    }

    public static class Lrclib {
        private String baseUrl = "https://lrclib.net/api";
        private String userAgent = "TigerMusicTagEditor/0.1.0 (master8@live.cn)";
        /** 注册的客户端应用 ID（公开值，可随客户端分发）；配置后按更高额度限速。 */
        private String appId = "";
        /** LRCLIB 未公布明确配额；批量检索按 2 请求/秒串行，降低 503 概率。 */
        private int requestsPerSecond = 2;
        private int timeoutSeconds = 8;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
