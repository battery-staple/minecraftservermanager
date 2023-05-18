package com.rohengiralt.minecraftservermanager.domain.model

import kotlinx.coroutines.flow.Flow

class MinecraftServerData(val data: DataType) {
    sealed class DataType {
        abstract val name: String
        class Folder(override val name: String, val contents: Flow<File>) : DataType()
        class File(override val name: String, val contents: Flow<ByteArray>) : DataType()
    }
}
