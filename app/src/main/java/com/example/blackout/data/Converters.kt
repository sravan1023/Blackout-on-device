package com.example.blackout.data

import androidx.room.TypeConverter
import com.example.blackout.data.entities.Category
import com.example.blackout.data.entities.FileType

class Converters {
    @TypeConverter fun fileTypeToString(v: FileType): String = v.name
    @TypeConverter fun stringToFileType(v: String): FileType = FileType.valueOf(v)
    @TypeConverter fun categoryToString(v: Category): String = v.name
    @TypeConverter fun stringToCategory(v: String): Category = Category.valueOf(v)
}
