import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.BufferedWriter
import java.sql.DriverManager
import kotlin.sequences.Sequence


data class MagnetInfo(
    val hash: String,
    val dn: String,
    var year: Int?  = null ,
    var codec: String?  = null ,
    var quality: String?  = null ,
    var episode: String?  = null ,
)

context(Transaction)
fun MagnetLink.saveToDB(
    out: (String) -> Unit = ::println,
){
    val link = link
    val hash = hash.toString()
    val parseableName = name ?: return
    val dto = MagnetInfo(
        hash = hash,
        dn = parseableName.toString(),
    )
    parseableName.run {
        require(getPlazaInfo() == null)
        var contentName = ""
        fun doIfContentNameIsBlank(block:()->String) {
            if (contentName.isBlank()) {
                contentName = block()
            }
        }
        year?.let { (name,years) ->
            doIfContentNameIsBlank { name }
            dto.year = years.toIntOrNull()
        }
        serialInfo?.let { (name,serialInfo) ->
            doIfContentNameIsBlank { name }
            dto.episode = serialInfo.takeIf { it.trim().isNotEmpty() }
        }
        videoInfo?.let { (name,quality,codec) ->
            doIfContentNameIsBlank { name }
            dto.quality = quality
            dto.codec = codec
        }
        System.err.println(hash)
        val id = Magnets.insert {

            Magnets.select {
                Magnets.hash eq hash
            }.firstOrNull()?.let {
                require(false) {
                    "hash $hash already exists dn: ${it[Magnets.dn]} this: ${parseableName}"
                }
            }
            it[Magnets.hash] = hash
            it[Magnets.dn] = parseableName.toString()
            it[Magnets.year] = dto.year
            it[Magnets.codec] = dto.codec
            it[Magnets.quality] = dto.quality
            it[Magnets.episode] = dto.episode
        }.let {
            out(link)
            out("  name: $contentName")
            out("  hash: ${it[Magnets.hash]}")
            out("  dn: ${it[Magnets.dn]}")
            it[Magnets.year]?.let { year ->
                out("  year: $year")
            }
            it[Magnets.codec]?.let { codec ->
                out("  codec: $codec")
            }
            it[Magnets.quality]?.let { quality ->
                out("  quality: $quality")
            }
            it[Magnets.episode]?.let { episode ->
                out("  episode: $episode")
            }
            it[Magnets.id].value
        }
        if (contentName.isNotBlank() && contentName.isNotEmpty()) {
            contentName = contentName.lowercase().replace("."," ")
            Names.run {
                select { name eq contentName }.firstOrNull()
                    ?.get(this.id)?.value
                    ?:insertAndGetId {
                        it[name] = contentName
                    }.value
            }.let { nameId ->
                println(nameId to id )
                MagnetNames.insert {
                    it[name_id] = nameId
                    it[magnet_id] = id
                }
            }
        }


    }
}

object a {
    @JvmStatic
    var i = 0
}

fun db(filePrefix:String): Database {
    return Database.connect(getNewConnection = {
        DriverManager.getConnection("jdbc:sqlite:$filePrefix.db")
    }).also {db ->
        transaction(db) {
            addLogger(StdOutSqlLogger)
            addLogger(Slf4jSqlDebugLogger)
            SchemaUtils.create(Magnets,Names,MagnetNames)
        }
    }
}



fun displayingProgress(maxLines:Int) {
    val max = maxLines.toFloat()
    var percent = 0.0f
    while (true) {
        Thread.sleep(1000)
        val line = a.i.toFloat()
        //percent
        val leastPresent = percent
        percent = (line / max) * 100
        print("\r${"%.2f".format(percent)}% lines: ${a.i}/$maxLines ")
        //complain second time
        val spends = percent - leastPresent
        val last = 100 - percent
        val second = last / spends
        val left = when {
//                second > 60 * 60 * 24 -> {
//                    val days = second / (60 * 60 * 24)
//                    "${"%.2f".format(days)} days"
//                }
            second > 60 * 60 -> {
                val hours = second / (60 * 60)
                "${"%.2f".format(hours)} hours"
            }
            second > 60 -> {
                val minutes = second / 60
                "${"%.2f".format(minutes)} minutes"
            }
            else -> {
                "${"%.2f".format(second)} seconds"
            }
        }
        print("left: $left")

//            println("${
//                // save two digits
//                "%.2f".format((line / max) * 100)
//            }% lines: ${a.i}/$maxLines")
        if (a.i >= maxLines) {
            break
        }
    }
}
suspend fun Sequence<String>.toMagnetsDB(
    db:Database,
    yaml: BufferedWriter? = null,
    chunk: Int = 5000,
) = this
    .map { MagnetLink(MagnetLink(it).link) }
    .toSet()
    .sortedBy { it.hash.toString() }
    .chunked(chunk)
//    .asFlow()
//    .flowOn(Dispatchers.Default)
//    .map { it.map { link -> MagnetLink(link) } }
//    .flowOn(Dispatchers.IO)
    .onEach { links ->
        transaction(db) {
            links.forEach {
                it.saveToDB()
                a.i++
            }
            yaml?.flush()
        }
    }
    .toList().let {
//        it.collect()
        yaml?.flush()
        yaml?.close()
    }
