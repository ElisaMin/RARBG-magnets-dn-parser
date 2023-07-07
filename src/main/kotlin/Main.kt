import java.io.File

fun getFile(s:String) = File("../bts/rarbg-main/${s}.txt")
fun toMagnetsDB(dist:String,vararg files: File) =
    files.map { it.bufferedReader().lineSequence() }
        .asSequence()
        .flatten()
        .toMagnetsDB(
            db(
                "dist/$dist".also { require(!File("$it.db").exists()) }
            )
        )

fun main() {
    toMagnetsDB("rarbg.infos.name.single",
        getFile("moviesrarbg"),
        getFile("showsrarbg")
    )
}
