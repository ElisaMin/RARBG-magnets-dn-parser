@JvmInline
value class MagnetLink(
    private val data:Pair<MagnetHash,ParseableName?>
) {
    constructor(link: String):this(
        MagnetHash(link
            .substringAfter("magnet:?xt=urn:btih:")
            .substringBefore("&")
            .takeIf { it.length.let { size -> size == 40 || size == 32 } }!!
        ) to link
            .substringAfter("&dn=")
            .substringBefore("&")
            .takeIf { it.isNotEmpty() }
            ?.let { ParseableName(it) }
    )
    val name get() = data.second
    val hash get() = data.first
    val link get() = "magnet:?xt=urn:btih:${hash}&dn=${name?:""}"
}



@JvmInline
value class MagnetHash (
    private val hash:String
) {
    override fun toString() = hash
}

@JvmInline
value class ParseableName (
    private val name:String
) {
    override fun toString(): String
            = name
    init {
        require(name.isNotEmpty())
    }
    private operator fun String.get(r:Regex)
            = r.find(this)?.groupValues?.takeIf {
        it.first() == this
    }?.drop(1)
        ?.takeIf { it.isNotEmpty() }
        ?.toTypedArray()

    // name version suffix
    fun getPlazaInfo():Triple<String,String?,String>? {
        for ((r, hasVersion) in Plaza.regexps) {
            val result = name[r] ?: continue
            val name = result[0]
            var i = 1
            val version = if (hasVersion) result[i++] else null
            val suffix = result[i]
            return Triple(name, version, suffix)
        }
        return null
    }
    val isIgnore:Boolean get()  {
        Ignore.ignore.forEach {
            if (name == it) return true
        }
        Ignore.ignoreRegexps.forEach {
            if (it.matches(name)) return true
        }
        return false
    }
    fun loggedPrefix():String? {
        prefixes.forEach {
            if (name.startsWith(it)) return it
        }
        return null
    }
    val year get() = name[searchYear]
        ?.let {(name,century,year,suffix) -> Triple(name, century+year,suffix) }

    // name quality codec suffix
    val videoInfo:Array<String>? get() {
        val result = name[VideoInfo.regex] ?: return null
        return result
    }
    // name season info
    val serialInfo:Array<String>? get() {
        for ((r, i) in Serial.regexps) {
            val result = name[r] ?: continue
            val name = result[0]
            val info = when(i) {
                Int.MAX_VALUE -> result[1]+result[2]
                else -> result[i]
            }
            return arrayOf(name, info, result.last())
        }
        return null
    }



    companion object {
        val searchYear = Regex("""^(\S+)[-.](18|19|20)(\d\d)\.(\S+)$""")
        val prefixes = arrayOf("MP3","BBC","UFC.")
        object Plaza {
            val regexps = arrayOf(
                Regex("""^(\S+)[-.]Update.v(\S+)-(CODEX|PLAZA)$""") to true,
                Regex("""^(\S+)[-.](CODEX|PLAZA)$""") to false,
            )
        }
        object Ignore {
            val ignore = setOf(
                "Twistys.com",
                "AmKingdom.com",
                "Tushy.Raw",
                "National.Geographic",
                "BananaFever",
                "ReyaReign.Presents",
                "Arabelles.Playground.Presents",
            ).toTypedArray()
            val ignoreRegexps = arrayOf(
                Regex("""^(\S+)[-.](\d\d\.\d\d\.\d\d)\.(\S+)$"""),
            )
        }
        object VideoInfo {
            val regex = Regex(
                """^(\S+)\.(\d\d\d\d?p|REPACK)[.\S]+""" +
                        """(WEB|HDTV|HEVC|AAC|BluRay|WEBRip|BDRip|DVDRip|Bluray|WEBRiP|WEB-DL|BRRiP)\.(.+)$"""
            )
        }
        object Serial {
            val regexps = arrayOf(
                Regex("""^(\S+)[-.](s\d+e\d+|S\d+E\d+)[-.](\S+)$""") to 1,
                Regex("""^(\S+)[-.](Season|Sea|Series|S)[s.-]?(\d+)\.(\S+)$""") to Int.MAX_VALUE,
                Regex("""^(\S+)[-.](\d+[.]?of[.]?\d+)\.(\S+)$""") to 1,
                Regex("""^(\S+)[-.](E\d+|Part[-.]d+)\.(\S+)$""") to 1,
                Regex("""^(\S+)[-.](S\d+E?\d*\D*S?\d+E?\d*\D?)\.(\S+)$""") to 1,
            )
        }
    }
}

val trackers = """
    udp://tracker.opentrackr.org:1337/announce
    udp://opentracker.i2p.rocks:6969/announce
    udp://tracker.openbittorrent.com:6969/announce
    http://tracker.openbittorrent.com:80/announce
    udp://open.demonii.com:1337/announce
    udp://open.stealth.si:80/announce
    udp://exodus.desync.com:6969/announce
    udp://tracker.torrent.eu.org:451/announce
    udp://tracker.moeking.me:6969/announce
    udp://tracker1.bt.moack.co.kr:80/announce
    udp://tracker.bitsearch.to:1337/announce
    udp://tracker.auctor.tv:6969/announce
    udp://tracker.altrosky.nl:6969/announce
    udp://p4p.arenabg.com:1337/announce
    udp://movies.zsw.ca:6969/announce
    udp://explodie.org:6969/announce
    https://tracker.tamersunion.org:443/announce
    https://tr.burnabyhighstar.com:443/announce
    http://open.acgnxtracker.com:80/announce
    http://montreal.nyap2p.com:8080/announce
""".trimIndent().lineSequence().map { it.trim() }
    .toList()
    .toTypedArray()