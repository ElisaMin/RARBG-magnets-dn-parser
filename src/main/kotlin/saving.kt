import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.BufferedWriter
import java.sql.DriverManager
import kotlin.sequences.Sequence

val prefixes by lazy {
    transaction {
        Prefixes.selectAll().associate {
            it[Prefixes.id].value to it[Prefixes.prefix]
        }
    }
}

context(Transaction)
fun ParseableName.saveToDBAndYaml(
//    id:String,
    id:String,
    out: (String) -> Unit = ::println,
) {
    var contentName = ""
    if(isIgnore) {
        Ignores.insert {
            it[Ignores.magnet_id] = id
        }
        out("  ignore: true")
        return
    }
    loggedPrefix()?.let { prefix ->
        val prefixId = prefixes
            .map { it.key to it.value }
            .find {
                it.second == prefix
            }!!.first
        PrefixSerial.insert {
            it[magnet_id] = id
            it[prefix_id] = prefixId
        }
        out("  prefix: $prefix")
        return
    }
    getPlazaInfo()?.let { (name, version, suffix) ->
        contentName = name
        if (version!=null) {
            val r = Plaza.select {
                Plaza.name like "$name%"
            }.toList()
            val plaza_id = when (r.size) {
                1 -> r.first()[Plaza.id].value
                0 ->  Plaza.insertAndGetId {
                    it[magnet_id] = id
                    it[Plaza.name] = name
                }.value
                else -> r.run {
                    find {
                        it[Plaza.name] == name
                    }?:first()
                }[Plaza.id].value
            }
            PlazaUpdate.insert {
                it[PlazaUpdate.magnet_id] = id
                it[PlazaUpdate.plaza_id] = plaza_id
                it[PlazaUpdate.update] = version
            }.let {
                out("  plazaUpdate:")
                out("    name: $name")
                out("    version: ${it[PlazaUpdate.update]}")
            }
        } else {
            //insert
            Plaza.insert {
                it[magnet_id] = id
                it[Plaza.name] = name
            }.let {
                out("  plaza:")
                out("    name: $name")
            }
        }
    }
    serialInfo?.let { (name,serialInfo) ->
        contentName = contentName.takeIf { it.isNotBlank() } ?: name
        Serial.insert {
            it[Serial.magnet_id] = id
            it[Serial.info] = serialInfo
        }.let {
            out("  serial:")
            out("    name: $name")
            out("    info: ${it[Serial.info]}")
        }
    }
    year?.let { (name,years) ->
        contentName = contentName.takeIf { it.isNotBlank() } ?: name
        val year = years.toIntOrNull() ?: return
        MovieYear.insert {
            it[MovieYear.magnet_id] = id
            it[MovieYear.year] = year
            it[MovieYear.name] = name
        }.let {
            out("  year:")
            out("    name: ${it[MovieYear.name]}")
            out("    year: ${it[MovieYear.year]}")
        }
    }
    videoInfo?.let { (name,quality,codec) ->
        contentName = contentName.takeIf { it.isNotBlank() } ?: name
        MovieType.insert {
            it[MovieType.magnet_id] = id
            it[MovieType.quality] = quality
            it[MovieType.codec] = codec
        }.let {
            out("  movieType:")
            out("    name: $name")
            out("    quality: ${it[MovieType.quality]}")
            out("    codec: ${it[MovieType.codec]}")
        }
    }
    if (contentName.isNotBlank() && contentName.isNotEmpty()) {
        contentName = contentName.lowercase().replace("."," ")
        Names.run {
            select {
                name eq contentName
            }.firstOrNull()?.get(this.id)?.value ?:insertAndGetId {
                it[name] = contentName
            }.value
        }.let { nameId ->
            MagnetNames.insert {
                it[name_id] = nameId
                it[magnet_id] = id
            }
        }
    }
    out("  name: $contentName")
}
context(Transaction)
fun MagnetLink.saveToDB(
    out: (String) -> Unit = ::println,
){
    val link = link
    val hash = hash.toString()
    val parseableName = name ?: return
    out(link)
    val id = Magnets.select {
        Magnets.hash eq hash
    }.firstOrNull()?.let {
        out("  hash: ${it[Magnets.hash]}")
//        println("  - already in db")
        it[Magnets.hash]
    }?:run {
        Magnets.insert { table ->
            table[Magnets.hash] = hash
            table[Magnets.dn] = parseableName.toString()
        }.let {
            out("  hash: ${it[Magnets.hash]}")
            it[Magnets.hash]
        }
    }
    parseableName.saveToDBAndYaml(
        id = id,
        out = out
    )
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
//            addLogger(Slf4jSqlDebugLogger)
            SchemaUtils.create(Magnets, Ignores, MovieYear, MovieType, Serial, Plaza, PlazaUpdate, Prefixes, PrefixSerial,Names,MagnetNames)
            val prefixes = Prefixes.selectAll()
                .map { it[Prefixes.prefix] }
                .toSet()

            ParseableName.prefixes.filter { !prefixes.contains(it) }.forEach {str->
                Prefixes.insert {
                    it[prefix] = str
                }
            }
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
) = this.chunked(chunk)
    .asFlow()
    .flowOn(Dispatchers.Default)
    .map { it.map { link -> MagnetLink(link) } }
    .flowOn(Dispatchers.IO)
    .onEach { links ->
        transaction(db) {
            links.forEach {
                it.saveToDB { line -> yaml?.appendLine(line) }
                a.i++
            }
            yaml?.flush()
        }
    }.let {
        it.collect()
        yaml?.flush()
        yaml?.close()
    }
