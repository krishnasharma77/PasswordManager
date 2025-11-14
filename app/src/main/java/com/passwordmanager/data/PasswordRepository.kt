package com.passwordmanager.data

import com.passwordmanager.security.EncryptionHelper
import kotlinx.coroutines.flow.Flow

class PasswordRepository(
    private val dao: PasswordDao,
    private val encryption: EncryptionHelper
) {
    fun getAll(): Flow<List<PasswordEntity>> = dao.getAll()

    suspend fun addPassword(title: String, username: String, rawPassword: String) {
        val encrypted = encryption.encrypt(rawPassword)
        dao.insert(
            PasswordEntity(
                title = title,
                username = username,
                encryptedPassword = encrypted
            )
        )
    }

    suspend fun updatePassword(entity: PasswordEntity, newPassword: String) {
        val encrypted = encryption.encrypt(newPassword)
        dao.update(entity.copy(encryptedPassword = encrypted))
    }

    suspend fun deletePassword(entity: PasswordEntity) = dao.delete(entity)

    fun decryptPassword(entity: PasswordEntity): String {
        return encryption.decrypt(entity.encryptedPassword)
    }
}