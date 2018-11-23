package de.eso.rxplayer

import com.squareup.moshi.*
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.util.LinkedHashMap

class JSONUtils {
    fun readTrackFile(): List<Track> {
        val maptype = Types.newParameterizedType(Map::class.java, String::class.java, Track::class.java)
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).add(TrackMapJsonAdapter()).build()

        val jsonString = File("C:\\repos\\rxplayer\\app\\src\\main\\media\\tracks.json").inputStream().readBytes().toString(Charsets.UTF_8)
        val jsonAdapter: JsonAdapter<Map<String, Track>> = moshi.adapter(maptype)
        val trackList = jsonAdapter.fromJson(jsonString)!!.values.toList()
        return trackList
    }

    fun readArtistFile(): List<Artist> {
        val maptype = Types.newParameterizedType(Map::class.java, String::class.java, Artist::class.java)
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).add(ArtistMapJsonAdapter()).build()

        val jsonString = File("C:\\repos\\rxplayer\\app\\src\\main\\media\\artists.json").inputStream().readBytes().toString(Charsets.UTF_8)
        val jsonAdapter: JsonAdapter<Map<String, Artist>> = moshi.adapter(maptype)
        val artistList = jsonAdapter.fromJson(jsonString)!!.values.toList()
        return artistList
    }

    fun readAlbumFile(): List<Album> {
        val maptype = Types.newParameterizedType(Map::class.java, String::class.java, Album::class.java)
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).add(AlbumMapJsonAdapter()).build()

        val jsonString = File("C:\\repos\\rxplayer\\app\\src\\main\\media\\albums.json").inputStream().readBytes().toString(Charsets.UTF_8)
        val jsonAdapter: JsonAdapter<Map<String, Album>> = moshi.adapter(maptype)
        val albumList = jsonAdapter.fromJson(jsonString)!!.values.toList()
        return albumList
    }
}

class TrackMapJsonAdapter {
    @FromJson
    fun mapFromJson(jsonMap: Map<String, Track>): Map<String, Track> {
        val result = LinkedHashMap<String, Track>()
        for ((key, value) in jsonMap) {
            result[key] = value
        }
        return result
    }

    @ToJson
    fun mapToJson(modelMap: Map<String, Track>): Map<String, Track> {
        val result = LinkedHashMap<String, Track>()
        for ((key, value) in modelMap) {
            result[key] = value
        }
        return result
    }
}

class ArtistMapJsonAdapter {
    @FromJson
    fun mapFromJson(jsonMap: Map<String, Artist>): Map<String, Artist> {
        val result = LinkedHashMap<String, Artist>()
        for ((key, value) in jsonMap) {
            result[key] = value
        }
        return result
    }

    @ToJson
    fun mapToJson(modelMap: Map<String, Artist>): Map<String, Artist> {
        val result = LinkedHashMap<String, Artist>()
        for ((key, value) in modelMap) {
            result[key] = value
        }
        return result
    }
}

class AlbumMapJsonAdapter {
    @FromJson
    fun mapFromJson(jsonMap: Map<String, Album>): Map<String, Album> {
        val result = LinkedHashMap<String, Album>()
        for ((key, value) in jsonMap) {
            result[key] = value
        }
        return result
    }

    @ToJson
    fun mapToJson(modelMap: Map<String, Album>): Map<String, Album> {
        val result = LinkedHashMap<String, Album>()
        for ((key, value) in modelMap) {
            result[key] = value
        }
        return result
    }
}