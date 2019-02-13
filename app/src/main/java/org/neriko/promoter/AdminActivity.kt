package org.neriko.promoter

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_admin.*
import org.json.JSONArray
import java.util.*

class AdminActivity : AppCompatActivity() {

    val mAuth = FirebaseAuth.getInstance()
    val mData = FirebaseFirestore.getInstance()

    val winners = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        //Setting up primary winner image
        mData.collection("winners").document("main_winner")
                .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException != null) {
                        Log.w("Promoter", firebaseFirestoreException)
                        return@addSnapshotListener
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val uid = documentSnapshot.getString("uid")!!
                        mData.collection("users").document(uid).get()
                                .addOnSuccessListener {
                                    val shortcode = it.getString("picture_shortcode")!!
                                    val name = it.getString("profile_name")!!
                                    val link = if (shortcode.contains("http")) shortcode
                                    else "https://instagram.com/p/$shortcode/media/?size=l"
                                    Picasso.get().load(link).into(image_main)
                                    image_main.setOnClickListener {
                                        if (mAuth.currentUser != null) {
                                            val map = HashMap<String, Any>()
                                            map["value"] = 1
                                            mData.collection("participators").document(mAuth.uid!!).set(map)
                                        }
                                        val uri = Uri.parse("http://instagram.com/_u/$name")
                                        val likeIng = Intent(Intent.ACTION_VIEW, uri)
                                        likeIng.setPackage("com.instagram.android")
                                        try {
                                            startActivity(likeIng)
                                        } catch (e: ActivityNotFoundException) {
                                            startActivity(Intent(Intent.ACTION_VIEW,
                                                    Uri.parse("http://instagram.com/$name")))
                                        }
                                    }
                                }
                    }
                }

        //Setting up secondary winners recyclerView
        val users = ArrayList<User>()
        val recyclerAdapter = CustomAdapter(users)
        val recyclerView = findViewById<RecyclerView>(R.id.images_container).apply {
            isNestedScrollingEnabled = false
            layoutManager = GridLayoutManager(this@AdminActivity, 3)
            adapter = recyclerAdapter
        }
        mData.collection("winners").document("winners_array")
                .addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException != null) {
                        Log.w("Promoter", firebaseFirestoreException)
                        return@addSnapshotListener
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val jsonArray = JSONArray(documentSnapshot.get("array").toString())
                        for (i in 0 until jsonArray.length()) {
                            mData.collection("users").document(jsonArray.getString(i)).get() //Download winner's user data
                                    .addOnSuccessListener {
                                        if (users.size >= 9) {
                                            users[i] = it.toObject(User::class.java)!!
                                            recyclerAdapter.notifyItemChanged(i)
                                        } else {
                                            users.add(it.toObject(User::class.java)!!)
                                            recyclerAdapter.notifyItemInserted(users.size-1)
                                        }
                                    }
                        }
                    }
                }

        generate_winners.setOnClickListener { view ->
            mData.collection("participators").get().addOnSuccessListener {
                val documents = it.documents
                val participators = ArrayList<String>(documents.size * 3)
                for (document in documents) {
                    for (i in 0 until document.getLong("value")!!) {
                        participators.add(document.id)
                    }
                }

                winners.clear()

                var luckyID = participators.random()!!
                winners.add(luckyID)

                val list = ArrayList<String>()
                for (i in 0..8) {
                    luckyID = participators.random()!!
                    winners.add(luckyID)
                }

                Toast.makeText(this, "Generated winners check logcat to review!", Toast.LENGTH_LONG).show()
                for (i in 0 until winners.size) {
                    System.out.println(winners[i])
                }
            }
        }

        apply_winners.setOnClickListener {
            val winner = HashMap<String, Any>()
            winner["uid"] = winners[0]
            mData.collection("winners").document("main_winner").set(winner)

            val secondary_winners = HashMap<String, Any>()
            val list = ArrayList<String>()
            for (i in 1..9) {
                list.add(winners[i])
            }
            secondary_winners["array"] = list
            mData.collection("winners").document("winners_array").set(secondary_winners)
            Toast.makeText(this, "Applied winners!", Toast.LENGTH_LONG).show()
        }

        clear_participators.setOnClickListener {
            mData.collection("participators").get().addOnSuccessListener {
                for (document in it.documents) {
                    mData.collection("participators").document(document.id).delete()
                }
                Toast.makeText(this, "Deleted participators!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun <E> List<E>.random(): E? = if (size > 0) get(Random().nextInt(size)) else null

    private inner class CustomAdapter(val users: ArrayList<User>): RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomAdapter.ViewHolder {
            val itemLayoutView = LayoutInflater.from(parent.context).inflate(R.layout.recycler_item, null)
            return ViewHolder(itemLayoutView)
        }

        override fun getItemCount() = users.size

        override fun onBindViewHolder(holder: CustomAdapter.ViewHolder, position: Int) {
            val link = if (users[position].picture_shortcode.contains("http")) users[position].picture_shortcode
            else "https://instagram.com/p/" + users[position].picture_shortcode + "/media/?size=m"
            Picasso.get().load(link).into(holder.imageView)
        }

        private inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
            val imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        }
    }
}
