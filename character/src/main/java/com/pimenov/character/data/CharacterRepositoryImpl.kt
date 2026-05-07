package com.pimenov.character.data

import com.pimenov.character.api.CharacterRepository
import com.pimenov.character.api.CharacterSheet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CharacterRepositoryImpl(private val dao: CharacterDao) : CharacterRepository {
    override suspend fun save(sheet: CharacterSheet): Long =
        dao.upsert(CharacterEntity.fromDomain(sheet))

    override suspend fun byId(id: Long): CharacterSheet? = dao.byId(id)?.toDomain()

    override fun observeLatest(): Flow<CharacterSheet?> =
        dao.observeLatest().map { it?.toDomain() }

    override suspend fun clear() = dao.clear()
}
