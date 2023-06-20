import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.DriverManager
import kotlin.sequences.Sequence


context(Transaction)
fun Collection<MagnetInfoDTO>.saveDB() {
    val named = this.groupBy { it.name }
    val names = Names.batchInsert(named.keys) {
        this[Names.name] = it
    }.map {
        it[Names.id].value to it[Names.name]
    }
    Magnets.batchInsert(this) {
        this[Magnets.hash] = it.hash
        this[Magnets.dn] = it.dn
        this[Magnets.year] = it.year
        this[Magnets.codec] = it.codec
        this[Magnets.quality] = it.quality
        this[Magnets.episode] = it.episode
    }.map {
        it[Magnets.id].value to it[Magnets.hash]
    }.map { (magnetId, hash) ->
        val name = find { hash == it.hash }!!.name
        val nameId = names.first { it.second == name }.first
        magnetId to nameId
    }.let {
        MagnetNames.batchInsert(it) {(magnetID, nameID) ->
            this[MagnetNames.magnet_id] = magnetID
            this[MagnetNames.name_id] = nameID
        }
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
            SchemaUtils.create(Magnets,Names,MagnetNames)
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
            .flatMap { (_,dtos) -> dtos }
        }
    transaction(db) {
        converts.saveDB()
    }

}

