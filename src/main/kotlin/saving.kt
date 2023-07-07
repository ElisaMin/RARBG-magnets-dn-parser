import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.DriverManager
import kotlin.sequences.Sequence

context(Transaction)
fun Collection<MagnetInfoDTO>.save(names: Collection<Pair<Int, String>>) =  Magnets.batchInsert(this) {
    this[Magnets.hash] = it.hash
    this[Magnets.dn] = it.dn
    this[Magnets.year] = it.year
    this[Magnets.codec] = it.codec
    this[Magnets.quality] = it.quality
    this[Magnets.episode] = it.episode
    this[Magnets.name_id] = names.first { (_,name) -> name == it.name }.first
}.map {
    it[Magnets.id].value to it[Magnets.hash]
}.map { (magnetId, hash) ->
    val name = find { hash == it.hash }!!.name
    val nameId = names.first { it.second == name }.first
    magnetId to nameId
}


context(Transaction)
fun Collection<MagnetInfoDTO>.saveNames(): List<Pair<Int, String>> {
    val named = this.groupBy { it.name }
    return Names.batchInsert(named.keys) {
        this[Names.name] = it
    }.map {
        it[Names.id].value to it[Names.name]
    }
}

data class MagnetInfoDTO(
    val hash: String,
    val dn: String,
    var year: Int?  = null ,
    var codec: String?  = null ,
    var quality: String?  = null ,
    var episode: String?  = null ,
    var name:String = ""
)

fun MagnetLink.toDTO(): MagnetInfoDTO {
    val dto = MagnetInfoDTO(
        hash = hash.toString(),
        dn = name!!.toString() ,
    )
    name?.run {
        require(getPlazaInfo() == null)
        fun doIfContentNameIsBlank(block:()->String) {
            if (dto.name.isBlank()) {
                dto.name = block().trim()
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
            name?.let { s -> doIfContentNameIsBlank { s } }
            dto.quality = quality
            dto.codec = codec
        }
        dto.name = dto.name.trim().replace("."," ").trim().lowercase()
    }
    require(dto.name.isNotEmpty()) {
        "error: link ${this.link} : name is empty for $name ${this.name} this $dto "
    }

    return dto

}
fun db(filePrefix:String): Database {
    return Database.connect(getNewConnection = {
        DriverManager.getConnection("jdbc:sqlite:$filePrefix.db")
    }).also {db ->
        transaction(db) {
            addLogger(StdOutSqlLogger)
            addLogger(Slf4jSqlDebugLogger)
            SchemaUtils.create(Magnets,Names)
        }
    }
}
fun <T> parseFailed(f:()->String,block:()->T) =
    runCatching {
        block()
    }.onFailure {
        System.err.println("skip: `${f()}` reason: ${it.message}")
    }.getOrNull()


fun Sequence<String>.toMagnetsDB(
    db:Database,
) {
    val converts = this
        .mapNotNull { parseFailed({it}) {
            MagnetLink(MagnetLink(it).link).link
        } }
        .toSet()
        .mapNotNull { parseFailed({it}) {
            MagnetLink(it).toDTO()
        } }
        .groupBy { it.year }
        .asSequence()
        .sortedBy { it.key }
        .toList()
        .asReversed()
        .flatMap {(_,infoDTOS) -> infoDTOS
            .groupBy { it.name }
            .asSequence()
            .sortedBy {(names,_) -> names }
//            .flatMap {
//                it.value.groupBy { it.episode }
//                    .asSequence()
//                    .sortedBy { it.key }
//            }
            .flatMap { (_,dtos) -> dtos }
        }
    var time = System.currentTimeMillis()
    transaction(db) {

        println("saving names")
        converts.saveNames()
    }.let {names ->
        println("names count: ${names.size}, time: ${System.currentTimeMillis() - time}")
        println("saving magnets")
        converts.chunked(3000).forEach { dtoList ->
            transaction(db) {
                dtoList.save(names)
            }
            println("time: ${System.currentTimeMillis() - time}")
            time = System.currentTimeMillis()
        }

    }

}

