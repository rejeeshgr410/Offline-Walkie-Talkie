package com.example.offlinewalkietalkie.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "talkloop_contacts")

class TrustedContactsRepository(private val context: Context) {

    companion object {
        private val CONTACTS_KEY = stringPreferencesKey("trusted_contacts_json")
    }

    val contacts: Flow<List<TrustedContact>> = context.dataStore.data.map { prefs ->
        val raw = prefs[CONTACTS_KEY] ?: "[]"
        try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<TrustedContact>()
            for (i in 0 until jsonArray.length()) {
                list.add(TrustedContact.fromJson(jsonArray.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveContact(contact: TrustedContact) {
        context.dataStore.edit { prefs ->
            val raw = prefs[CONTACTS_KEY] ?: "[]"
            val currentList = try {
                val jsonArray = JSONArray(raw)
                val list = mutableListOf<TrustedContact>()
                for (i in 0 until jsonArray.length()) {
                    list.add(TrustedContact.fromJson(jsonArray.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }

            val existingIndex = currentList.indexOfFirst { it.contactId == contact.contactId || it.publicKeyHex == contact.publicKeyHex }
            if (existingIndex >= 0) {
                currentList[existingIndex] = contact
            } else {
                currentList.add(contact)
            }

            val newArray = JSONArray()
            currentList.forEach { newArray.put(it.toJson()) }
            prefs[CONTACTS_KEY] = newArray.toString()
        }
    }

    suspend fun verifyContact(contactId: String) {
        context.dataStore.edit { prefs ->
            val raw = prefs[CONTACTS_KEY] ?: "[]"
            val currentList = try {
                val jsonArray = JSONArray(raw)
                val list = mutableListOf<TrustedContact>()
                for (i in 0 until jsonArray.length()) {
                    list.add(TrustedContact.fromJson(jsonArray.getJSONObject(i)))
                }
                list
            } catch (e: Exception) {
                mutableListOf()
            }

            val index = currentList.indexOfFirst { it.contactId == contactId }
            if (index >= 0) {
                currentList[index] = currentList[index].copy(isVerified = true)
                val newArray = JSONArray()
                currentList.forEach { newArray.put(it.toJson()) }
                prefs[CONTACTS_KEY] = newArray.toString()
            }
        }
    }

    suspend fun isContactTrusted(publicKeyHex: String): Boolean {
        val list = contacts.first()
        return list.any { it.publicKeyHex == publicKeyHex && it.isVerified }
    }
}
