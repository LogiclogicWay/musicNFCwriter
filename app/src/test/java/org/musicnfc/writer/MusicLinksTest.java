package org.musicnfc.writer;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MusicLinksTest {
    @Test public void extractsNeteaseSongIdAndBuildsScheme() {
        String link = "https://y.music.163.com/m/song?id=1915569928";
        assertEquals("1915569928", MusicLinks.extractParameter(MusicLinks.Platform.NETEASE, link));
        assertEquals("orpheus://song/1915569928/?autoplay=true", MusicLinks.buildUri(MusicLinks.Platform.NETEASE, "1915569928"));
    }

    @Test public void extractsUrlsFromChineseShareCaptions() {
        assertEquals("https://qishui.douyin.com/s/Example1/", MusicLinks.normalizeInput(
                "《歌曲》@汽水音乐 https\\://qishui.douyin.com/s/Example1/ 歌手《歌曲》"));
        assertEquals("https://c6.y.qq.com/base/fcgi-bin/u?__=ExampleToken",
                MusicLinks.normalizeInput("歌手《歌曲》https\\://c6.y.qq.com/base/fcgi-bin/u?\\_\\_=ExampleToken @QQ音乐分享"));
        assertEquals("https://163cn.tv/Example2",
                MusicLinks.normalizeInput("歌手的单曲《歌曲》: https://163cn.tv/Example2 (来自@网易云音乐)分享"));
        assertEquals("https://m.kugou.com/share/song.html?chain=ExampleChain",
                MusicLinks.normalizeInput("歌手的单曲《歌曲》https://m.kugou.com/share/song.html?chain=ExampleChain （@酷狗音乐）"));
    }

    @Test public void encodesQqJsonParameter() {
        String result = MusicLinks.buildUri(MusicLinks.Platform.QQ, "000XVfoa2AMAYn");
        assertTrue(result.startsWith("qqmusic://qq.com/media/playSonglist?p=%7B%22song%22"));
        assertTrue(result.contains("songmid%22%3A%22000XVfoa2AMAYn%22"));
        assertTrue(result.endsWith("%22action%22%3A%22play%22%7D"));
    }

    @Test public void extractsKugouChainOnly() {
        assertEquals("Ab_12-xy", MusicLinks.extractParameter(MusicLinks.Platform.KUGOU,
                "https://m.kugou.com/share/song.html?chain=Ab_12-xy"));
        assertNull(MusicLinks.extractParameter(MusicLinks.Platform.KUGOU,
                "https://example.com/song?id=123456"));
        String result = MusicLinks.buildUri(MusicLinks.Platform.KUGOU, "Ab_12-xy");
        assertTrue(result.startsWith("kugou://start.weixin?%7B%22cmd%22%3A303"));
        assertTrue(result.contains("chain%3DAb_12-xy"));
    }

    @Test public void extractsQishuiTrackId() {
        assertEquals("7688990083363162121", MusicLinks.extractParameter(MusicLinks.Platform.QISHUI,
                "https://www.douyin.com/music/track?track_id=7688990083363162121"));
        assertEquals("luna://luna.com/playing?track_id=7688990083363162121",
                MusicLinks.buildUri(MusicLinks.Platform.QISHUI, "7688990083363162121"));
    }
}
