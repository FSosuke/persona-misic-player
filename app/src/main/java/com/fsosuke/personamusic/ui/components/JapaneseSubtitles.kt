package com.fsosuke.personamusic.ui.components

/**
 * Small Japanese subtitles under English headings, the way Persona's Japanese menus pair
 * words. Lookup is by heading text (case-insensitive); unknown headings get none.
 */
object JapaneseSubtitles {
    private val map = mapOf(
        // Screens
        "home" to "ホーム",
        "library" to "ライブラリ",
        "playlist" to "プレイリスト",
        "playlists" to "プレイリスト",
        "customize" to "カスタマイズ",
        "stats" to "統計",
        "downloads" to "ダウンロード",
        "confidants" to "コープ",
        "hidden" to "非表示",
        "sound" to "サウンド",
        "calendar" to "カレンダー",
        "social stats" to "人間パラメータ",
        "favourites" to "お気に入り",
        "now playing" to "再生中",
        "sign in" to "ログイン",
        "search" to "検索",
        "radio" to "ラジオ",
        "on air" to "放送中",
        "your stations" to "マイ局",
        "nationwide" to "全国",
        "yt music" to "YouTubeミュージック",
        // Home & sections
        "recent" to "最近の再生",
        "most played" to "よく聴く曲",
        "song log" to "ソングログ",
        "speed dial" to "スピードダイヤル",
        "for you" to "あなたへ",
        "japan" to "日本",
        "top result" to "トップ結果",
        "songs" to "曲",
        "artists" to "アーティスト",
        "albums" to "アルバム",
        "top songs" to "トップソング",
        "top songs japan" to "日本のトップソング",
        "top artists" to "トップアーティスト",
        "your library" to "あなたのライブラリ",
        "in progress" to "ダウンロード中",
        "new from your confidants" to "コープの新作",
        "smart playlists" to "スマートプレイリスト",
        // Customize
        "theme" to "テーマ",
        "youtube music" to "YouTubeミュージック",
        "backgrounds" to "背景",
        "recommendations" to "おすすめ",
        "updates & backup" to "更新とバックアップ",
        "volume" to "音量",
        "equalizer" to "イコライザー",
        // Player
        "queue" to "キュー",
        "playing" to "再生中",
        "next" to "次へ",
        "take your time" to "ゆっくりでいい",
    )

    fun of(english: String): String? = map[english.trim().lowercase()]
}
