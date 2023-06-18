import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import kotlin.system.exitProcess
import Magnet.NameInUrl.*
import Magnet.NameInUrl.Companion.isIgnore
import Magnet.NameInUrl.Companion.isMovie
import Magnet.NameInUrl.Companion.isPlaza
import Magnet.NameInUrl.Companion.isPrefix
import Magnet.NameInUrl.Companion.isSerial
import Magnet.NameInUrl.Companion.isSomeMovie

fun Flow<String>.parseMagnet() = flowOn(Dispatchers.Default)
    .mapNotNull { Magnet(it)?: kotlin.run {
        println(it)
        null
    } }
fun Flow<String>.mapNotInDB() = flowOn(Dispatchers.Unconfined)
    .map { magnetLink ->
        //magnet:?xt=urn:btih:${hash}&
        val hash = magnetLink.substringAfter("xt=urn:btih:")
            .substringBefore("&")
        val dn = magnetLink.substringAfter("dn=")
            .substringBefore("&", "")
        hash to dn
    }.filterNot { (hash, _) ->
        transaction {
            Magnets.select {
                Magnets.hash eq hash
            }.toList().size == 1
        }
    }
fun Flow<Pair<String,String>>.saveHashAndDnToDB() = flowOn(Dispatchers.IO)
    .mapNotNull { (hash, dn) ->
        val r = transaction {
            Magnets.insert {
                it[Magnets.hash] = hash
                it[Magnets.dn] = dn
            }
        }

        val id = r[Magnets.id]
        val dbHash = r[Magnets.hash]
        val dbDn = r[Magnets.dn]

        runCatching {
            DTO(id,hash = dbHash, downloadName =  dbDn)
        }.onFailure {
            if (it !is Magnet.NoName.Exception)
                throw it
        }.getOrNull()
    }
fun Flow<DTO>.tagOfIgnore() = flowOn(Dispatchers.IO)
    .onEach {magnet ->
        require(magnet.id?.value!=null)
        val link = magnet.toLink()
        isIgnore(link) ?: return@onEach
        transaction {
            Ignores.insert {
                it[magnet_id] = magnet.id!!.value
            }
        }
    }
fun Flow<DTO>.tagOfPlaza() = flowOn(Dispatchers.IO)
    .onEach {dto->
        require(dto.id!=null)
        val id = dto.id
        val link = dto.toLink()
        val magnet = isPlaza(link) ?: return@onEach
        transaction {
            if (magnet.isUpdate!!) {
                val plaza = Plaza.select {
                    Plaza.name like "${magnet.name}%"
                }.let {
                    var q = it.toList()
                    if (q.isEmpty()) {
                        Plaza.insert {
                            it[name] = magnet.name
                            it[magnet_id] = id
                        }
                        q = Plaza.select {
                            Plaza.name like "${magnet.name}%"
                        }.toList()
                    }
                    require(q.isNotEmpty()) {
                        "magnet is not in db \n ${magnet.toLink()} \n $magnet "
                    }
                    require(q.size == 1) {
                        println(magnet.toString())
                        println(Plaza.selectAll().joinToString {
                            it[Plaza.name]+" "+it[Plaza.id]+" "
                        })
                        exitProcess(q.size)
                        q.joinToString {
                            it[Plaza.name]+" "+it[Plaza.id]+" "+ magnet.toLink()
                        }
                    }
                    q.first()[Plaza.id]
                }
                PlazaUpdate.insert {
                    it[magnet_id] = id
                    it[plaza_id] = plaza
                    it[update] = magnet.version!!
                }
            } else {
                Plaza.select {
                    Plaza.name eq magnet.name
                }.let { query ->
                    if (query.toList().isEmpty()) {
                        Plaza.insert {
                            it[magnet_id] = id
                            it[name] = magnet.name
                        }
                    } else {
                        Plaza.update {
                            Plaza.id eq query.first()[Plaza.id]
                            it[magnet_id] = id
                        }
                    }
                }
            }
        }
    }

fun Flow<DTO>.tagOfMovie() = flowOn(Dispatchers.IO)
    .onEach {dto->
        require(dto.id!=null)
        val id = dto.id
        val link = dto.toLink()
        val magnet = isMovie(link) ?: return@onEach
        transaction {
            MovieYear.insert {
                it[magnet_id] = id
                it[year] = magnet.year.toInt()
                it[name] = magnet.name
            }
            isSomeMovie(link)?.let { magnet->
                MovieType.insert {
                    it[magnet_id] = id
                    it[codec] = magnet.codec
                    it[quality] = magnet.resolution
                }
            }?: isSomeMovie(link)?.let { magnet ->
                MovieType.insert {
                    it[magnet_id] = id
                    it[codec] = magnet.codec
                    it[quality] = magnet.resolution
                }
            }
        }

    }
fun Flow<DTO>.tagOfPrefix() = flowOn(Dispatchers.IO)
    .onEach {dto->
        require(dto.id!=null)
        val id = dto.id
        val link = dto.toLink()
        val magnet = isPrefix(link) ?: return@onEach
        transaction {
            PrefixSerial.insert {
                it[magnet_id] = id
                it[prefix_id] = prefixes.toList().find { it.second == magnet.prefix }?.first!!
            }
        }
    }
fun Flow<DTO>.tagOfSerial() = flowOn(Dispatchers.IO)
    .onEach {dto->
        require(dto.id!=null)
        val id = dto.id
        val link = dto.toLink()
        val magnet = isSerial(link) ?: return@onEach
        transaction {
            Serial.insert {
                it[magnet_id] = id
                it[info] = magnet.serialInfo
            }
        }
    }
fun Flow<DTO>.tags() =
    tagOfIgnore()
        .tagOfPlaza()
        .tagOfMovie()
        .tagOfPrefix()
        .tagOfSerial()

fun Flow<Magnet>.saveToDB() = flowOn(Dispatchers.IO)
    .onEach {magnet ->
        transaction {
            println(magnet)

            val r = Magnets.insert {
                it[hash] = magnet.hash
                it[dn] = when (magnet) {
                    is Magnet.NoName -> ""
                    is Magnet.NameInUrl -> magnet.downloadName
                }
            }
            val id = r[Magnets.id]
            val hash = r[Magnets.hash]
            val dn = r[Magnets.dn]
            if (magnet is Magnet.NoName)
                return@transaction
            require(magnet is Magnet.NameInUrl)
            require(magnet.downloadName == dn) {
                "$dn != ${magnet.downloadName}"
            }
            require(magnet.hash == hash)
            val link = magnet.toLink()
            with(Magnet.NameInUrl) {
                isIgnore(link)?.let {magnet->
                    Ignores.insert {
                        it[magnet_id] = id
                    }
                }
                isPlaza(link)?.let {magnet->
                    TODO()
                }
                isMovie(link)?.let {magnet->
                    MovieYear.insert {
                        it[magnet_id] = id
                        it[year] = magnet.year.toInt()
                        it[name] = magnet.name
                    }
                    isSomeMovie(link)?.let {magnet->
                        MovieType.insert {
                            it[magnet_id] = id
                            it[codec] = magnet.codec
                            it[quality] = magnet.resolution
                        }
                    }
                }?: isSomeMovie(link)?.let {magnet->
                    MovieType.insert {
                        it[magnet_id] = id
                        it[codec] = magnet.codec
                        it[quality] = magnet.resolution
                    }
                }
                isSerial(link)?.let {magnet->
                    Serial.insert {
                        it[magnet_id] = id
                        it[info] = magnet.serialInfo
                    }
                }
                isPrefix(link)?.let {magnet->
                    PrefixSerial.insert {
                        it[magnet_id] = id
                        it[prefix_id] = prefixes.toList().find { it.second == magnet.prefix }?.first!!
                    }
                }
            }
        }
    }
val prefixes by lazy {
    transaction {
        Prefixes.selectAll().associate {
            it[Prefixes.id].value to it[Prefixes.prefix]
        }
    }
}
