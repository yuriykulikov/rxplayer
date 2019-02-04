package de.eso.rxplayer.vertx

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

fun Moshi.serializer(): (Any) -> String {
    return { obj: Any ->
        when (obj) {
            is List<*> -> {
                val element: Any = obj[0]!!
                adapter<List<*>>(Types.newParameterizedType(List::class.java, element.javaClass)).toJson(obj)

            }
            else -> {
                adapter(obj.javaClass).toJson(obj)
            }
        }
    }
}

fun Moshi.deserializer(): (Class<out Any>, String) -> Any {
    return { cls, str -> adapter(cls).fromJson(str)!! }
}

