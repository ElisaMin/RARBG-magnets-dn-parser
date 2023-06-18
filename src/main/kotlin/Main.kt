import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.sql.DriverManager

fun main(args: Array<String>) {

    val db = Database.connect(getNewConnection = {
        DriverManager.getConnection("jdbc:sqlite:dist/database")
    })
    transaction(db) {
        addLogger(Slf4jSqlDebugLogger)
        SchemaUtils.create(Magnets, Ignores, MovieYear, MovieType, Serial, Plaza, PlazaUpdate, Prefixes, PrefixSerial)
        val prefixes = Prefixes.selectAll()
            .map { it[Prefixes.prefix] }
            .toSet()

        Magnet.NameInUrl.Prefix.prefixs.filter { !prefixes.contains(it) }.forEach {str->
            Prefixes.insert {
                it[prefix] = str
            }
        }
    }
    println("started")
    val file = File("../bts/rarbg-main/7z/everything/everything.txt").reader()

    file.useLines {
        runBlocking {
            println("flow on")
            withContext(Dispatchers.IO) {
                it.asFlow()
                    .mapNotInDB()
                    .saveHashAndDnToDB()
                    .onEach {
                        println(it.toLink())
                    }.tags()
                    .collect {

                        Unit
                    }
            }
        }

    }
}