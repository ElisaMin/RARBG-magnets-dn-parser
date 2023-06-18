import org.jetbrains.exposed.dao.id.EntityID
import kotlin.system.exitProcess


fun magnet(url:String)= Magnet.invoke(url)

sealed class Magnet(
    linkInfo:Array<String>
) {
    constructor(link: String) : this(link.also {
        val validateHeader = link.startsWith("magnet:?")
        require(validateHeader) { "not a magnet link" }
    }.split('&', '?').drop(1).toTypedArray())
    protected abstract fun update()
    open val hash = linkInfo.find { it.startsWith("xt=urn:btih:") }?.substring(12)!!

    companion object {
        operator fun invoke(link: String): Magnet? {
            return runCatching { NameInUrl(link) }
                .getOrElse {
                    if (it is NoName.Exception) NoName(link) else throw it
                }
        }
    }
    abstract fun toLink():String

    class NoName(link: String) : Magnet(link) {
        init {
            name = ""
        }
        class Exception : kotlin.Exception()
        override fun update() {}
        override fun toString(): String {
            return "NoName(hash='$hash')"
        }

        override fun toLink()
                = "magnet:?xt=urn:btih:$hash"
    }
    sealed class NameInUrl(linkInfo: Array<String>): Magnet(linkInfo) {
        data class DTO(
            val id: EntityID<Int>? = null,
            override val downloadName: String = "",
            override val hash:String,
        ):NameInUrl(arrayOf("dn=$downloadName", "xt=urn:btih:$hash")) {
            override fun update() {}
            override var name = downloadName
        }
        protected class Skip(nameInMagnet: NameInUrl):Exception("skip ${nameInMagnet.toLink()}")
        protected fun skip(): Nothing = throw Skip(this)
        companion object {
            fun isNoName(link: String) = link.split("dn=").let { list->
                if (list.size<2) null else list[1].split('&')[0].takeIf { it.isNotEmpty() }
            }
            fun isIgnore(link:String) = wrapSkipAble {
                Ignore(link)
            }
            fun isPrefix(link:String) = wrapSkipAble {
                Prefix(link)
            }
            fun isSerial(link:String) = wrapSkipAble {
                Serial(link)
            }
            fun isPlaza(link:String) = wrapSkipAble {
                Plaza(link)
            }
            fun isMovie(link:String) = wrapSkipAble {
                Movie(link)
            }
            fun isSomeMovie(link:String) = wrapSkipAble {
                SomeMovie(link)
            }
            fun isOther(link:String) = wrapSkipAble {
                Other(link)
            }

            private inline fun <T: Magnet> wrapSkipAble(crossinline block: () -> T) = runCatching {
                block()
            }.onFailure {
                if (it !is Skip) throw it
            }.getOrNull()

            operator fun invoke(link: String) =
                wrapSkipAble {
                    Ignore(link)
                }?: wrapSkipAble {
                    Prefix(link)
                }?: wrapSkipAble {
                    Serial(link)
                }?: wrapSkipAble {
                    Plaza(link)
                }?: wrapSkipAble {
                    Movie(link)
                }?: wrapSkipAble {
                    SomeMovie(link)
                }?: wrapSkipAble {
                    Other(link)
                }
        }



        private constructor(link: String) : this(link.also {
            val validateHeader = link.startsWith("magnet:?")
            require(validateHeader) { "not a magnet link" }
        }.split('&', '?').drop(1).toTypedArray())
        abstract override fun update()
        open val downloadName = linkInfo.find { it.startsWith("dn=") }?.drop(3)
            ?.takeIf { it.isNotEmpty() }
            ?:throw NoName.Exception()
        private val link by lazy { "magnet:?xt=urn:btih:$hash&dn=$downloadName" }
        override fun toLink(): String = link

        private fun init(){
            update()
        }
        init {
            init()
        }
        fun String.matchRegexValues(r:Regex)
                = r.find(this)?.groupValues
            ?.takeIf {
                it.first() == this
            }
            ?.drop(1)?.toTypedArray()

        class Other(link: String) : NameInUrl(link) {
            override fun update() {
//                skip()
            }
            override fun toString(): String {
                return "Other(downloadName='$downloadName')"
            }
        }

        // Ignore
        class Ignore(link: String) : NameInUrl(link) {
            companion object {
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
            override fun update() {
                if (ignore.any { downloadName.startsWith(it) })
                    return
                if (ignoreRegexps.any { it.matches(downloadName) })
                    return
                skip()
            }
            override fun toString(): String {
                return "Ignore(downloadName='$downloadName')"
            }
        }
        class Serial(link: String) : NameInUrl(link) {
            companion object {
                val regexps = arrayOf(
                    Regex("""^(\S+)[-.](s\d+e\d+|S\d+E\d+)[-.](\S+)$""") to 1,
                    Regex("""^(\S+)[-.](Season|Sea|Series|S)[s.-]?(\d+)\.(\S+)$""") to 3,
                    Regex("""^(\S+)[-.](\d+[.]?of[.]?\d+)\.(\S+)$""") to 1,
                    Regex("""^(\S+)[-.](E\d+|Part[-.]d+)\.(\S+)$""") to 1,
                    Regex("""^(\S+)[-.](S\d+E?\d*\D*S?\d+E?\d*\D?)\.(\S+)$""") to 1,
                )

            }
            lateinit var serialInfo: String
                private set
            // suffix
            lateinit var suffix: String
                private set
            override fun update() {
                for ((r, i) in regexps) {
                    val re = downloadName.matchRegexValues(r)
                        ?: continue
                    name = re[0]
                    serialInfo = re[i]
                    suffix = re.last()
                    return
                }
                skip()
            }

            override fun toString(): String {
                return "Serial(name='$name', serialInfo='$serialInfo', suffix='$suffix',link='${toLink()}')"
            }
        }
        class Movie(link: String) : NameInUrl(link) {
            companion object {
                val regexps = arrayOf(
                    Regex("""^(\S+)[-.](\d\d\d\d)\.(\S+)$""") to 1,
                )
            }
            lateinit var year: String
                private set
            lateinit var suffix: String
                private set
            override fun update() {
                for ((r, i) in regexps) {
                    val re = downloadName.matchRegexValues(r)
                        ?: continue
                    name = re[0]
                    year = re[i]
                    suffix = re.last()
                    return
                }
                skip()
            }
            override fun toString(): String {
                return "Movie(name='$name', year='$year', suffix='$suffix',link='${toLink()}')"
            }
        }
        class SomeMovie(link: String) : NameInUrl(link) {
            companion object {
                val regex = Regex(
                    """^(\S+)\.(\d\d\d\d?p|REPACK|\S+)\.""" +
                            """(WEB|HDTV|HEVC|AAC|BluRay|WEBRip|BDRip|DVDRip|Bluray|WEBRiP|WEB-DL|BRRiP)\.(.+)$"""
                )
            }

            //分辨率
            lateinit var resolution: String
                private set

            //解码方式
            lateinit var codec: String
                private set
            lateinit var suffix: String
                private set

            override fun update() {
                val re = downloadName.matchRegexValues(regex)
                    ?:skip()
                name = re[0]
                resolution = re[2]
                codec = re[3]
                suffix = re.last()
                runCatching {
                    suffix
                    resolution
                    suffix.takeIf { it.isNotBlank() }!!
                }.onFailure {
                    skip()
                }
            }
        }
        class Plaza(link: String) : NameInUrl(link) {
            companion object {
                val regexps = arrayOf(
                    Regex("""^(\S+)[-.]Update.v(\S+)-(CODEX|PLAZA)$""") to true,
                    Regex("""^(\S+)[-.](CODEX|PLAZA)$""") to false,
                )
            }

            var version: String? = null
                private set
            var isUpdate:Boolean? = null
                private set

            override fun update() {
                for ((r, title) in regexps) {
                    val re = downloadName.matchRegexValues(r)
                        ?: continue
                    name = re[0].takeIf { it.isNotBlank() }!!
                    println(toLink())
                    isUpdate = title
                    if (isUpdate!!) {
                        version = re[1]
                        if (version?.run {
                                replace(".","").takeIf {
                                    it.count { char -> char.isDigit() } > 1
                                }
                            }==null) {
                            println(toLink())
                            println(re.joinToString())
                            exitProcess(3)
                        }
                    }
                    return
                }
                skip()
            }

            override fun toString(): String {
                return "Plaza(name='$name', version=$version, link='${toLink()}')"
            }
        }



        class Prefix(link: String) : NameInUrl(link) {
            val prefix = prefixs.find { downloadName.startsWith(it) }
                ?: skip()

            override fun update() {
                prefix
            }

            companion object {
                val prefixs = arrayOf("MP3","BBC","UFC.")
            }
        }

    }


    open lateinit var name: String
        protected set



}
