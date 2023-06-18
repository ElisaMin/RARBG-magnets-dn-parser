import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table


object Magnets: Table("magnets") {
    val hash = varchar("hash",40)
    val dn = text("dn")
    override val primaryKey: PrimaryKey = PrimaryKey(hash)
}
inline val Magnets.id get() = hash
//object Magnets: IntIdTable("magnets") {
//    val hash = varchar("hash",40)
//    val dn = text("dn")
//}
abstract class RefMagnetTable(name:String): IntIdTable(name) {
    val magnet_id = reference(
        "magnet_id",Magnets.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
}
object Ignores:RefMagnetTable("ignores")
object MovieYear:RefMagnetTable("movie_year") {
    val year = integer("year")
    val name = text("name")
}
object MovieType:RefMagnetTable("movie_type") {
    val codec = text("codec")
    val quality = text("quality")
}
object Serial:RefMagnetTable("serial") {
    val info = text("info")
}
object Plaza:RefMagnetTable("plaza") {
    val name = text("name")

}
object PlazaUpdate:RefMagnetTable("plaza_updates") {
    val update = text("update")
    val plaza_id = reference(
        "plaza_id",Plaza.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
}
object Prefixes: IntIdTable("prefixes") {
    val prefix = text("prefix")
        .uniqueIndex()
}
object PrefixSerial:RefMagnetTable("prefix_serial") {
    val prefix_id = reference(
        "prefix_id",Prefixes.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
}
object Names:IntIdTable("names") {
    val name = text("name")
        .uniqueIndex()
}
object MagnetNames:IntIdTable("magnet_names") {
    val magnet_id = reference(
        "magnet_id",Magnets.hash,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
    val name_id = reference(
        "name_id",Names.id,
        onDelete = ReferenceOption.CASCADE,
        onUpdate = ReferenceOption.CASCADE,
    )
}