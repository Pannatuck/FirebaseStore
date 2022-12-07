package com.pan.firebasestore

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.pan.firebasestore.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val personCollectionRef = Firebase.firestore.collection("persons")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUpload.setOnClickListener {
            val person = getOldPerson()
            savePerson(person)
        }

//        subscribeToRealtimeUpdates()

        binding.btnRetrieve.setOnClickListener{
            retrievePerson()
        }

        // бере дані про існуючу персону з полів о доданні нової і оновлює на дані,
        // вказані в полях для оновлення
        binding.btnUpdate.setOnClickListener{
            val oldPerson = getOldPerson()
            val newPersonMap = getNewPersonMap()
            updatePerson(oldPerson, newPersonMap)
        }

        binding.btnDelete.setOnClickListener {
            val person = getOldPerson()
            deletePerson(person)
        }

        binding.btnBatched.setOnClickListener{
            changeName("3teLLsOu7J7r0Q92hSWI", "Garry", "all cool. chilling")
        }

        binding.btnTransaction.setOnClickListener {
            birthday("3teLLsOu7J7r0Q92hSWI")
        }
    }

    private fun getOldPerson(): Person {
        val nickname = binding.etNickname.text.toString()
        val status = binding.etStatus.text.toString()
        val age = binding.etAge.text.toString().toInt()
        return Person(nickname, status, age)
    }

    private fun getNewPersonMap(): Map<String, Any>{
        val nickname = binding.etUpdateNickname.text.toString()
        val status = binding.etUpdateStatus.text.toString()
        // треба конвертувати в Int, пізніше. Якщо в поле нічого не буде вписано і буде спроба
        // конвертувати в Int, програма крашнеться
        val age = binding.etUpdateAge.text.toString()
        val map = mutableMapOf<String, Any>()
        if (nickname.isNotEmpty()){
            map["nickname"] = nickname
        }
        if(status.isNotEmpty())
            map["status"] = status
        if(age.isNotEmpty())
            map["age"] = age.toInt()
        return map
    }

    private fun deletePerson(person: Person) =
        CoroutineScope(Dispatchers.IO).launch {
            val personQuery = personCollectionRef
                .whereEqualTo("nickname", person.nickname)
                .whereEqualTo("status", person.status)
                .whereEqualTo("age", person.age)
                .get()
                .await()
            if (personQuery.documents.isNotEmpty()){
                for(document in personQuery){
                    try {
                        // видалення всього документа про персону
                        personCollectionRef.document(document.id).delete().await()
                        // видалення полів, вказаних всередині
                        /*personCollectionRef.document(document.id).update(mapOf(
                            "firstName" to FieldValue.delete()
                        ))*/
                        withContext(Dispatchers.Main){
                            Toast.makeText(this@MainActivity, "Deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception){
                        withContext(Dispatchers.Main){
                            Toast.makeText(this@MainActivity, e.message,
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else{
                withContext(Dispatchers.Main){
                    Toast.makeText(this@MainActivity, "No person matched the query",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

    // передаємо існуючу персону (oldPerson) для створення Query, щоб вказати БД яку саме персону
    // треба оновити і передаємо нову персону (newPersonMap, яку отриаємо при виклику з
    // методу getNewPersonMap(), для вказання, які саме поля у персони треба оновити)
    private fun updatePerson(person: Person, newPersonMap: Map<String, Any>) =
        CoroutineScope(Dispatchers.IO).launch {
            val personQuery = personCollectionRef
                .whereEqualTo("nickname", person.nickname)
                .whereEqualTo("status", person.status)
                .whereEqualTo("age", person.age)
                .get()
                .await()
            if (personQuery.documents.isNotEmpty()){
                for(document in personQuery){
                    try {
                        // потрібно вказувати options, інакше, якщо є пусті поля, він замінить на них
                        personCollectionRef.document(document.id).set(
                            newPersonMap,
                            SetOptions.merge()
                        ).await()
                        withContext(Dispatchers.Main){
                            Toast.makeText(this@MainActivity, "Updated successfully", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception){
                        withContext(Dispatchers.Main){
                            Toast.makeText(this@MainActivity, e.message,
                                Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else{
                withContext(Dispatchers.Main){
                    Toast.makeText(this@MainActivity, "No person matched the query",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }

    // транзакції корисні, для обробки випадків, коли декілька користувачів працюють з одним й тим
    // самим полем. Вони будуть відстежувати внесені зміни і працювати з тими значеннями, які користувач
    // бачив на момент початку роботи з ними (якщо інший користувач вже їх змінив)
    // В цьому випадку буде збільшено вік користувача і на момент початку транзакції два користувача
    // будуть мати вік "35" (наприклад), і коли кожен збільшить на один, то не буде помилки або 37
    // в результаті. Вік збільшиться на 1 і буде дорівнювати 36
    private fun birthday(personId: String) = CoroutineScope(Dispatchers.IO).launch {
        try {
            Firebase.firestore.runTransaction { transaction ->
                val personRef = personCollectionRef.document(personId)
                val person = transaction.get(personRef)
                val newAge = person["age"] as Long + 1
                transaction.update(personRef, "age", newAge)
                null // нулл вказує, що транзакція завершилась
            }.await()
        } catch (e: Exception){
            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
        }
    }


    // Batch Writes
    // Використовується для внесення одразу декількох змін, кожна з яких обов'язково має виконатись
    // для того щоб, хоч одна з змін вступила в силу
    private fun changeName(
        personId: String,
        newNickname: String,
        newStatus: String
    ) = CoroutineScope(Dispatchers.IO).launch {
        try{
            Firebase.firestore.runBatch { batch ->
                val personRef = personCollectionRef.document(personId)
                batch.update(personRef, "nickname", newNickname)
                batch.update(personRef, "status", newStatus)
            }.await()
        }catch (e: Exception){
            withContext(Dispatchers.Main){
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

//    /* це listener самого Firebase, який реалізує автоматичну перевірку того, чи оновились дані у БД
//    * Якщо якісь зміни виникли, addSnapshotListener автоматом виконає необхідні дії (перевірка,
//    * чи кинула БД помилку і value для вказання, що саме треба зробити, коли дані змінились.
//    * В цьому випадку, робить такуж саму зміну і в UI користувача.)*/
//    private fun subscribeToRealtimeUpdates(){
//        personCollectionRef.addSnapshotListener { value, error ->
//            error?.let{
//                Toast.makeText(this, it.message, Toast.LENGTH_SHORT).show()
//                return@addSnapshotListener
//            }
//            value?.let{
//                val sb = StringBuilder()
//                for(document in it){
//                    val person = document.toObject(Person::class.java)
//                    sb.append("$person\n")
//                }
//                binding.tvPersons.text = sb.toString()
//            }
//        }
//    }

    private fun retrievePerson() = CoroutineScope(Dispatchers.IO).launch {
        val fromAge = binding.etAgeStart.text.toString().toInt()
        val toAge = binding.etAgeEnd.text.toString().toInt()
        try {
            // querySnapshot, це щось начебто знимку усіх документів, які зберігаються в Firestore
            // будь-яку функція типу Task, можливо викликати через await()
            val querySnapshot = personCollectionRef
                // Firebase має багато подібних функцій для роботи з Query (наче SQL, але через функції)
                // Такі саме запроси можна робити і в realtime updates, за які в цьому проекті відповідає
                // функція subscribeToRealtimeUpdates (перед addSnapshotListener, вставити потрібні Query)
                .whereGreaterThan("age", fromAge)
                .whereLessThan("age", toAge)
                .orderBy("age")
                .get()
                .await()

            val sb = StringBuilder()
            // виокремлюємо із списку документів окремий документ (в цьому випадку це окрема Person)
            for(document in querySnapshot.documents){
                // виокремленна Person буде типу DocumentSnapshot. Методом Firebase toObject,
                // конвертуємо в потрібний нам тип (в дата клас Person)
                val person = document.toObject(Person::class.java)
                sb.append("$person\n")
            }
            // для відображення UI, потрбіно використовувати main тред
            withContext(Dispatchers.Main){
                binding.tvPersons.text = sb.toString()
            }
        }catch (e: Exception){
            withContext(Dispatchers.Main){
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun savePerson(person: Person) = CoroutineScope(Dispatchers.IO).launch {
        try {
            // метод для додання нових полів у Firestore
            personCollectionRef.add(person).await()
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Successfully saved data.", Toast.LENGTH_LONG).show()
            }
        } catch(e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}