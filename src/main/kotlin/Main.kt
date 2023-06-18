import kotlinx.coroutines.FlowPreview
import java.io.File

@OptIn(FlowPreview::class)
suspend fun main() {
    val getFile = {it:String -> File("../bts/rarbg-main/${it}.txt") }
    getFile("moviesrarbg").toMagnetsDB("dist/mas.hash.id")
    getFile("showsrarbg").toMagnetsDB("dist/mas.hash.id")
}
